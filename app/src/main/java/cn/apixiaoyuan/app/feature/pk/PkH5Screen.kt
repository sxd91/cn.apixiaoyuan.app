package cn.apixiaoyuan.app.feature.pk

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceError
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import cn.apixiaoyuan.app.core.design.icon.AppIcons
import cn.apixiaoyuan.app.core.oldsimian.PkJsInjector
import cn.apixiaoyuan.app.core.session.SessionStore

/**
 * 口算 PK 的 H5 容器。
 *
 * 这是模块 8 的核心 —— 口算 PK 全部交互在 H5 里，原生只做三件事：
 *
 *  1. **提供容器**：全屏 WebView，开启 JS 与 DOM Storage
 *  2. **同步登录态**：把原生 [SessionStore] 里存的 cookie 注入
 *     WebView 的 [CookieManager]，让 H5 侧的请求也带上登录态
 *  3. **拦截 scheme**：原版 H5 用 `leo://` scheme 调原生能力，
 *     这里拦截并记录，未实现的先忽略
 *
 * **不做的事**：
 *  - 不注入 JS bridge（原版的 `addJavascriptInterface` 暴露了哪些方法
 *    还没从 smali 确证，贸然注入会引入未知行为；等确证后再补）
 *  - 不解析 H5 内部的答题协议（那是 H5 私有协议，原生不参与）
 *
 * 关键实现点：**Cookie 同步的时机**。WebView 的 CookieManager 与
 * OkHttp 的 PersistentCookieJar 是两套独立存储 —— H5 发起请求时用的
 * 是 CookieManager 里的 cookie，不是 OkHttp 的。必须在 `loadUrl` 之前
 * 把 SessionStore 里的 cookie 逐个写进 CookieManager，否则 H5 侧
 * 表现为未登录。
 *
 * @param viewModel PK 状态机
 * @param onFinish   H5 侧要关闭容器时的回调（如 `leo://close`），
 *                   由调用方决定去哪；当前未接线，留出口
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PkH5Screen(
    viewModel: PkViewModel,
    onFinish: () -> Unit = {},
) {
    
    // WebView 实例在 composition 期间创建；销毁由 AndroidView 的 onRelease
    // 负责（不能放 DisposableEffect.onDispose，详见下方 AndroidView 注释）。
    val context = LocalContext.current
    val webView = remember {
        WebView(context).apply {
            // ---- View 焦点：PK 容器绝不能持有它（真机崩溃的根因）----
            //
            // 崩溃栈（dropbox data_app_crash@1790394023891，2026-09-26 11:40）：
            //   Choreographer.doFrame
            //     → Compose applyChanges (ez0.h / ma.i)
            //     → ViewGroup.removeViewInLayout
            //     → ViewGroup.removeViewInternal (ViewGroup.java:5847)
            //     → View.rootViewRequestFocus (View.java:9103)
            //     → AndroidComposeView.requestFocus
            //     → "Compose Runtime internal error
            //        (pending composition has not been applied)"
            //
            // 机理：`ViewGroup.removeViewInternal` 发现被移除的 View 正是
            // `mFocused` 时，会调 `rootViewRequestFocus()` 向上层重新找焦点
            // 持有者；此刻 Compose 正处在 apply 阶段，焦点落到
            // `AndroidComposeView` 上便触发重入合成 → 抛错崩溃。
            //
            // 而 WebView 天生可聚焦：clickable 的 View 在 touch mode 下触摸即
            // `requestFocus()`。所以「点一下 PK 页面，再返回」必然命中这条路径。
            //
            // 这里设 `isFocusable=false` 从源头断掉；**不动**
            // `descendantFocusability`，保留 WebView 内部 input 弹输入法的能力
            // （HTML 输入焦点走 chromium 内部子 View，不依赖 WebView 自身可聚焦）。
            isFocusable = false
            isFocusableInTouchMode = false

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadsImagesAutomatically = true
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                // UA 对齐原版 vgo 容器（取证：BaseWebApp 继承腾讯 X5 WebView，
                // H5 侧按 UA 特征区分容器能力；自加的 ReverseOldGuy 标记可能被
                // H5 风控当异常客户端，去掉）。
            }

            // 同步登录态：把 SessionStore 的 cookie 写进 CookieManager。
            // 必须在 loadUrl 之前 —— WebView 用 CookieManager 发请求，
            // 不是用 OkHttp 的 PersistentCookieJar。
            CookieManager.getInstance().apply {
                setAcceptCookie(true)
            }
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            syncCookiesToWebView(viewModel.h5Url)

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    viewModel.setProgress(5)
                    viewModel.webError = null
                    // 重置注入标记：同一次加载 onPageFinished 可能回调多次，
                    // 不重置会导致「老挂戏老叟」脚本被叠加注入多轮。
                    view?.let { PkJsInjector.markPageStarted(it) }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    viewModel.setProgress(100)
                    view?.title?.takeIf { it.isNotBlank() }?.let { viewModel.webTitle = it }
                    // 「老挂戏老叟」PK 侧注入：去排行榜动效 / 结算页自动开下一局。
                    // 本项目 PK 容器是自己的 WebView，直接 evaluateJavascript 即可，
                    // 不需要像 cn.nizou.sxd 那样 hook 宿主的 loadUrl。
                    view?.let { PkJsInjector.injectIfEnabled(it) }
                }
                
                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?,
                ) {
                    // 只对主文档报错 —— 子资源失败（图片、埋点）不该阻塞整页。
                    if (request?.isForMainFrame == true) {
                        viewModel.webError = "H5 加载失败：${error?.description ?: "未知错误"}"
                        viewModel.setProgress(0)
                    }
                }
                
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?,
                ): Boolean {
                    val url = request?.url?.toString() ?: return false
                    return handleScheme(url, onFinish)
                }
                
                @Deprecated("Deprecated in API 24, but kept for older WebView")
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    return url?.let { handleScheme(it, onFinish) } ?: false
                }
            }
        }
    }
    
    // 原生**主动驱动加载**的目标 URL。
    //
    // 只认这个值，绝不用 `WebView.url` 做判断 —— PK H5 是 SPA（hash 路由），
    // H5 内部导航会把 `WebView.url` 改写成 `pk.html#/xxx`，此时
    // `view.url != h5Url` **恒为真** → `AndroidView.update` 每次重组都 loadUrl
    // → 表现就是「PK 页面一直在刷新」。
    //
    // 这里记住「原生最近一次下发的目标」：只有目标变化，或用户显式重试
    // （reloadToken 递增）才重新加载。H5 内部的导航完全不触发原生 loadUrl，
    // 也就不再打断 SPA 自身的路由。
    var loadedTarget by remember { mutableStateOf<Pair<String, Int>?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            // 注意：这里**不能** destroy WebView —— 销毁已移交给 AndroidView 的
            // onRelease（见下方 AndroidView）。onDispose 与合成同帧同步执行，
            // 此时 WebView 正在被移出视图树，destroy 会与 requestFocus 重入竞争
            // 导致 Compose 运行时崩溃。这里只做非破坏性的清理。
            //
            // clearFocus 是必要的一半：即使 WebView 本身不可聚焦
            // （见构造处的 isFocusable=false），focus 也可能落在它的某个子 View 上，
            // removeViewInternal 同样会走 rootViewRequestFocus 那条崩溃路径。
            runCatching { webView.clearFocus() }
            runCatching { webView.stopLoading() }
        }
    }

    // ---- 系统返回键 ----
    //
    // 语义对齐原版容器（BaseWebApp 的返回 = 先退 H5 历史，退无可退才关容器）：
    //  1. H5 自己有历史（含 SPA 的 pushState/hash 导航，Chromium 会记进
    //     navigation controller）→ `goBack()`，不退容器；
    //  2. 已在 H5 首页 → 回调 [onFinish]，由调用方决定去哪。
    //     [PkScreen] 传的是 `navController.popBackStack()`，即回主页。
    BackHandler(enabled = true) {
        goBackOrFinish(webView, onFinish)
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            
            // ---- 顶部进度条 ----
            if (viewModel.webProgress in 1..99) {
                LinearProgressIndicator(
                    progress = { viewModel.webProgress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            
            // ---- 标题栏 ----
            //
            // 返回按钮是原版有、而此前这里漏掉的：原版 PK 走独立的 WebApp
            // Activity（`BaseWebAppActivity`），它的标题栏由容器统一提供返回控件，
            // 点它 = 退 H5 历史 / 关容器回主页。这里此前只有 `Text(webTitle)`，
            // 用户点不到「返回」，只能靠系统返回键 —— 这就是「点 PK 页返回按钮
            // 没法像原版一样回主页」的原因。
            //
            // 语义与 [BackHandler] 完全一致，共用 [goBackOrFinish]，避免两处
            // 行为漂移。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { goBackOrFinish(webView, onFinish) }) {
                    Icon(
                        imageVector = AppIcons.Back,
                        contentDescription = "返回",
                    )
                }
                Text(
                    text = viewModel.webTitle,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (viewModel.loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }
            
            // ---- WebView ----
            AndroidView(
                factory = { webView },
                modifier = Modifier.fillMaxSize(),
                update = { view ->
                    // Cookie 同步放在 update 而不是只在 remember 里做一次：
                    // 登录可能发生在进入 PK 页**之后**（或在别处刷新了会话），
                    // 只在创建时同步一次的话，那种情况下 WebView 仍拿不到登录态，
                    // 表现就是 H5 里显示未登录 / 401 SolarAuthFilter。
                    // update 在每次重组与 h5Url 变化时都会跑，幂等且成本可接受。
                    syncCookiesToWebView(viewModel.h5Url)

                    // ---- 加载判定：只认「原生下发的目标」，不用 view.url ----
                    //
                    // 此前是 `if (view.url != viewModel.h5Url && webError == null)`，
                    // 对 SPA 恒为真 → 无限刷新（详见 nativeLoadTarget 的注释）。
                    // 现在：目标 = (h5Url, reloadToken)；仅当与上次下发目标不同
                    // 才 loadUrl。H5 内部路由变化不再触发任何原生加载。
                    val target = viewModel.h5Url to viewModel.reloadToken
                    if (viewModel.webError == null && target != loadedTarget) {
                        loadedTarget = target
                        view.loadUrl(viewModel.h5Url)
                    }
                },
                // 销毁必须交给 AndroidView 的 onRelease：它在 View 被移出视图树
                // **之后**才回调。此前放在 DisposableEffect(Unit).onDispose 里会崩：
                // 返回时 Compose 先 removeViewInLayout 摘掉 WebView，摘除过程触发
                // requestFocus → 重入合成；而 onDispose 与合成同帧同步执行 destroy()，
                // 两者竞争抛出 "pending composition has not been applied"
                // （真机崩溃栈底：ViewGroup.removeViewInLayout → ... → requestFocus）。
                onRelease = { view -> releaseWebView(view) },
            )
        }
        
        // ---- 错误覆盖层 ----
        viewModel.webError?.let { err ->
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Card(modifier = Modifier.padding(24.dp)) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = err,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = viewModel.h5Url,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        TextButton(onClick = { viewModel.reload() }) { Text("重试") }
                    }
                }
            }
        }
    }
}

/**
 * 「返回」的统一实现：先退 H5 历史，退无可退才关容器回主页。
 *
 * 标题栏的返回按钮与系统返回键（[BackHandler]）**共用**这一个实现，
 * 保证两条入口行为一致 —— 此前标题栏根本没有返回控件，只能在系统返回键里
 * 写内联逻辑，改一处漏一处。
 *
 * H5 的 hash 路由导航（SPA 的 `#/xxx`）会被 Chromium 记进 navigation
 * controller，所以 `canGoBack()` 同样覆盖 SPA 内部的前进后退。
 *
 * @param webView   PK 容器
 * @param onFinish  H5 首页再返回时的回调（由 [PkScreen] 决定，当前 = 回主页）
 */
private fun goBackOrFinish(webView: WebView, onFinish: () -> Unit) {
    if (webView.canGoBack()) webView.goBack() else onFinish()
}

/**
 * 安全销毁 WebView（由 AndroidView 的 onRelease 调用，此时 View 已移出视图树）。
 *
 * 顺序是有讲究的，真机崩溃倒逼出的三条：
 *
 *  1. **先从父容器摘除**。onRelease 时通常已被摘除，但若 View 仍挂在某个
 *     ViewGroup 上，直接 destroy 会让父容器在后续布局/焦点遍历中碰到已销毁的
 *     实例。用 runCatching 包住是因为"已不在树里"是常态，抛异常无意义。
 *  2. **清空回调与 JS 开关**。webViewClient 持有 viewModel 与 onFinish 引用，
 *     不清会让整棵 Activity 泄漏到 WebView 的内部线程；同时关掉 JS 阻断
 *     页面里还在跑的定时器继续回调原生。
 *  3. **stopLoading 再 destroy**。destroy 前必须停掉未完成的加载，否则
 *     网络线程回调已销毁的 WebView 会触发 native 层崩溃（chromium）。
 *
 * 整个流程用 runCatching 兜底：销毁阶段的任何异常都不该升级成用户可见的崩溃。
 */
private fun releaseWebView(view: WebView) {
    runCatching {
        // 先清焦点：removeView 时若 WebView 或其子 View 仍持有焦点，
        // ViewGroup.removeViewInternal 会走 rootViewRequestFocus()，
        // 把焦点交给 AndroidComposeView → 重入合成崩溃
        // （真机栈见构造处 isFocusable 的注释）。clearFocus 要在 destroy 之前。
        view.clearFocus()
        view.stopLoading()
        view.webViewClient = WebViewClient()
        view.settings.javaScriptEnabled = false
        (view.parent as? android.view.ViewGroup)?.removeView(view)
        view.removeAllViews()
        view.destroy()
    }
}
/**
 * 把 [SessionStore] 里的登录态 cookie 同步进 WebView 的 CookieManager。
 *
 * 这是 H5 侧能识别登录态的唯一通道 —— CookieManager 与 OkHttp 的
 * [cn.apixiaoyuan.app.core.session.PersistentCookieJar] 是两套独立存储。
 *
 * 三个必须踩对的点：
 *
 *  1. **URL 必须是合法 host**。真机 cookie 里 `ks_*` 那批挂在
 *     `.yuanfudao.com`（带前导点，hostOnly=false），直接拼
 *     `https://.yuanfudao.com` 不是合法 URL，`setCookie` 会静默失败。
 *     这里去掉前导点，并对每个 cookie 各自的 domain 调一次。
 *
 *  2. **每个 cookie 按其 domain 落盘**，不能全塞到 H5 页面 host 下 ——
 *     cookie 的 domain 归属由服务端 `Set-Cookie` 决定，WebView 发请求时
 *     按 RFC 6265 匹配，放错域等于没放。
 *
 *  3. **expires 用 HTTP 日期格式**，不是 epoch 毫秒。
 *     `CookieManager.setCookie` 解析 `Expires=` 时按 `EEE, dd MMM yyyy HH:mm:ss z`
 *     解析，写数字会被忽略，cookie 退化成会话 cookie，WebView 进程一回收就丢。
 *
 * @param pageUrl H5 入口 URL，仅用于兜底 —— domain 为空的条目落到它上面。
 */
private fun syncCookiesToWebView(pageUrl: String) {
    val cm = CookieManager.getInstance()
    val fallbackHost = runCatching { java.net.URI(pageUrl).host }.getOrNull()

    SessionStore.loadCookies().forEach { entry ->
        if (entry.value.isEmpty()) return@forEach

        val host = entry.domain.removePrefix(".").ifEmpty { fallbackHost ?: return@forEach }

        val cookieString = buildString {
            append(entry.name).append('=').append(entry.value)
            append("; domain=").append(host)
            append("; path=").append(entry.path.ifEmpty { "/" })
            if (entry.expiresAt > 0L) {
                append("; expires=").append(httpDate(entry.expiresAt))
            }
            if (entry.secure) append("; Secure")
        }
        cm.setCookie("https://$host", cookieString)
    }
    cm.flush()
}

/** epoch 毫秒 → HTTP 日期（`EEE, dd MMM yyyy HH:mm:ss z`，GMT）。 */
private fun httpDate(epochMillis: Long): String =
    java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", java.util.Locale.US)
        .apply { timeZone = java.util.TimeZone.getTimeZone("GMT") }
        .format(java.util.Date(epochMillis))

/**
 * 拦截 `leo://` scheme。
 *
 * 原版 H5 通过这个 scheme 调原生能力，已知的有：
 *  - `leo://openWebView?url=...` —— 打开新 WebView
 *    （MT APK MCP 取证：`LeoPkAppWidgetProvider` 等类内含 `leo://openWebView?url=`）
 *  - `leo://close` —— 关闭当前容器
 *  - `leo://back` —— 退当前容器历史（对齐原生返回语义）
 *
 * 当前处理 `close` / `back`（都回退），其余记录日志后忽略。
 * 完整 scheme 表待从原版 smali 的 WebView 容器实现里挖出。
 *
 * @return true 表示已消费该 URL，WebView 不再加载它
 */
private fun handleScheme(url: String, onFinish: () -> Unit): Boolean {
    if (!url.startsWith("leo://")) return false
    
    val uri = Uri.parse(url)
    when (uri.host) {
        "close", "back", "finish" -> {
            onFinish()
            return true
        }
        "openWebView" -> {
            // 原版会新开一个 WebView 加载 url 参数里的地址。
            // 当前未实现多 WebView 栈，记录后让 WebView 自己处理（会失败，但不崩）。
            return false
        }
        else -> {
            return true
        }
    }
}
