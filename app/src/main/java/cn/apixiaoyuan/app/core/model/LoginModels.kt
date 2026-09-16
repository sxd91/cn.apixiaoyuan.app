package cn.apixiaoyuan.app.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 登录响应信封。
 *
 * 与 [cn.apixiaoyuan.app.core.network.Envelope] 不是一套体系：
 * [Envelope] 的 `code == 0` 是成功，登录响应的 `code == 1` 才是成功。
 * 服务端这两套并存，不要互相套用。
 *
 * 四个业务码逐行来自 `LoginResponse.smali`：
 *  - `1`  [LOGIN_SUCCESS]      成功
 *  - `2`  [LOGIN_NEED_AUTH]    需要认证（短信二次验证）
 *  - `3`  [LOGIN_BANNED]       账号封禁
 *  - `10` [LOGIN_PHONE_NOT_MATCH] 手机号不匹配
 */
@Serializable
data class LoginResponse(
    @SerialName("code") val code: Int,
    @SerialName("body") val body: LoginResponseBody? = null,
) {
    val isSuccess: Boolean get() = code == LOGIN_SUCCESS
    val needAuth: Boolean get() = code == LOGIN_NEED_AUTH
    val isBanned: Boolean get() = code == LOGIN_BANNED
    val phoneNotMatch: Boolean get() = code == LOGIN_PHONE_NOT_MATCH

    companion object {
        const val LOGIN_SUCCESS = 1
        const val LOGIN_NEED_AUTH = 2
        const val LOGIN_BANNED = 3
        const val LOGIN_PHONE_NOT_MATCH = 10
    }
}

/**
 * 登录成功后的用户载荷。
 *
 * 构造函数签名（`LoginResponseBody.smali` 逐行）：
 * `(Ljava/lang/String;IIILjava/lang/Integer;L.../YtkUserSchoolInfo;L.../UserVO;)V`
 *
 * **没有 token 字段** —— token 可能藏在响应头，或由 `ytkUserId` + 设备指纹本地生成。
 */
@Serializable
data class LoginResponseBody(
    @SerialName("encryptedPhone") val encryptedPhone: String,
    @SerialName("ytkUserId") val ytkUserId: Int,
    @SerialName("primaryUserId") val primaryUserId: Int,
    @SerialName("rewardPoints") val rewardPoints: Int,
    @SerialName("rewardPetFoods") val rewardPetFoods: Int? = null,
    @SerialName("ytkUserInfo") val ytkUserInfo: YtkUserSchoolInfo,
    @SerialName("leoUserInfo") val leoUserInfo: UserVO,
)

/**
 * 旧版登录返回（`YtkApiService.passwordLoginCall` / `smsLogin`）。
 *
 * 字段来自 `UserAccount.smali`：
 * `createdTime:J`、`email:String?`、`id:I`、`identity:String?`、
 * `passwordExist:Z`、`phone:String?`、`primaryUserId:I`
 */
@Serializable
data class UserAccount(
    @SerialName("id") val id: Int,
    @SerialName("primaryUserId") val primaryUserId: Int,
    @SerialName("phone") val phone: String? = null,
    @SerialName("email") val email: String? = null,
    @SerialName("identity") val identity: String? = null,
    @SerialName("passwordExist") val passwordExist: Boolean = false,
    @SerialName("createdTime") val createdTime: Long = 0L,
)
