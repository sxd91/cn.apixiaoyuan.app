// pk_auto_stroke.js —— 「PK 自动提交画笔」（内置版）
//
// 移植来源：cn.nizou.sxd `hook/SimianV2PkAutomation.kt` 的 `submitStrokeOnce`
// 内联脚本（2026-09 真机验证），**按内置架构重写**：
//
//   | | cn.nizou.sxd（LSPosed） | 本项目（内置客户端） |
//   |---|---|---|
//   | 注入方式 | hook 宿主 WebView.loadUrl | 自己的 WebView 直接 evaluateJavascript |
//   | 触发时机 | hook 到题目页后由 native 排程 | onPageFinished + 自身轮询等画板就绪 |
//   | 笔迹点 | 硬编码 11 个 PointF | 同款点集（下方 POINTS） |
//   | 提交次数 | 由 prefs 配置 | 由 window.__pk_stroke_count 传入 |
//
// ## 为什么必须走画板实例
//
// PK 页是 Vue 3 + Pinia 的 H5。答题画板不是 DOM 元素，而是
// `useRecognizeBoard` composable 内部的局部 `ref`，**不在 Pinia store 里**
// （真机 probe 已证实：`globalProperties` 无 `$pinia`、`System.entries()` 为空）。
// 唯一能拿到活体画板的路径是**遍历 Vue 组件树的 `setupState`**，找带
// `pad` + `recognizeConfig` 的那个组件 —— 参考项目的 `scanInstance` 就是这么做的，
// 这里逐条保留其四级兜底顺序（app-el → app-descendant → document-scan → window）。
//
// ## 提交流程（三步，缺一不可）
//
//   1. `pad.fromData(groups, { clear: true })` —— 把点集正规化成贝塞尔段**并真正
//      画到 canvas**。只写 `_data` 不画，后续 `endStroke` 监听器读 `toData()`
//      会拿到空内容，识别端收不到笔迹。
//   2. 回退路径：没有 `fromData` 的旧画板实现，直接写 `_data` 并同步 `_isEmpty`、
//      调 `_fromData` 重绘。
//   3. `pad.dispatchEvent(new CustomEvent('endStroke', {detail:{synthetic:true}}))`
//      —— 触发画板自己的提交逻辑（识别 + 上报都在这个监听器里）。
//
// ## 与参考项目的差异（如实记录）
//
//  - **去掉了 `System.import` / `SystemJS registry` 兜底**：那条路要遍历
//    `System.entries()` 动态 import 整个 bundle，在本项目（每次进页面都新注入）
//    下开销大且未验证；四级 Vue 树兜底已覆盖真机场景，先用保守版本。
//  - **去掉了 native 侧的 `Handler.postDelayed` 排程**：本脚本用自身的
//    `setInterval` 轮询等画板就绪，就绪后按 `window.__pk_stroke_interval`
//    的间隔连提 N 次。JS 侧闭环，native 只负责注入一次。
//
// 注入参数（由 PkJsInjector 在注入前写入 window）：
//   window.__pk_stroke_count     提交次数（默认 1）
//   window.__pk_stroke_interval  两次提交间隔毫秒（默认 1200）
(function () {
    'use strict';

    if (window.__pk_auto_stroke_installed) return 'already-installed';
    window.__pk_auto_stroke_installed = true;

    var POINTS = [
        { x: 146.8571, y: 498.5714 }, { x: 146.8571, y: 516.2858 },
        { x: 146.8571, y: 544.4261 }, { x: 148, y: 561.7143 },
        { x: 148, y: 584 }, { x: 148, y: 610.8572 },
        { x: 148, y: 627.7143 }, { x: 149.7143, y: 652.2858 },
        { x: 151.4286, y: 668 }, { x: 153.1429, y: 675.7143 },
        { x: 156.8571, y: 684.5715 }
    ];

    var total = (typeof window.__pk_stroke_count === 'number' && window.__pk_stroke_count > 0)
        ? Math.floor(window.__pk_stroke_count) : 1;
    var interval = (typeof window.__pk_stroke_interval === 'number' && window.__pk_stroke_interval >= 0)
        ? window.__pk_stroke_interval : 1200;
    // 等画板就绪的最长轮询时间：题目页渲染 + Vue 挂载在真机上约 1~3s。
    var READY_TIMEOUT_MS = 20000;
    var READY_POLL_MS = 250;

    var status = window.__pk_stroke_status = {
        status: 'waiting-pad', submitted: 0, total: total, errors: []
    };

    function dbg(m) {
        try { console.log('[pk_auto_stroke] ' + m); } catch (e) { }
    }

    function unref(t) {
        return (t && typeof t === 'object' && 'value' in t) ? t.value : t;
    }

    // 画板判据：既能 dispatchEvent（EventTarget）又有 toData（真实画板 API）。
    function isPad(p) {
        return !!p && typeof p.dispatchEvent === 'function' && typeof p.toData === 'function';
    }

    // recognizeConfig 判据：带 keypointId 才算「活的题目配置」，避免命中上一题的残留。
    function isLive(cfg) {
        return !!cfg && !!cfg.keypointId;
    }

    var hit = null;

    function subTreeChildren(vnode) {
        var kids = [];
        var walk = function (v) {
            if (!v) return;
            if (v.component) kids.push(v.component);
            if (Array.isArray(v.children)) v.children.forEach(walk);
            if (v.suspense && v.suspense.activeBranch) walk(v.suspense.activeBranch);
        };
        walk(vnode);
        return kids;
    }

    function scanInstance(inst, depth) {
        if (!inst || hit || depth > 60) return;
        var st = inst.setupState || {};
        var padObj = unref(st.pad);
        var cfgObj = unref(st.recognizeConfig);
        if (isPad(padObj) && isLive(cfgObj)) {
            hit = { source: 'vue-setup', pad: padObj, config: cfgObj };
            return;
        }
        var ctx = inst.ctx || {};
        var ctxPad = unref(ctx.pad);
        if (isPad(ctxPad) && isLive(unref(ctx.recognizeConfig))) {
            hit = { source: 'vue-ctx', pad: ctxPad, config: unref(ctx.recognizeConfig) };
            return;
        }
        var kids = subTreeChildren(inst.subTree);
        for (var i = 0; i < kids.length; i++) {
            scanInstance(kids[i], depth + 1);
            if (hit) return;
        }
    }

    // 在任意 document 根（含 iframe contentDocument、ShadowRoot）上找 __vue_app__。
    function findVueAppIn(rootDoc) {
        if (!rootDoc || hit) return null;
        var appEl = rootDoc.getElementById ? rootDoc.getElementById('app') : null;
        if (appEl && appEl.__vue_app__) return { app: appEl.__vue_app__, via: 'app-el' };
        if (appEl) {
            var stack = [appEl], n = 0;
            while (stack.length && n < 400) {
                var cur = stack.pop(); n++;
                if (cur.__vue_app__) return { app: cur.__vue_app__, via: 'app-descendant' };
                var kids = cur.children;
                if (kids) for (var i = 0; i < kids.length; i++) stack.push(kids[i]);
            }
        }
        var all = rootDoc.querySelectorAll ? rootDoc.querySelectorAll('*') : [];
        var cap = Math.min(all.length, 4000);
        for (var j = 0; j < cap; j++) {
            var el = all[j];
            if (el.__vue_app__) return { app: el.__vue_app__, via: 'document-scan' };
            if (el.shadowRoot && el.shadowRoot.__vue_app__) {
                return { app: el.shadowRoot.__vue_app__, via: 'shadow-root' };
            }
        }
        if (rootDoc.defaultView && rootDoc.defaultView.__vue_app__) {
            return { app: rootDoc.defaultView.__vue_app__, via: 'window' };
        }
        var w = rootDoc.defaultView || {};
        if (w.Vue && w.Vue.__app__) return { app: w.Vue.__app__, via: 'vue-global' };
        return null;
    }

    function findVueApp() {
        var found = findVueAppIn(document);
        if (found) return found;
        var iframes = document.querySelectorAll('iframe');
        for (var i = 0; i < iframes.length && i < 20; i++) {
            var doc = null;
            try { doc = iframes[i].contentDocument; } catch (e) { continue; }
            if (!doc) continue;
            found = findVueAppIn(doc);
            if (found) { found.via = 'iframe:' + i + ':' + found.via; return found; }
        }
        return null;
    }

    function collect() {
        hit = null;
        var found = findVueApp();
        status.vueAppVia = found ? found.via : 'none';
        if (!found) return null;
        var app = found.app;
        var root = app._instance;
        if (!root && app._container) {
            root = app._container._vnode && app._container._vnode.component;
        }
        scanInstance(root, 0);
        if (hit) { status.source = hit.source; return hit; }
        // provides 兜底（Pinia store 通过 provide 注入的场景）
        var provides = (app._context && app._context.provides) || {};
        var keys = Object.keys(provides);
        for (var i = 0; i < keys.length; i++) {
            var v = null;
            try { v = provides[keys[i]]; } catch (e) { continue; }
            var pad = unref(v && v.pad);
            var cfg = unref(v && v.recognizeConfig);
            if (isPad(pad) && isLive(cfg)) {
                status.source = 'vue-provides';
                return { source: 'vue-provides', pad: pad, config: cfg };
            }
        }
        return null;
    }

    function groupsFromPoints() {
        return [{
            points: POINTS,
            penColor: '#000',
            minWidth: 3,
            maxWidth: 3,
            dotSize: 0,
            velocityFilterWeight: 0.7,
            compositeOperation: 'source-over'
        }];
    }

    function submitOnce(found) {
        var pad = found.pad;
        status.keypointId = found.config.keypointId;
        var injected = false;
        // 路径一：画板自身的注入 API（会真正画到 canvas）。
        try {
            if (typeof pad.fromData === 'function') {
                pad.fromData(groupsFromPoints(), { clear: true });
                injected = true;
                status.injectMode = 'fromData';
            }
        } catch (e) {
            status.errors.push('fromData: ' + (e && e.message ? e.message : e));
        }
        // 路径二：旧实现兜底 —— 写 _data + 重绘。
        if (!injected) {
            try {
                pad._data = groupsFromPoints();
                if ('_isEmpty' in pad) pad._isEmpty = false;
                if (typeof pad._fromData === 'function') {
                    pad._fromData(pad._data, pad._drawCurve.bind(pad), pad._drawDot.bind(pad));
                }
                if ('_isEmpty' in pad) pad._isEmpty = false;
                status.injectMode = 'raw-data';
            } catch (e2) {
                status.errors.push('raw-data: ' + (e2 && e2.message ? e2.message : e2));
                return false;
            }
        }
        // 路径三：触发画板自己的提交逻辑。
        try {
            pad.dispatchEvent(new CustomEvent('endStroke', { detail: { synthetic: true } }));
            status.submitted++;
            status.status = 'submitted';
            return true;
        } catch (e3) {
            status.errors.push('endStroke: ' + (e3 && e3.message ? e3.message : e3));
            status.status = 'failed';
            return false;
        }
    }

    var waited = 0;
    var timer = setInterval(function () {
        if (waited >= READY_TIMEOUT_MS) {
            clearInterval(timer);
            status.status = 'timeout';
            status.error = '画板未在 ' + READY_TIMEOUT_MS + 'ms 内就绪';
            dbg('timeout: ' + JSON.stringify(status));
            return;
        }
        waited += READY_POLL_MS;
        var found = collect();
        if (!found) return;
        clearInterval(timer);
        status.status = 'found-pad';

        // 找到画板后按间隔连提 total 次。每次都重新 collect：
        // 一题提交后画板可能被替换（下一题是新的组件实例），复用旧 pad 会注入到上一题。
        var done = 0;
        var submitTimer = setInterval(function () {
            var live = collect();
            if (!live) {
                status.errors.push('第 ' + (done + 1) + ' 次提交时画板已消失');
            } else {
                submitOnce(live);
            }
            done++;
            if (done >= total) {
                clearInterval(submitTimer);
                status.status = 'done';
                dbg('done: ' + JSON.stringify(status));
            }
        }, Math.max(0, interval));
    }, READY_POLL_MS);

    return JSON.stringify(status);
})();
