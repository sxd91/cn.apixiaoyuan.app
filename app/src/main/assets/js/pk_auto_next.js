// pk_auto_next.js —— 「结束页自动化：结算页自动开下一局」（内置版）
//
// 移植来源：cn.nizou.sxd `assets/js/cyclic.js`（2026-08-29 真机验证），
// 逐条保留三级策略，去掉对 native bridge（window.AutoOral）的依赖：
//
//   策略一（Vue 组件）：从 `#app.__vue_app__._instance` 起遍历组件树，
//     命中 `gotoHonorRoll` / `onAgain` 就直接调 —— 最干净，不触发页面重载。
//   策略二（按钮点击）：按文案前缀匹配「继续PK / 再战 / 再来 / 下一局 / 重来」，
//     对 button / [role=button] / class 含 btn|again|retry|next|start 的元素
//     以及 div / span / a / li 做点击（2026-08-29 放宽：不再要求 offsetParent 非空，
//     真机上有的按钮 offsetParent 为 null 但仍可点）。
//   策略三（兜底）：直接 `location.reload()` 回入口，重新进一局。
//
// 与老挂戏老叟的差异：那边的 `mode === 0`（发包模式）依赖 hook 改提交包，
// 内置架构下没有这个前提，所以**只保留 reload 兜底**，不做发包分支。
//
// 注入时机：由 PkH5Screen 在 onPageFinished 注入，每次页面加载后都会执行；
// 内部用 setInterval 最多尝试 15 次（按钮可能延迟渲染），成功后自动停止。
(function () {
    function dbg(m) {
        try { console.log('[pk_auto_next] ' + m); } catch (e) {}
    }

    function getInterval() {
        var v = window.__pk_next_interval;
        return (typeof v === 'number' && v >= 0) ? v : 1500;
    }

    // ---- 策略一：Vue 组件树 ----
    function findRoot() {
        try {
            var app = document.querySelector('#app');
            if (app && app.__vue_app__ && app.__vue_app__._instance) {
                return app.__vue_app__._instance;
            }
        } catch (e) {}
        try {
            var a2 = document.querySelector('#app');
            if (a2 && a2.__vue__) return a2.__vue__.$root;
        } catch (e) {}
        return null;
    }

    function walk3(node, d, cb) {
        if (!node || d > 14) return;
        if (node.component) {
            cb(node.component, d, true);
            walk3(node.component.subTree, d + 1, cb);
            return;
        }
        if (Array.isArray(node.children)) {
            for (var i = 0; i < node.children.length; i++) walk3(node.children[i], d + 1, cb);
        } else if (node.children && typeof node.children === 'object') {
            walk3(node.children, d + 1, cb);
        }
        if (node.dynamicChildren) {
            for (var j = 0; j < node.dynamicChildren.length; j++) {
                walk3(node.dynamicChildren[j], d + 1, cb);
            }
        }
    }

    function walk2(comp, d, cb) {
        if (!comp || d > 14) return;
        cb(comp, d, false);
        if (comp.$children) {
            for (var i = 0; i < comp.$children.length; i++) walk2(comp.$children[i], d + 1, cb);
        }
    }

    function strategyVue() {
        var root = findRoot();
        if (!root) return false;
        var done = false;
        function cb(inst, d, isV3) {
            if (done) return;
            var p = isV3
                ? (inst.proxy || inst.setupState || {})
                : (inst._setupProxy || inst._setupState || inst);
            if (typeof p.gotoHonorRoll === 'function') {
                try { p.gotoHonorRoll('resultPageJs', '', ''); done = true; } catch (e) {}
            } else if (typeof p.onAgain === 'function') {
                try { p.onAgain(); done = true; } catch (e) {}
            }
        }
        try {
            if (root.subTree) walk3(root.subTree, 0, cb);
            else walk2(root, 0, cb);
        } catch (e) {}
        return done;
    }

    // ---- 策略二：按钮文案匹配 ----
    function strategyButton() {
        var els = document.querySelectorAll(
            'button,[role=button],[class*=btn],[class*=again],[class*=retry],' +
            '[class*=next],[class*=start],div,span,a,li'
        );
        for (var i = 0; i < els.length; i++) {
            var el = els[i];
            var t = (el.textContent || '').trim();
            if (!t || t.length > 12) continue;
            if (/^(继续\s*PK|再战|再来|再玩|下一局|继续|重新开始|ok)/.test(t) ||
                /再来|再战|继续\s*PK|下一局|再来一局|重新开始/i.test(t)) {
                try {
                    el.click();
                    return true;
                } catch (e) {
                    try {
                        el.dispatchEvent(new MouseEvent('click', { bubbles: true }));
                        return true;
                    } catch (e2) {}
                }
            }
        }
        return false;
    }

    function triggerAgainNow() {
        if (strategyVue()) { dbg('vue strategy ok'); return true; }
        if (strategyButton()) { dbg('button strategy ok'); return true; }
        // 兜底：直接回入口重新匹配。
        setTimeout(function () {
            try { window.location.reload(); } catch (e) {}
        }, 900);
        return true;
    }

    var attempt = 0;
    var loop = setInterval(function () {
        attempt++;
        triggerAgainNow();
        if (attempt > 15) {
            clearInterval(loop);
            dbg('gave up after ' + attempt);
        }
    }, Math.max(getInterval(), 200));
    dbg('js injected, interval=' + getInterval());
})();
