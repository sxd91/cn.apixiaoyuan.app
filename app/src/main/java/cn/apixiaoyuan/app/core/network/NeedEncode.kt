package cn.apixiaoyuan.app.core.network

import okhttp3.MediaType.Companion.toMediaType

/**
 * 标记该接口的请求体在发出前需要 native 层编码（`libContentEncoder.so`）。
 *
 * 与 [NeedDecode] 方向相反：`@NeedDecode` 处理响应，本注解处理请求。
 *
 * 已确证需编码的接口：
 *  - `uploadExamResult`（`PUT /leo-math/android/exams/v2/{examId}`，练习成绩上传）
 *  - `postSavedExp`（`POST /leo-star/android/exercise/rank/login/attend`）
 *
 * 编码链路（原版逐行读出）：请求体字节 → gzip 压缩 → `libContentEncoder.so`
 * 的 `c()`。与响应解码链路互逆，共用同一个 so。真实实现见
 * `core/native/NativeEncodeInstaller.kt`。
 *
 * 拦截逻辑见 [NeedEncodeInterceptor]。编码器出口为 [EncodeBridge]。
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER)
@Retention(AnnotationRetention.RUNTIME)
annotation class NeedEncode

/**
 * 编码出口。native 库未接入时用 [IdentityEncoder]，接入后替换为 JNI 实现。
 *
 * 与 [PayloadDecoder] 对称：把 native 依赖抽象成接口，让「网络通、编码待接」
 * 这个中间态可验证。
 */
fun interface PayloadEncoder {
    /** @param raw 待编码的明文 JSON 字节；@return 编码后的字节。 */
    fun encode(raw: ByteArray): ByteArray
}

/** 恒等编码器：直接透传。用于 native 未接入阶段。 */
object IdentityEncoder : PayloadEncoder {
    override fun encode(raw: ByteArray): ByteArray = raw
}

/**
 * 全局编码器持有者。native 接入时由 `core/native/` 调用 [install] 替换。
 */
object EncodeBridge {

    @Volatile
    private var encoder: PayloadEncoder = IdentityEncoder

    @Volatile
    var nativeAvailable: Boolean = false
        private set

    fun install(encoder: PayloadEncoder, fromNative: Boolean = true) {
        this.encoder = encoder
        this.nativeAvailable = fromNative
    }

    fun encode(raw: ByteArray): ByteArray = encoder.encode(raw)

    /** 测试或降级时复位。 */
    fun reset() {
        encoder = IdentityEncoder
        nativeAvailable = false
    }
}

/**
 * 按方法注解决定是否编码请求体。
 *
 * 实现要点：RequestBody 是流式的，编码必须把整个 body 读成字节、编码、
 * 再塞回一个新 RequestBody。这会失去流式特性——对 JSON 接口无所谓，
 * 对上传接口是灾难，因此只对标注了 [NeedEncode] 的请求做这件事。
 *
 * ## Content-Type 必须改成 `application/octet-stream`（2026-09-25 对齐原版）
 *
 * 原版 `wp/h.b(Converter, Object)` 逐行实现是：
 * ```
 * new Buffer() → converter.convert(obj) → RequestBody.writeTo(buffer)
 *   → buffer.readByteArray() → ds/i4.c([B)          // gzip + libContentEncoder
 *   → RequestBody.create(MediaType.parse("application/octet-stream"), bytes)
 * ```
 *
 * 注意最后一步 —— 原版**无条件把 Content-Type 改写成
 * `application/octet-stream`**，不是沿用原来的 `application/json`。
 * 此前本项目用 `body.contentType()`（保留原类型），与协议不符：
 * 服务端的 `solar-encoder` 中间件据此判断「body 是否已编码」，
 * 沿用 JSON 类型会让它认为收到的仍是明文而拒绝（实测 417
 * `x-block-by: solar-encoder`）。
 */
class NeedEncodeInterceptor : okhttp3.Interceptor {

    override fun intercept(chain: okhttp3.Interceptor.Chain): okhttp3.Response {
        val request = chain.request()

        val invocation = request.tag(retrofit2.Invocation::class.java)
            ?: return chain.proceed(request)
        if (invocation.method().getAnnotation(NeedEncode::class.java) == null) {
            return chain.proceed(request)
        }

        val body = request.body ?: return chain.proceed(request)
        val raw = okio.Buffer().also { body.writeTo(it) }.readByteArray()
        val encoded = EncodeBridge.encode(raw)

        // 与 `wp/h.b` 一致：编码后的 body 一律声明为 octet-stream。
        // 二进制编码结果本来就不是 JSON，用 JSON 的 Content-Type 描述它是错的。
        val newRequest = request.newBuilder()
            .method(
                request.method,
                okhttp3.RequestBody.create(OCTET_STREAM, encoded),
            )
            .build()
        return chain.proceed(newRequest)
    }

    private companion object {
        /** 与 `wp/h.b` 末尾的 `MediaType.parse("application/octet-stream")` 同值。 */
        val OCTET_STREAM: okhttp3.MediaType = "application/octet-stream".toMediaType()
    }
}