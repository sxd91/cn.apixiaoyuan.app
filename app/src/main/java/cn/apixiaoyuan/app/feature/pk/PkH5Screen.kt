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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
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
    
    // WebView 实例在 composition 期间创建，DisposableEffect 负责销毁。
    val context = LocalContext.current
    val webView = remember {
        WebView(context).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                loadsImagesAutomatically = true
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                userAgentString = userAgentString + " ReverseOldGuy/1.0"
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
    
    DisposableEffect(Unit) {
        onDispose {
            webView.stopLoading()
            webView.destroy()
        }
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = viewModel.webTitle,
                    style = MaterialTheme.typography.titleSmall,
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
                    // 只在 URL 变化时重新加载。
                    if (view.url != viewModel.h5Url && viewModel.webError == null) {
                        view.loadUrl(viewModel.h5Url)
                    }
                },
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
 *  - `leo://close` —— 关闭当前容器
 *
 * 当前只处理 `close`（回上一页），其余记录日志后忽略。
 * 完整 scheme 表待从原版 smali 的 WebView 容器实现里挖出。
 *
 * @return true 表示已消费该 URL，WebView 不再加载它
 */
private fun handleScheme(url: String, onFinish: () -> Unit): Boolean {
    if (!url.startsWith("leo://")) return false
    
    val uri = Uri.parse(url)
    when (uri.host) {
        "close" -> {
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
