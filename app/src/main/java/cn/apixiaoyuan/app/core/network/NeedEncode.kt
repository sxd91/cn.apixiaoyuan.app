package cn.apixiaoyuan.app.core.network

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

        val newBody = okhttp3.RequestBody.create(
            body.contentType(),
            encoded,
        )
        val newRequest = request.newBuilder().method(request.method, newBody).build()
        return chain.proceed(newRequest)
    }
}