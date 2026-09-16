package cn.apixiaoyuan.app.feature.repl

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.apixiaoyuan.app.core.network.BaseUrlRegistry
import cn.apixiaoyuan.app.core.session.PersistentCookieJar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Headers.Companion.toHeaders
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * 协议请求台状态机。
 *
 * 与 [cn.apixiaoyuan.app.feature.api.ApiViewModel] 的区别：后者从
 * `ApiRegistry` 的静态元数据出发（路径与参数固定，用户只填值）；
 * 本类**完全自由**——URL / Method / Headers / Body 全部手工输入，
 * 用于打那些还没落盘 Kotlin 定义、或压根不在接口清单里的请求。
 *
 * 共享 [PersistentCookieJar]，所以登录态自动带上；
 * 不走 `@NeedDecode` 拦截，看到的是原始字节。
 *
 * 域名快捷填充：用户可从已注册的 [BaseUrlRegistry] 里取 leo / ytk
 * 的 host，避免手敲完整域名。
 */
class ReplViewModel : ViewModel() {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .cookieJar(PersistentCookieJar)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /** 请求方法。*/
    var requestMethod by mutableStateOf("GET")
        private set

    /** 完整 URL。*/
    var url by mutableStateOf("")

    /** 请求头，每行一个 `name: value`。*/
    val headerLines = mutableStateListOf("Accept: application/json")

    /** 请求体（仅 POST/PUT/PATCH/DELETE 有意义）。*/
    var body by mutableStateOf("")

    /** 是否正在请求。*/
    var loading by mutableStateOf(false)
        private set

    /** 响应状态码。-1 表示未发出或网络失败。*/
    var statusCode by mutableStateOf(-1)
        private set

    /** 响应头（展平为 `name: value`）。*/
    val responseHeaders = mutableStateListOf<String>()

    /** 响应体原文。*/
    var responseBody by mutableStateOf<String?>(null)
        private set

    /** 耗时毫秒。*/
    var elapsedMs by mutableStateOf(0L)
        private set

    /** 错误信息。*/
    var errorMessage by mutableStateOf<String?>(null)
        private set

    /** 请求历史（最近 50 条，内存态）。*/
    val history = mutableStateListOf<HistoryEntry>()

    data class HistoryEntry(
        val method: String,
        val url: String,
        val status: Int,
        val elapsedMs: Long,
    )

    /** 可选方法。*/
    val methods = listOf("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS")

    /** 已注册域名别名 → host，供快捷填充。*/
    fun hostOptions(): List<Pair<String, String>> = listOf("leo", "ytk").mapNotNull { alias ->
        BaseUrlRegistry.resolve(alias)?.let { alias to it.toString() }
    }

    fun selectMethod(next: String) {
        requestMethod = next
    }

    fun addHeader() {
        headerLines.add("")
    }

    fun removeHeader(index: Int) {
        if (index in headerLines.indices) headerLines.removeAt(index)
    }

    fun setHeader(index: Int, value: String) {
        if (index in headerLines.indices) headerLines[index] = value
    }

    /** 用别名 host 拼一个起始 URL。*/
    fun applyHost(alias: String) {
        BaseUrlRegistry.resolve(alias)?.let { base ->
            url = base.toString().trimEnd('/') + (url.takeIf { it.startsWith("/") } ?: "/")
        }
    }

    fun clearResponse() {
        statusCode = -1
        responseHeaders.clear()
        responseBody = null
        errorMessage = null
        elapsedMs = 0L
    }

    /** 发送请求。*/
    fun send() {
        if (loading) return
        val target = url.trim()
        if (target.isBlank()) {
            errorMessage = "URL 为空"
            return
        }
        loading = true
        clearResponse()

        viewModelScope.launch {
            val started = System.currentTimeMillis()
            try {
                val headers = headerLines
                    .filter { it.contains(":") }
                    .associate { line ->
                        val idx = line.indexOf(':')
                        line.substring(0, idx).trim() to line.substring(idx + 1).trim()
                    }
                    .toHeaders()

                val reqBody = if (requestMethod in listOf("POST", "PUT", "PATCH", "DELETE")) {
                    body.toRequestBody(
                        "application/json".toMediaType(),
                    )
                } else null

                val request = Request.Builder()
                    .url(target)
                    .method(requestMethod, reqBody)
                    .headers(headers)
                    .build()

                val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }
                elapsedMs = System.currentTimeMillis() - started
                statusCode = response.code
                response.headers.forEach { (n, v) -> responseHeaders.add("$n: $v") }
                val raw = withContext(Dispatchers.IO) { response.body.string() }
                responseBody = raw.take(500_000)
                history.add(0, HistoryEntry(requestMethod, target, response.code, elapsedMs))
                if (history.size > 50) history.removeAt(history.size - 1)
            } catch (t: Throwable) {
                elapsedMs = System.currentTimeMillis() - started
                statusCode = -1
                errorMessage = "${t.javaClass.simpleName}: ${t.message}"
                history.add(0, HistoryEntry(requestMethod, target, -1, elapsedMs))
                if (history.size > 50) history.removeAt(history.size - 1)
            } finally {
                loading = false
            }
        }
    }
}
