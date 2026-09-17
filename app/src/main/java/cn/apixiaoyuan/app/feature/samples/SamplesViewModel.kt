package cn.apixiaoyuan.app.feature.samples

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.apixiaoyuan.app.core.database.AppDatabase
import cn.apixiaoyuan.app.core.database.Sample
import cn.apixiaoyuan.app.core.network.EncodeBridge
import cn.apixiaoyuan.app.core.network.DecodeBridge
import cn.apixiaoyuan.app.core.samples.SampleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * 单条重放的结果。
 *
 * 只保留展示需要的字段 —— 不做完整响应建模，样本库页面只需要
 * 「成功没成功、状态码多少、回来了什么、耗时多久」。
 */
data class ReplayResult(
    val sampleId: Long,
    val success: Boolean,
    val statusCode: Int,
    val body: String,
    val durationMs: Long,
    val error: String?,
)

/**
 * 样本库状态机。
 *
 * 三块状态：
 *  - [samples]：样本列表，从 Room 的 Flow 收集，数据库一变页面就变
 *  - [loading]：首次加载中
 *  - [lastReplay]：最近一次重放结果，用于结果面板
 *
 * 重放走「直构 OkHttp」而不是 Retrofit：样本的 URL 是完整动态 URL
 * （含 host 与查询串），Retrofit 的静态签名接不住运行时才确定的 URL。
 * 这与 `ReplViewModel` 是同一条路径，两者的取舍理由一致。
 *
 * 重放时会按样本的 [Sample.needEncode] / [Sample.needDecode] 显式过一遍
 * `EncodeBridge` / `DecodeBridge` —— 样本库里记录的请求可能是编码后的形态，
 * 而服务器要的是编码后的字节；解码方向同理。
 */
class SamplesViewModel(
    private val repo: SampleRepository = SampleRepository(AppDatabase.get()),
) : ViewModel() {

    /** 样本列表，倒序。 */
    val samples = mutableStateListOf<Sample>()

    /** 首次加载中。列表有内容后置 false。 */
    var loading by mutableStateOf(true)
        private set

    /** 最近一次重放结果；未重放为 null。 */
    var lastReplay by mutableStateOf<ReplayResult?>(null)
        private set

    /** 当前正在重放的样本 id；无进行中为 null。 */
    var replayingId by mutableStateOf<Long?>(null)
        private set

    init {
        viewModelScope.launch {
            repo.observeAll().collectLatest { list ->
                samples.clear()
                samples.addAll(list)
                loading = false
            }
        }
    }

    /** 删除一条样本。 */
    fun delete(id: Long) {
        viewModelScope.launch { repo.delete(id) }
    }

    /**
     * 重放一条样本。
     *
     * 流程：读最新快照 -> 需要则编码请求体 -> 发出 -> 需要则解码响应体 ->
     * 回写重放记录 -> 展示结果。任一步失败都不抛异常，落进 [ReplayResult.error]。
     */
    fun replay(id: Long) {
        if (replayingId != null) return
        replayingId = id
        lastReplay = null

        viewModelScope.launch {
            val sample = repo.findById(id)
            if (sample == null) {
                lastReplay = ReplayResult(
                    sampleId = id,
                    success = false,
                    statusCode = -1,
                    body = "",
                    durationMs = 0,
                    error = "样本不存在（可能已被删除）",
                )
                replayingId = null
                return@launch
            }

            val startedAt = System.currentTimeMillis()
            val result = runCatching { execute(sample) }
            val elapsed = System.currentTimeMillis() - startedAt

            val replay = result.getOrElse { t ->
                ReplayResult(
                    sampleId = id,
                    success = false,
                    statusCode = -1,
                    body = "",
                    durationMs = elapsed,
                    error = "${t.javaClass.simpleName}: ${t.message ?: "无消息"}",
                )
            }

            lastReplay = replay
            replayingId = null
            repo.markReplayed(id, System.currentTimeMillis(), replay.success)
        }
    }

    /**
     * 真正发出请求。
     *
     * 在 IO 线程执行，不阻塞主线程。超时 30 秒，与网络底座一致。
     */
    private suspend fun execute(sample: Sample): ReplayResult = withContext(Dispatchers.IO) {
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        val builder = Request.Builder().url(sample.url)

        // 请求头：样本存的是 JSON 字符串，这里逐行解析。
        // 空值或解析失败都不阻断请求 —— 缺头总比整个重放失败好。
        sample.headers?.lines()?.forEach { line ->
            val idx = line.indexOf(':')
            if (idx > 0) {
                val name = line.substring(0, idx).trim()
                val value = line.substring(idx + 1).trim()
                if (name.isNotEmpty()) builder.header(name, value)
            }
        }

        val hasBody = sample.method.uppercase() != "GET" &&
            sample.method.uppercase() != "HEAD"

        val bodyText = sample.requestBody
        if (bodyText != null && hasBody) {
            val raw = bodyText.toByteArray(Charsets.UTF_8)
            // @NeedEncode：请求体在发出前过 native 编码。
            // 样本里的 requestBody 存的是编码前明文，这里编码后发出。
            val outBytes = if (sample.needEncode) EncodeBridge.encode(raw) else raw
            val mediaType = "application/json; charset=utf-8".toMediaType()
            builder.method(sample.method, outBytes.toRequestBody(mediaType))
        } else {
            builder.method(sample.method, null)
        }

        val startedAt = System.currentTimeMillis()
        client.newCall(builder.build()).execute().use { response ->
            val rawBytes = response.body.bytes()
            // @NeedDecode：响应体在解析前过 native 解码。
            val outBytes = if (sample.needDecode) DecodeBridge.decode(rawBytes) else rawBytes
            val decodedText = outBytes.toString(Charsets.UTF_8)
            ReplayResult(
                sampleId = sample.id,
                success = response.isSuccessful,
                statusCode = response.code,
                body = decodedText,
                durationMs = System.currentTimeMillis() - startedAt,
                error = if (response.isSuccessful) null else "HTTP ${response.code}",
            )
        }
    }
}
