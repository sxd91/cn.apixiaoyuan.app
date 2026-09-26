package cn.apixiaoyuan.app.core.oldsimian

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Toast
import cn.apixiaoyuan.app.core.native.ContentBridge
import cn.apixiaoyuan.app.core.session.SessionStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.URLDecoder
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * PK H5 的原生桥 —— 注入为 `window.WebView` 与 `window.LeoSecureWebView`。
 *
 * ## 协议（记忆「H5 ↔ 原生桥协议」逐字取证，非推测）
 *
 * H5 调用：`window.WebView[能力](b64)`，payload =
 * `btoa(JSON.stringify({method, params:{arguments:[...], callback:"<回调名>"}}))`。
 * 原生必须在**同一个 window** 上回调：`window['<回调名>']('<base64结果>')`，
 * 结果约定为 JSON 数组 `[err, data...]`（err 为 null 表成功）。
 *
 * 回调名优先读 `params.callback`，其次扫 `arguments[0]` 里的 `trigger` /
 * `callback` 字符串字段（H5 把函数参数序列化成回调名注册到 window）。
 *
 * ## 为什么要注入两个名字
 *
 * H5 的桥层按「有无前缀」选对象：
 *  - 无前缀能力（openWebView / getUserInfo / toast …）→ `window.WebView[cap]`；
 *  - LeoSecure 前缀能力（dataEncrypt / dataDecrypt / requestConfig）→
 *    `window.LeoSecureWebView[cap]`。
 * 同一个实例注册两个名字即可全覆盖，不用维护两份逻辑。
 *
 * ## dataEncrypt / dataDecrypt（ds/i4 语义，2026-09-26 钉死）
 *
 *  - `dataEncrypt`：入参 `{base64}`（JSON 明文的 base64）→ Base64 解码 →
 *    **gzip 压缩** → `ContentBridge.encode`（native）→ Base64 编码 →
 *    回调 `[null, {result}]`；
 *  - `dataDecrypt`：入参 `{base64}`（密文的 base64）→ Base64 解码 →
 *    `ContentBridge.encode` → **gunzip** → Base64 编码 → 回调 `[null, {result}]`。
 *
 * 两者分别与项目 `NativeEncodeInstaller` / `NativeDecodeInstaller` 的顺序一致
 * （= 原版 ds/i4.c / ds/i4.a），但这里不能走网络层拦截器（是 JS 桥直调），
 * 所以在桥内独立实现同样的顺序。
 *
 * ## 线程与异常纪律
 *
 * `@JavascriptInterface` 方法跑在 JS 桥线程：**任何异常都会崩掉宿主进程**，
 * 所以每个方法体全部包 `runCatching`，回调统一 post 到主线程执行
 * （`evaluateJavascript` 必须主线程）。
 */
class PkWebViewBridge(
    private val appContext: Context,
    private val webView: WebView,
) {
    private val main = Handler(Looper.getMainLooper())

    // ==================== 无前缀能力 ====================

    /**
     * 用户信息 —— PK H5 首屏用户卡的**主要来源**。
     *
     * 没有这个桥时 H5 拿不到用户信息 → 首屏只有「0 胜 | 胜率 0%」没有名字头像
     * （真机症状：切年级触发 homepage 请求后才从响应兜底显示）。
     * 数据读 [SessionStore] 的当前用户缓存（由主页子账号列表 / PK 入口数据回填）。
     */
    @JavascriptInterface
    fun getUserInfo(payload: String?) {
        val info = runCatching {
            JSONObject().apply {
                put("userId", SessionStore.yfdU ?: 0L)
                put("userName", SessionStore.currentNickname ?: "我")
                put("nickName", SessionStore.currentNickname ?: "我")
                put("avatarUrl", SessionStore.currentAvatarUrl ?: "")
                put("userPendantUrl", "")
                put("gradeId", SessionStore.grade() ?: 0)
            }
        }.getOrDefault(JSONObject())
        respond(payload, ok(info))
    }

    /**
     * 打开子页 —— 「开始PK」等点击的真正通路。
     *
     * H5 传 `native://openWebView?url=<enc>&hideNavigation=true...` 声明式参数，
     * 这里解出 url 后在**当前 WebView** 加载（单容器策略：返回键可退回列表页，
     * 与原版「新开 WebView」观感一致）。缺这个桥 = 点击 PK 没反应（真机症状）。
     */
    @JavascriptInterface
    fun openWebView(payload: String?) {
        val url = extractOpenUrl(payload)
        if (!url.isNullOrBlank()) {
            main.post { runCatching { webView.loadUrl(url) } }
        }
        respond(payload, ok())
    }

    /** 关容器。单容器下退 H5 历史即可。 */
    @JavascriptInterface
    fun closeWebView(payload: String?) {
        main.post { runCatching { if (webView.canGoBack()) webView.goBack() } }
        respond(payload, ok())
    }

    /** 提示。消息形态两种都兜：纯字符串 / {message: "..."}。 */
    @JavascriptInterface
    fun toast(payload: String?) {
        val msg = extractToastMessage(payload)
        if (!msg.isNullOrBlank()) {
            main.post {
                runCatching { Toast.makeText(appContext, msg, Toast.LENGTH_SHORT).show() }
            }
        }
        respond(payload, ok())
    }

    @JavascriptInterface
    fun getDeviceInfo(payload: String?) {
        respond(payload, ok(JSONObject().put("pad", false).put("os", "android")))
    }

    @JavascriptInterface
    fun getImmerseStatusBarHeight(payload: String?) {
        respond(payload, ok(JSONObject().put("height", 0)))
    }

    /** 未登录拉起登录 —— 本项目账号在 App 内登录，这里只提示。 */
    @JavascriptInterface
    fun login(payload: String?) {
        main.post {
            runCatching { Toast.makeText(appContext, "请在 App 内登录", Toast.LENGTH_SHORT).show() }
        }
        respond(payload, ok())
    }

    /** 能力白名单。H5 侧「不实现也不影响主流程」，回空表即可。 */
    @JavascriptInterface
    fun getNativeCommandList(payload: String?) {
        respond(payload, ok(JSONArray()))
    }

    // ==================== LeoSecure 前缀能力 ====================

    /**
     * dataEncrypt：JSON 明文 → gzip → native → base64（= 原版 ds/i4.c）。
     * H5 把回调结果作为 octet-stream body 直接提交。
     */
    @JavascriptInterface
    fun dataEncrypt(payload: String?) {
        val out = runCatching {
            val json = JSONObject(decodePayloadJson(payload).orEmpty())
            val raw = decodeFlexibleBase64(json.getString("base64"))
            val mid = ContentBridge.encode(gzip(raw)) ?: error("ContentBridge 未就绪")
            b64(mid)
        }.getOrNull()
        respond(payload, if (out != null) ok(JSONObject().put("result", out)) else err("encrypt failed"))
    }

    /**
     * dataDecrypt：密文 base64 → native → gunzip → base64（= 原版 ds/i4.a）。
     * H5 用它解出题接口（match/v2）的加密 arraybuffer 响应。
     */
    @JavascriptInterface
    fun dataDecrypt(payload: String?) {
        val out = runCatching {
            val json = JSONObject(decodePayloadJson(payload).orEmpty())
            val raw = decodeFlexibleBase64(json.getString("base64"))
            val mid = ContentBridge.encode(raw) ?: error("ContentBridge 未就绪")
            b64(gunzip(mid))
        }.getOrNull()
        respond(payload, if (out != null) ok(JSONObject().put("result", out)) else err("decrypt failed"))
    }

    /** requestConfig（加签 + 公共参数）。PK 页不需要，回成功避免 Promise 悬挂。 */
    @JavascriptInterface
    fun requestConfig(payload: String?) {
        respond(payload, ok())
    }

    // ==================== 内部工具 ====================

    /** 成功回调体：`[null, data]` 的 base64。 */
    private fun ok(data: Any = JSONObject()): String =
        b64(JSONArray().put(JSONObject.NULL).put(data).toString())

    /** 失败回调体：`[err]` 的 base64。 */
    private fun err(msg: String): String = b64(JSONArray().put(msg).toString())

    private fun b64(s: String): String = Base64.encodeToString(s.toByteArray(), Base64.NO_WRAP)

    private fun b64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun decodePayloadJson(payload: String?): String? = runCatching {
        val raw = payload ?: return@runCatching null
        String(Base64.decode(raw, Base64.DEFAULT))
    }.getOrNull()

    /**
     * 兼容标准 / URL-safe base64（H5 的 `Base64.encode` 可能产出 `-_` 字符集，
     * 解码前统一归一化 + 补齐 padding）。
     */
    private fun decodeFlexibleBase64(s: String): ByteArray {
        val normalized = s.replace('-', '+').replace('_', '/').replace("\n", "").replace("\r", "")
        val padded = normalized + "=".repeat((4 - normalized.length % 4) % 4)
        return Base64.decode(padded, Base64.DEFAULT)
    }

    /** 在 window 上回调 H5 注册的回调名。回调名白名单校验，防注入。 */
    private fun respond(payload: String?, resultB64: String) {
        val cb = extractCallback(payload) ?: return
        if (!cb.matches(Regex("[A-Za-z0-9_$]+"))) return
        main.post {
            runCatching {
                webView.evaluateJavascript("window['$cb'] && window['$cb']('$resultB64')", null)
            }
        }
    }

    /** 回调名：`params.callback` 优先，其次 `arguments[i].trigger / .callback`。 */
    private fun extractCallback(payload: String?): String? = runCatching {
        val json = JSONObject(decodePayloadJson(payload) ?: return@runCatching null)
        val params = json.optJSONObject("params") ?: return@runCatching null
        params.optString("callback").takeIf { it.isNotBlank() && it != "null" }?.let { return@runCatching it }
        val args = params.optJSONArray("arguments") ?: return@runCatching null
        for (i in 0 until args.length()) {
            val a = args.optJSONObject(i) ?: continue
            a.optString("trigger").takeIf { it.isNotBlank() && it != "null" }?.let { return@runCatching it }
            a.optString("callback").takeIf { it.isNotBlank() && it != "null" }?.let { return@runCatching it }
        }
        null
    }.getOrNull()

    /** 从 openWebView 的声明式参数里解出真实 url。 */
    private fun extractOpenUrl(payload: String?): String? = runCatching {
        val json = JSONObject(decodePayloadJson(payload) ?: return@runCatching null)
        val args = json.optJSONObject("params")?.optJSONArray("arguments")
            ?: return@runCatching null
        for (i in 0 until args.length()) {
            val a = args.optJSONObject(i) ?: continue
            val schemas = a.optJSONArray("schemas")
            if (schemas != null) {
                for (j in 0 until schemas.length()) {
                    val s = schemas.optString(j)
                    val m = Regex("url=([^&]+)").find(s) ?: continue
                    return@runCatching URLDecoder.decode(m.groupValues[1], "UTF-8")
                }
            }
            a.optString("url").takeIf { it.isNotBlank() }?.let { return@runCatching it }
        }
        null
    }.getOrNull()

    private fun extractToastMessage(payload: String?): String? = runCatching {
        val json = JSONObject(decodePayloadJson(payload) ?: return@runCatching null)
        val args = json.optJSONObject("params")?.optJSONArray("arguments")
            ?: return@runCatching null
        for (i in 0 until args.length()) {
            if (args.optString(i).isNotBlank() && args.optJSONObject(i) == null) {
                return@runCatching args.optString(i)
            }
            val obj = args.optJSONObject(i) ?: continue
            obj.optString("message").takeIf { it.isNotBlank() }?.let { return@runCatching it }
            obj.optString("text").takeIf { it.isNotBlank() }?.let { return@runCatching it }
        }
        null
    }.getOrNull()

    private fun gzip(raw: ByteArray): ByteArray = ByteArrayOutputStream().use { out ->
        GZIPOutputStream(out).use { it.write(raw) }
        out.toByteArray()
    }

    private fun gunzip(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPInputStream(data.inputStream()).use { gz ->
            val buf = ByteArray(8192)
            while (true) {
                val n = gz.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
            }
        }
        return out.toByteArray()
    }
}