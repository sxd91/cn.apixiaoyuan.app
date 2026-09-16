package cn.apixiaoyuan.app.core.network

/**
 * 标记该接口的响应体需要 native 层解码（`libRequestEncoder.so` /
 * `libContentEncoder.so` / `libRedressProcess.so`）。
 *
 * 纯 Gson 解析这类响应会拿到密文。拦截逻辑见 [NeedDecodeInterceptor]。
 * 已确证需解码的接口：
 *  - `getChineseKnowledgeUsageExamInfo`（知识点运用题目）
 *  - `getChineseKnowledgeUsageExamResult`（知识点运用结果）
 *  - `getPaperExerciseDetailData`（试卷详情）
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER)
@Retention(AnnotationRetention.RUNTIME)
annotation class NeedDecode

/**
 * 解码出口。native 库未接入时用 [IdentityDecoder]，接入后替换为 JNI 实现。
 *
 * 之所以抽象成接口而不是直接调 JNI：native 复刻是独立里程碑（DEV-PLAN 的 M5），
 * 上层不应被它阻塞。恒等实现让「网络通、解析通、解码待接」这个中间态可验证。
 */
fun interface PayloadDecoder {
    /** @param raw 服务端返回的原始字节；@return 解码后的明文 JSON 字节。 */
    fun decode(raw: ByteArray): ByteArray
}

/** 恒等解码器：直接透传。用于 native 未接入阶段。 */
object IdentityDecoder : PayloadDecoder {
    override fun decode(raw: ByteArray): ByteArray = raw
}

/**
 * 全局解码器持有者。native 接入时由 `core/native/` 调用 [install] 替换。
 */
object DecodeBridge {

    @Volatile
    private var decoder: PayloadDecoder = IdentityDecoder

    @Volatile
    var nativeAvailable: Boolean = false
        private set

    fun install(decoder: PayloadDecoder, fromNative: Boolean = true) {
        this.decoder = decoder
        this.nativeAvailable = fromNative
    }

    fun decode(raw: ByteArray): ByteArray = decoder.decode(raw)

    /** 测试或降级时复位。 */
    fun reset() {
        decoder = IdentityDecoder
        nativeAvailable = false
    }
}

/**
 * 按方法注解决定是否解码响应体。
 *
 * 实现要点：Response 的 body 是一次性的，解码必须发生在 Gson 消费之前，
 * 所以这里把整个 body 读成字节、解码、再塞回一个新 ResponseBody。
 * 这会让响应失去流式特性——对 JSON 接口无所谓，对下载接口是灾难，
 * 因此只对标注了 [NeedDecode] 的请求做这件事。
 */
class NeedDecodeInterceptor : okhttp3.Interceptor {

    override fun intercept(chain: okhttp3.Interceptor.Chain): okhttp3.Response {
        val request = chain.request()
        val response = chain.proceed(request)

        val invocation = request.tag(retrofit2.Invocation::class.java)
            ?: return response
        if (invocation.method().getAnnotation(NeedDecode::class.java) == null) {
            return response
        }

        val body = response.body ?: return response
        val raw = body.bytes()
        val decoded = DecodeBridge.decode(raw)

        val newBody = okhttp3.ResponseBody.create(
            body.contentType(),
            decoded,
        )
        return response.newBuilder().body(newBody).build()
    }
}
