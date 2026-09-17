package cn.apixiaoyuan.app.feature.repl

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.apixiaoyuan.app.core.database.AppDatabase
import cn.apixiaoyuan.app.core.database.Sample
import cn.apixiaoyuan.app.core.network.BaseUrlRegistry
import cn.apixiaoyuan.app.core.samples.SampleRepository
import cn.apixiaoyuan.app.core.session.PersistentCookieJar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Headers.Companion.toHeaders
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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

    /** 样本库入口。数据库不可用时为 null，存样本按钮据此禁用。 */
    private val sampleRepo: SampleRepository? by lazy {
        runCatching { SampleRepository(AppDatabase.get()) }.getOrNull()
    }

    /** 存样本的提示文案。null 表示无提示。 */
    var sampleHint by mutableStateOf<String?>(null)
        private set

    /** 是否已成功存过样本（用于按钮的短反馈）。 */
    var sampleSaved by mutableStateOf(false)
        private set

    /** 已注册域名别名 → host，供快捷填充。*/
    fun hostOptions(): List<Pair<String, String>> = listOf("leo", "ytk").mapNotNull { alias ->
        BaseUrlRegistry.resolve(alias)?.let { alias to it.toString() }
    }

    fun selectMethod(next: String) {
        requestMethod = next
        if (sampleHint != null) clearSampleHint()
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

    /** 清除存样本提示。修改 URL \/ Method \/ 重发时调用。 */
    fun clearSampleHint() {
        sampleHint = null
        sampleSaved = false
    }

    /**
     * 把当前构造的请求存为样本。
     *
     * 存的是**编码前**的形态：`headers` 逐行拼成 `name: value` 文本，
     * `requestBody` 是用户填的明文。重放时由 [cn.apixiaoyuan.app.feature.samples.SamplesViewModel]
     * 按 `needEncode` \/ `needDecode` 决定是否过 native 编解码。
     *
     * 编码 \/ 解码标记来自当前 Method 与 URL 的启发式判断，**不是确证事实**：
     *  - `needDecode`：URL 落在已确证需解码的三个接口路径里（知识点运用 \/ 试卷详情）
     *  - `needEncode`：URL 命中 `postSavedExp` 的路径
     * 用户在样本库里可以按实际结果手动纠正这两项（表字段可写）。
     *
     * 样本名默认用 `METHOD 路径末段 时间`，重名时 DAO 会 ABORT，
     * 这里把异常吞掉并给出「名字已存在」提示，不覆盖旧样本。
     */
    fun saveSample(customName: String? = null) {
        val repo = sampleRepo ?: run {
            sampleHint = "数据库未就绪，无法存样本"
            return
        }
        val target = url.trim()
        if (target.isBlank()) {
            sampleHint = "URL 为空"
            return
        }
        val name = customName?.trim()?.takeIf { it.isNotEmpty() }
            ?: defaultSampleName(target)
        viewModelScope.launch {
            val sample = Sample(
                name = name,
                description = null,
                sourceRequestId = null,
                method = requestMethod,
                url = target,
                headers = headerLines.filter { it.contains(":") }.joinToString("\n"),
                requestBody = body.takeIf { it.isNotBlank() },
                needEncode = looksLikeNeedEncode(target),
                needDecode = looksLikeNeedDecode(target),
                createdAt = System.currentTimeMillis(),
                lastReplayedAt = null,
                lastReplaySuccess = null,
            )
            val id = repo.create(sample)
            if (id != null) {
                sampleSaved = true
                sampleHint = "已存为样本：$name"
            } else {
                sampleSaved = false
                sampleHint = "样本名已存在：$name"
            }
        }
    }

    /** 默认样本名：`METHOD 路径末段 时间`。 */
    private fun defaultSampleName(target: String): String {
        val path = target.substringAfter("://", "").substringAfter("/", "").substringBefore("?")
        val tail = path.trimEnd('/').substringAfterLast('/').take(24).ifEmpty { "root" }
        val stamp = SimpleDateFormat("MMdd-HHmmss", Locale.US).format(Date())
        return "$requestMethod $tail $stamp"
    }

    /** 已确证需解码的接口路径（来自 docs/LOGIN-API.md 与 ApiRegistry）。 */
    private fun looksLikeNeedDecode(target: String): Boolean = listOf(
        "/leo-chinese/android/knowledge/usage",
        "/leo-chinese/android/knowledge",
        "/leo-exam/android/paper",
    ).any { target.contains(it) }

    /** 已确证需编码的接口路径：postSavedExp。 */
    private fun looksLikeNeedEncode(target: String): Boolean =
        target.contains("/leo-star/android/exercise/rank/login/attend")

    /**
     * 把一次「发送」落进请求流水。
     *
     * 与 [saveSample] 的区别：那个存样本（可重放），这个只记流水。
     * 两条路径的 `needEncode` / `needDecode` 判定共用同一对私有函数，
     * 避免「直接发送」与「存样本后重放」在编解码方向上分叉。
     *
     * 落库失败不打断请求 —— 历史写不进去不该让用户看不到响应。
     */
    private fun recordToHistory(
        method: String,
        url: String,
        statusCode: Int,
        success: Boolean,
        durationMs: Long,
        error: String?,
    ) {
        val repo = sampleRepo ?: return
        val headerText = headerLines
            .filter { it.contains(":") }
            .joinToString("\n")
            .takeIf { it.isNotBlank() }
        viewModelScope.launch {
            repo.recordRawRequest(
                method = method,
                url = url,
                headers = headerText,
                requestBody = body.takeIf { it.isNotBlank() },
                requestEncoded = looksLikeNeedEncode(url),
                statusCode = statusCode,
                success = success,
                durationMs = durationMs,
                responseDecoded = looksLikeNeedDecode(url),
                error = error,
            )
        }
    }

    /** 样本名与 URL 变化时清掉旧提示。 */
    fun onUrlChanged(next: String) {
        url = next
        if (sampleHint != null) clearSampleHint()
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
                recordToHistory(
                    method = requestMethod,
                    url = target,
                    statusCode = response.code,
                    success = response.isSuccessful,
                    durationMs = elapsedMs,
                    error = null,
                )
            } catch (t: Throwable) {
                elapsedMs = System.currentTimeMillis() - started
                statusCode = -1
                errorMessage = "${t.javaClass.simpleName}: ${t.message}"
                history.add(0, HistoryEntry(requestMethod, target, -1, elapsedMs))
                if (history.size > 50) history.removeAt(history.size - 1)
                recordToHistory(
                    method = requestMethod,
                    url = target,
                    statusCode = -1,
                    success = false,
                    durationMs = elapsedMs,
                    error = "${t.javaClass.simpleName}: ${t.message}",
                )
            } finally {
                loading = false
            }
        }
    }
}
