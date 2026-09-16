package cn.apixiaoyuan.app.core.network

import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import retrofit2.Invocation

/**
 * 标记 Retrofit 方法使用哪套 BaseUrl。
 *
 * Retrofit 只支持单一 baseUrl；小猿口算的网络层有主域（leo_base_url）与账号域
 * （ytk_base_url）两套，且共用同一个 OkHttpClient。这里把「域名选择」从构建期
 * 移到调用期：注解挂在 Service 方法上，[BaseUrlInterceptor] 在请求发出前通过
 * [Invocation] 读出注解值，重写 scheme/host/port。
 *
 * 用法：
 * ```
 * @BaseUrl(BASE_LEO)
 * @GET("/leo-math/android/exams/v5/exercises")
 * suspend fun getMathExercisesTabV6(): Envelope<...>
 * ```
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER)
@Retention(AnnotationRetention.RUNTIME)
annotation class BaseUrl(val value: String)

/** 主域：练习 / PK / 中文 / 英文 / 试卷 / 用户资料。 */
const val BASE_LEO = "leo"

/** 账号域：登录 / 账号 / 子账号。 */
const val BASE_YTK = "ytk"

/**
 * 域名注册表。真实 host 在 [RetrofitFactory] 初始化时注入，这里只存
 * 别名 → HttpUrl 的映射，避免把域名硬编码进注解。
 */
object BaseUrlRegistry {

    private val hosts = mutableMapOf<String, HttpUrl>()

    /** 由 RetrofitFactory 在初始化时调用；重复注册覆盖（便于运行时切换环境）。 */
    fun register(alias: String, url: HttpUrl) {
        hosts[alias] = url
    }

    /** 未注册的别名回退到第一套，避免请求直接崩在 host 缺失上。 */
    fun resolve(alias: String): HttpUrl? = hosts[alias] ?: hosts.values.firstOrNull()

    fun isRegistered(alias: String): Boolean = hosts.containsKey(alias)

    fun clear() = hosts.clear()
}

/**
 * 按方法注解重写请求域名。
 *
 * 在 Interceptor 链里必须位于鉴权之前——鉴权需要知道最终 host 才能决定
 * 是否注入 `YFD_U`（只有账号域才带）。
 */
class BaseUrlInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        // Invocation 由 Retrofit 在构建请求时写入 tag；非 Retrofit 发起的请求没有。
        val invocation = request.tag(Invocation::class.java) ?: return chain.proceed(request)
        val annotation = invocation.method().getAnnotation(BaseUrl::class.java)
            ?: return chain.proceed(request)

        val target = BaseUrlRegistry.resolve(annotation.value)
            ?: return chain.proceed(request)

        val old = request.url
        val rewritten = old.newBuilder()
            .scheme(target.scheme)
            .host(target.host)
            .port(target.port)
            .build()

        return chain.proceed(
            request.newBuilder()
                .url(rewritten)
                .build(),
        )
    }
}
