// pk_no_anim.js —— 「去除排行榜展示动效」（内置版）
//
// 移植来源：cn.nizou.sxd `assets/js/fastsettle.js`（2026-08-29 真机验证）。
// 只保留与「展示动效」相关的两条，删掉与答题链耦合的部分：
//
//   1. CSS 动画 / 过渡压到 0s —— 切题动画、排行榜滚动展示动效随之消失；
//   2. 音效静音 —— new Audio().play() 短路。
//
// **刻意不做的两件事**（原版注释里踩过的坑，逐条继承）：
//   - 不做「持续自动画线」：每 120ms 画一笔会让前端 recognize 的
//     「停止 700ms 后判题」定时器永不触发，判题链断裂（真机实测 30 题只提交 1 题）；
//   - 不做「setTimeout 0ms 劫持」：没有 OCR 回调恒真补丁时，0ms 跳题会在
//     OCR 返回前跳题，答题记录错乱。
//   本脚本只动样式与音频，不碰答题节奏 —— 这是安全的边界。
(function () {
    try {
        // ---- 1. CSS 动画 / 过渡 0s ----
        if (!document.getElementById('__pk_no_anim_style__')) {
            var st = document.createElement('style');
            st.type = 'text/css';
            st.id = '__pk_no_anim_style__';
            st.textContent =
                '*{transition:none!important;animation-duration:0s!important;' +
                'animation-delay:0s!important;transition-duration:0s!important;' +
                'transition-delay:0s!important;}';
            (document.head || document.documentElement).appendChild(st);
        }
        // ---- 2. 音效静音 ----
        try {
            var OrigAudio = window.Audio;
            window.Audio = function () {
                return { play: function () { return null; }, pause: function () {}, volume: 0 };
            };
            if (OrigAudio && OrigAudio.prototype) {
                OrigAudio.prototype.play = function () { return null; };
            }
        } catch (e) { /* 静音失败不影响样式 */ }
        try { console.log('[pk_no_anim] injected'); } catch (e) {}
    } catch (e) {}
})();
