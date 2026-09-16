package cn.apixiaoyuan.app.core.network

import kotlinx.serialization.Serializable

/**
 * 统一响应包装。
 *
 * 服务端返回的结构是 `{ code, msg, data }` 形态（原版 `@NotNullAndValid`
 * 校验的正是这层）。[ApiResult] 把这层拆成成功/失败两态，业务层只处理
 * [Success.data] 与 [Failure.exception]，不直接碰 code 判断。
 */
sealed interface ApiResult<out T> {

    data class Success<T>(val data: T) : ApiResult<T>

    data class Failure(
        val exception: ApiException,
    ) : ApiResult<Nothing>

    companion object {
        inline fun <T> catching(block: () -> T): ApiResult<T> =
            try {
                Success(block())
            } catch (e: ApiException) {
                Failure(e)
            } catch (e: Throwable) {
                Failure(ApiException.Unknown(e))
            }
    }
}

/** 网络层错误分类。业务层按类型决定重试、跳登录、还是提示。 */
sealed class ApiException(message: String) : Exception(message) {

    /** 服务端返回了业务错误码。 */
    class Business(val code: Int, val serverMsg: String) :
        ApiException("business error $code: $serverMsg")

    /** HTTP 非 2xx。 */
    class Http(val status: Int, val body: String?) :
        ApiException("http $status")

    /** 连接/读写/超时等传输层失败。 */
    class Network(cause: Throwable) :
        ApiException("network: ${cause.message}")

    /** 反序列化失败。 */
    class Parse(cause: Throwable) :
        ApiException("parse: ${cause.message}")

    /** 登录态失效，需要重新登录。 */
    class Unauthorized :
        ApiException("unauthorized")

    /** 未分类。 */
    class Unknown(cause: Throwable) :
        ApiException("unknown: ${cause.message}")

    val isRetryable: Boolean
        get() = this is Network
}

/** 业务成功码。原版判定以 0 为成功；若实测不是 0，只改这里。 */
const val CODE_SUCCESS = 0

/** 登录态失效码。 */
const val CODE_UNAUTHORIZED = 401

/** 服务端统一信封。字段名以实测为准，这里按常见形态落盘。 */
@Serializable
data class Envelope<T>(
    val code: Int = CODE_SUCCESS,
    val msg: String = "",
    val data: T? = null,
)

/** 把信封拆成 ApiResult；data 为 null 时视为业务失败。 */
fun <T> Envelope<T>.toResult(): ApiResult<T> = when {
    code == CODE_UNAUTHORIZED -> ApiResult.Failure(ApiException.Unauthorized())
    code != CODE_SUCCESS -> ApiResult.Failure(ApiException.Business(code, msg))
    data == null -> ApiResult.Failure(ApiException.Business(code, "empty data"))
    else -> ApiResult.Success(data)
}
