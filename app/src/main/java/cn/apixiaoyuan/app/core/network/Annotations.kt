package cn.apixiaoyuan.app.core.network

/**
 * 响应体无校验注解。
 *
 * 原版 `com.yuanfudao.android.leo.network` 里有一整套响应校验注解，
 * 挂在 Retrofit 方法上，由 converter 或 interceptor 读取后决定
 * 是否对返回体做非空 / 有效性断言。`@CheckNothing` 表示「跳过校验，
 * 原样返回」—— 登录/登出这类接口返回结构多变，不能用统一规则卡。
 *
 * 本工程当前只做标记，不做运行时行为；校验逻辑在 converter 层
 * 接进来之后才有实际作用。这样落盘是为了让 Service 签名与原版对齐，
 * 避免后续接 converter 时再回改所有 Service。
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER)
@Retention(AnnotationRetention.RUNTIME)
annotation class CheckNothing

/**
 * 强制走 Gson 转换器。
 *
 * 本工程的 Retrofit 默认装了 kotlinx.serialization converter，
 * 但原版有相当一部分接口返回结构用 `@SerializedName` 标注、
 * 依赖 Gson 的宽松解析。这个注解用于把特定方法切到 Gson converter 上，
 * 由 converter 层按注解分派。
 *
 * 当前与 [CheckNothing] 一样只做标记 —— 双 converter 分派是独立工作项，
 * 落注解是为了 Service 定义一次成型。
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER)
@Retention(AnnotationRetention.RUNTIME)
annotation class GsonConverter

/**
 * 非空且有效注解。
 *
 * 用于旧版 [cn.apixiaoyuan.app.core.network.api.YtkApiService] 的 `Call`
 * 返回 —— 旧版链路是同步 `Call`，调用方直接 `execute()` 取 `body()`，
 * 没有 `Envelope` 包裹，空体即错误。这个注解让 converter 在 body 为 null
 * 或校验失败时抛 [cn.apixiaoyuan.app.core.network.ApiException]。
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER)
@Retention(AnnotationRetention.RUNTIME)
annotation class NotNullAndValid
