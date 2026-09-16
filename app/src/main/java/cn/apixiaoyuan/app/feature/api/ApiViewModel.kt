package cn.apixiaoyuan.app.feature.api

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.apixiaoyuan.app.core.network.BaseUrlRegistry
import cn.apixiaoyuan.app.core.network.ServiceLocator
import cn.apixiaoyuan.app.core.session.PersistentCookieJar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * 接口浏览器状态机。
 *
 * 不经过 Retrofit Service（那些方法签名固定、参数写死），而是用
 * [ApiRegistry] 的元数据**直接构造 OkHttp 请求**。理由：
 *
 *  1. 用户在 UI 上填的参数是运行时才知道的，Retrofit 的静态方法签名接不住；
 *  2. 浏览器要展示的是**原始响应**（状态码 + 头 + 体），Retrofit 会先反序列化，
 *     拿不到原始字节；
 *  3. 未落盘的第二批接口（诗词乐园、消息同步等）也能立刻在浏览器里试打，
 *     不需要先写 Kotlin 定义。
 *
 * 关键：**复用同一套 Interceptor 与 CookieJar**。这里自己 new 一个 OkHttpClient
 * 是错的——那样请求不会带登录 cookie，也不会走 BaseUrlInterceptor 重写 host。
 * 所以从 [ServiceLocator] 已初始化的 RetrofitFactory 里拿 client。
 *
 * 实现细节：`RetrofitFactory` 当前没暴露 client 取值口，这里用一个轻量替代方案——
 * 直接用 [PersistentCookieJar] 构建一个**浏览器专用 client**，Interceptor 链
 * 只保留登录态注入所需的最小集。这与 RetrofitFactory 的 client 共享 CookieJar，
 * 因此登录态一致；差异是浏览器请求不走 `@NeedDecode` 拦截（这正是我们想要的，
 * 要看原始密文）。
 */
class ApiViewModel : ViewModel() {

    /** 浏览器专用 client：带 CookieJar，不带任何会改写响应体的 Interceptor。 */
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .cookieJar(PersistentCookieJar)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /** 搜索关键词。 */
    var keyword by mutableStateOf("")
        private set

    /** 当前选中的接口。null 表示还在列表页。 */
    var selected by mutableStateOf<ApiRegistry.ApiEndpoint?>(null)
        private set

    /** 参数输入值：参数名 → 用户填的字符串。 */
    val paramValues = mutableStateListOf<Pair<String, String>>()

    /** 是否正在请求。 */
    var loading by mutableStateOf(false)
        private set

    /** 最终请求 URL（重写 host 后），展示用。 */
    var finalUrl by mutableStateOf<String?>(null)
        private set

    /** 响应状态码。-1 表示请求未发出或网络失败。 */
    var statusCode by mutableStateOf(-1)
        private set

    /** 响应头（多值展平为 `name: value` 列表）。 */
    val responseHeaders = mutableStateListOf<String>()

    /** 响应体原文（已格式化，超长截断）。 */
    var responseBody by mutableStateOf<String?>(null)
        private set

    /** 耗时毫秒。 */
    var elapsedMs by mutableStateOf(0L)
        private set

    /** 错误信息。null 表示无错误。 */
    var errorMessage by mutableStateOf<String?>(null)
        private set

    /** 搜索结果。 */
    val results: List<ApiRegistry.ApiEndpoint>
        get() = ApiRegistry.search(keyword)

    /** 按分组聚合的搜索结果。 */
    val groupedResults: Map<String, List<ApiRegistry.ApiEndpoint>>
        get() = results.groupBy { it.group }

    fun updateKeyword(next: String) {
        keyword = next
    }

    /** 选中某个接口，按元数据预填参数默认值。 */
    fun select(endpoint: ApiRegistry.ApiEndpoint) {
        selected = endpoint
        paramValues.clear()
        endpoint.params.forEach { p ->
            paramValues.add(p.name to (p.defaultValue ?: ""))
        }
        statusCode = -1
        responseHeaders.clear()
        responseBody = null
        errorMessage = null
        finalUrl = null
        elapsedMs = 0L
    }

    /** 返回列表。 */
    fun back() {
        selected = null
    }

    fun setParam(name: String, value: String) {
        val idx = paramValues.indexOfFirst { it.first == name }
        if (idx >= 0) paramValues[idx] = name to value
    }

    /**
     * 发送请求。
     *
     * 按 [ApiRegistry.ApiParam.location] 决定参数放哪：
     *  - PATH  → 替换路径里的 `{name}`
     *  - QUERY → 拼到 URL query
     *  - FIELD → 表单字段（`application/x-www-form-urlencoded`）
     *  - BODY  → 原始 JSON body
     */
    fun send() {
        val endpoint = selected ?: return
        if (loading) return
        loading = true
        errorMessage = null
        responseHeaders.clear()
        responseBody = null
        statusCode = -1

        viewModelScope.launch {
            val started = System.currentTimeMillis()
            try {
                val base = BaseUrlRegistry.resolve(endpoint.baseUrl)
                    ?: error("域名别名 ${endpoint.baseUrl} 未注册")

                // PATH 替换
                var path = endpoint.path
                val queryPairs = mutableListOf<Pair<String, String>>()
                val formPairs = mutableListOf<Pair<String, String>>()
                var bodyJson: String? = null

                endpoint.params.forEach { p ->
                    val v = paramValues.firstOrNull { it.first == p.name }?.second ?: ""
                    if (v.isBlank() && !p.required) return@forEach
                    when (p.location) {
                        ApiRegistry.ParamIn.PATH -> path = path.replace("{${p.name}}", v)
                        ApiRegistry.ParamIn.QUERY -> queryPairs.add(p.name to v)
                        ApiRegistry.ParamIn.FIELD -> formPairs.add(p.name to v)
                        ApiRegistry.ParamIn.BODY -> bodyJson = v
                        ApiRegistry.ParamIn.HEADER -> Unit
                    }
                }

                val urlBuilder = base.newBuilder()
                    .encodedPath(path)
                queryPairs.forEach { (k, v) -> urlBuilder.addQueryParameter(k, v) }
                val url = urlBuilder.build()
                finalUrl = url.toString()

                val body = when {
                    formPairs.isNotEmpty() -> formPairs
                        .joinToString("&") { (k, v) -> "$k=${java.net.URLEncoder.encode(v, "UTF-8")}" }
                        .toRequestBody("application/x-www-form-urlencoded".toMediaType())
                    bodyJson != null -> bodyJson
                        .toRequestBody("application/json".toMediaType())
                    endpoint.method in listOf("POST", "PUT", "PATCH") ->
                        "".toRequestBody("application/json".toMediaType())
                    else -> null
                }

                val request = Request.Builder()
                    .url(url)
                    .method(endpoint.method, body)
                    .header("Accept", "application/json")
                    .build()

                val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }
                elapsedMs = System.currentTimeMillis() - started
                statusCode = response.code
                response.headers.forEach { (n, v) -> responseHeaders.add("$n: $v") }
                val raw = withContext(Dispatchers.IO) { response.body.string() }
                responseBody = raw.take(200_000)
            } catch (t: Throwable) {
                elapsedMs = System.currentTimeMillis() - started
                statusCode = -1
                errorMessage = "${t.javaClass.simpleName}: ${t.message}"
            } finally {
                loading = false
            }
        }
    }
}
