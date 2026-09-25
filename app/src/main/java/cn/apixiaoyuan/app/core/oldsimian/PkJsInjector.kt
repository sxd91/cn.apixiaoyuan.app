package cn.apixiaoyuan.app.core.oldsimian

import android.webkit.WebView

/**
 * PK 页（H5）的 JS 注入器 —— **内置架构的天然优势**。
 *
 * ## 为什么这边不需要 hook
 *
 * 参考项目 cn.nizou.sxd 是 LSPosed 模块，PK 交互全在**宿主**的 WebView 里，
 * 所以它必须 hook 宿主的 `WebView.loadUrl` / 加载回调，才能在 H5 页面里
 * 塞进自己的脚本（见其 `WebViewHook`）。
 *
 * 本项目的 PK 容器是**自己的** [cn.apixiaoyuan.app.feature.pk.PkH5Screen]，
 * 直接调 [WebView.evaluateJavascript] 就能注入 —— 同一批脚本，省掉整层 hook。
 *
 * ## 注入时机
 *
 * 必须在 `onPageFinished` 之后（此时 `document` 就绪、Vue 可能已挂载）。
 * 但 `onPageFinished` 对同一次加载可能回调多次（含 iframe），因此调用方
 * 需配合 [markPageStarted] 在 `onPageStarted` 时重置标志，保证**每次页面
 * 加载只注入一轮** —— 否则 [PkJsInjector] 的脚本会叠加多个 `setInterval`。
 *
 * ## 脚本清单
 *
 *  | 脚本 | 对应开关 | 作用 |
 *  |---|---|---|
 *  | `js/pk_no_anim.js`    | [OldSimianPrefs.noRankingAnim]  | CSS 动画/过渡归零 + 音效静音 |
 *  | `js/pk_auto_next.js`  | [OldSimianPrefs.autoNextRound]  | 结算页自动开下一局 |
 *  | `js/pk_auto_stroke.js`| [OldSimianPrefs.pkStrokeEnabled]| 题目页自动注入笔迹并提交 |
 *
 * 三者独立：只想去动画、不想自动连开、或只想自动交笔迹的人可以各开各的。
 */
object PkJsInjector {

    /** 已注入过脚本的页面标记（按 WebView 实例区分，避免多 WebView 串味）。 */
    private val injected = java.util.WeakHashMap<WebView, Boolean>()

    /** 在 `onPageStarted` 里调用，重置注入标记。 */
    fun markPageStarted(webView: WebView) {
        injected.remove(webView)
    }

    /**
     * 在 `onPageFinished` 里调用，按当前开关注入脚本。
     *
     * 无开关开启时不注入任何东西（连空脚本都不执行），
     * 保证「功能默认关 = 行为与没这个功能完全一致」。
     *
     * @param webView PK 容器
     * @return 本次实际注入的脚本数量，便于调用方打日志
     */
    fun injectIfEnabled(webView: WebView): Int {
        val prefs = OldSimianPrefs
        if (!prefs.noRankingAnim && !prefs.autoNextRound && !prefs.pkStrokeEnabled) return 0
        if (injected[webView] == true) return 0
        injected[webView] = true

        var count = 0
        if (prefs.noRankingAnim) {
            if (inject(webView, "js/pk_no_anim.js")) count++
        }
        if (prefs.autoNextRound) {
            // 先注入间隔配置，再注入主脚本（主脚本读 window.__pk_next_interval）。
            val interval = prefs.nextRoundIntervalMs.coerceAtLeast(0)
            webView.evaluateJavascript("window.__pk_next_interval=$interval;", null)
            if (inject(webView, "js/pk_auto_next.js")) count++
        }
        if (prefs.pkStrokeEnabled) {
            // 同套路：先把参数写进 window，再注入主脚本。
            // 脚本读 window.__pk_stroke_count / window.__pk_stroke_interval。
            val n = prefs.pkStrokeCount.coerceIn(
                OldSimianPrefs.PK_STROKE_COUNT_MIN,
                OldSimianPrefs.PK_STROKE_COUNT_MAX,
            )
            val iv = prefs.pkStrokeIntervalMs.coerceIn(
                OldSimianPrefs.PK_STROKE_INTERVAL_MIN,
                OldSimianPrefs.PK_STROKE_INTERVAL_MAX,
            )
            webView.evaluateJavascript(
                "window.__pk_stroke_count=$n;window.__pk_stroke_interval=$iv;",
                null,
            )
            if (inject(webView, "js/pk_auto_stroke.js")) count++
        }
        return count
    }

    /** 从 assets 读脚本并执行。读不到 / 执行失败都静默返回 false，不影响 H5 本身。 */
    private fun inject(webView: WebView, assetPath: String): Boolean = runCatching {
        val js = webView.context.assets.open(assetPath)
            .bufferedReader()
            .use { it.readText() }
        if (js.isBlank()) return@runCatching false
        webView.evaluateJavascript(js, null)
        true
    }.getOrDefault(false)
}