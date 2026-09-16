package cn.apixiaoyuan.app.core.network

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response
import okio.Buffer

/**
 * 请求/响应全量日志。
 *
 * 对齐原版 Packet 抓包的语义：可开关、可看完整 body。区别是原版写文件
 * （`externalCacheDir/packet_capture.log`），这里先输出到 logcat，
 * 落文件与样本回放交给 `feature/samples`（DEV-PLAN 模块 13）。
 *
 * 注意：打印 body 会消费流，所以必须用 [Buffer] 缓存后重建。
 * 只在 [enabled] 为 true 时执行，release 构建默认关闭。
 */
class LoggingInterceptor(
    private val enabled: Boolean = false,
    private val tag: String = "LeoNet",
    private val maxBodyChars: Int = 16 * 1024,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        if (!enabled) return chain.proceed(chain.request())

        val request = chain.request()
        val startNs = System.nanoTime()

        Log.d(tag, "--> ${request.method} ${request.url}")
        request.headers.forEach { (name, value) -> Log.d(tag, "    $name: $value") }
        request.body?.let { body ->
            val buffer = Buffer()
            runCatching { body.writeTo(buffer) }
                .onSuccess { Log.d(tag, "    body=${buffer.readUtf8().truncate()}") }
        }

        val response = chain.proceed(request)
        val tookMs = (System.nanoTime() - startNs) / 1_000_000

        Log.d(tag, "<-- ${response.code} ${request.url} (${tookMs}ms)")
        val text: String = runCatching {
            response.peekBody((maxBodyChars + 1).toLong()).string()
        }.getOrDefault("<unreadable>")
        Log.d(tag, "    body=${text.truncate()}")

        return response
    }

    private fun String.truncate(): String =
        if (length <= maxBodyChars) this else substring(0, maxBodyChars) + "…(+${length - maxBodyChars})"
}
