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
    @SerialName("encryptedPhone") val encryptedPhone: String = "",
    @SerialName("ytkUserId") val ytkUserId: Int = 0,
    /**
     * ⚠️ **字段名待实测确证，勿照搬直连版结论。**
     *
     * 2026-09-25 实测确证：**直连版** `POST /accounts/android/safe/login`
     * 的响应体是平铺账号对象、无 `code`/`body` 信封，其主账号字段名为
     * `primarySubUserId`，**从不返回 `primaryUserId`**（详见 [UserAccount]）。
     *
     * 本类服务的是**网关版** `/leo-gateway/android/auth/{sms,password}`，
     * 该通道尚未实测，响应结构未知 —— 因此这里**不擅自改名**，
     * 只把必填字段降为带默认值，对齐原版 Gson 宽松解析口径
     * （缺字段 → `0` / `""`，而非抛 `MissingFieldException`）。
     * 接入网关版前请先抓一次真实响应体确证字段名。
     */
    @SerialName("primaryUserId") val primaryUserId: Int = 0,
    @SerialName("rewardPoints") val rewardPoints: Int = 0,
    @SerialName("rewardPetFoods") val rewardPetFoods: Int? = null,
    @SerialName("ytkUserInfo") val ytkUserInfo: YtkUserSchoolInfo? = null,
    @SerialName("leoUserInfo") val leoUserInfo: UserVO? = null,
)

/**
 * 直连版登录返回（`YtkApiService.passwordLoginCall` / `smsLogin`）。
 *
 * ## 字段口径（2026-09-25 实测确证，非 smali 推断）
 *
 * 实测：`POST https://ape-api.yuanfudao.com/accounts/android/safe/login`
 * （form-urlencoded，`phone` 与 `verification` **均为 RSA 密文**，
 * `autoRegister=true`）→ HTTP 200，下发 `sess` / `userid=511467407`，
 * 响应体是**平铺账号对象，没有 `code` / `body` 信封**：
 * ```json
 * {"id":511467407,"email":null,"phone":"18723143414",
 *  "createdTime":1610787674487,"identity":"18723143414",
 *  "countryRegion":{...},"originUserId":511467407,"dtrUser":false,
 *  "subDeregisterInfos":{...},"trial":false,"sonSubUser":false,
 *  "primarySubUserId":511467407,"subUserInfos":{...},
 *  "loginIntercept":false,"passwordExist":true}
 * ```
 *
 * ## 为什么字段名是 `primarySubUserId` 而不是 `primaryUserId`
 *
 * 服务端**从不返回 `primaryUserId`**，等价语义的字段叫 `primarySubUserId`。
 * 之前按 `UserAccount.smali` 的 getter 名（`getPrimaryUserId`）反推字段名，
 * 把 getter 名当成了 JSON key —— 这是本次真机
 * `Field 'primaryUserId' is required ... but it was missing` 的直接原因。
 *
 * ## 为什么全部字段都给默认值
 *
 * 靶场 `UserAccount.smali` 的 Kotlin 元数据 `d2` 暴露了真实构造签名
 * `(IILjava/lang/String;Ljava/lang/String;JLjava/lang/String;Z)V`
 * （= `id, primaryUserId, email, phone, createdTime, identity, passwordExist`），
 * 且存在 `mask = 0x7f`（7 位全置）的合成构造 → **原版 7 个字段全部有默认值**；
 * 该 smali 内也**没有 `$serializer` / `Companion`**，说明原版走 Gson
 * 宽松反射解析（缺字段 → `0` / `null`），**结构不符时永不抛异常**。
 *
 * 本项目 `RetrofitFactory` 只装了 kotlinx.serialization converter
 * （`@GsonConverter` 仅作标记，无运行时行为），kotlinx 对**无默认值的
 * 非空字段**在键缺失时直接抛 `MissingFieldException`。因此这里必须
 * 逐字段给默认值，才能复现原版「结构漂移不致命」的容错口径。
 */
@Serializable
data class UserAccount(
    /** 账号 ID，实测恒存在（= `userid` cookie）。给默认值仅为对齐原版容错口径。 */
    @SerialName("id") val id: Int = 0,
    /**
     * 主账号 ID（服务端真实字段名）。
     *
     * 实测值与 [id] 相同（均为 `511467407`）。**注意它不是 `primaryUserId`。**
     */
    @SerialName("primarySubUserId") val primarySubUserId: Int = 0,
    @SerialName("originUserId") val originUserId: Int = 0,
    @SerialName("phone") val phone: String? = null,
    @SerialName("email") val email: String? = null,
    @SerialName("identity") val identity: String? = null,
    @SerialName("passwordExist") val passwordExist: Boolean = false,
    @SerialName("createdTime") val createdTime: Long = 0L,
    @SerialName("loginIntercept") val loginIntercept: Boolean = false,
    /**
     * 子账号（宝贝学习账号）归属信息。
     *
     * 实测响应（2026-09-25）：
     * ```json
     * {"project2SubUserInfo":{"6":{"projectId":6,
     *   "primarySubUserId":511467407,
     *   "subUserIds":[511467407,1066052990,1151665130]}}}
     * ```
     *
     * `subUserIds` 的**第一个元素是主账号自己**，其余才是子账号 —— 这是
     * 「切换宝贝学习账号」列表的唯一数据源（服务端没有单独的列表接口，
     * 只有 `batchGet` 批量换资料）。key `"6"` 是小猿口算所属业务线。
     */
    @SerialName("subUserInfos") val subUserInfos: SubUserInfos? = null,
)

/** `UserAccount.subUserInfos` 的载荷。 */
@Serializable
data class SubUserInfos(
    @SerialName("project2SubUserInfo") val project2SubUserInfo: Map<String, ProjectSubUserInfo> = emptyMap(),
)

/** 单条业务线的子账号归属。 */
@Serializable
data class ProjectSubUserInfo(
    @SerialName("projectId") val projectId: Int = 0,
    @SerialName("primarySubUserId") val primarySubUserId: Int = 0,
    @SerialName("subUserIds") val subUserIds: List<Int> = emptyList(),
)
