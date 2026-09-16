package cn.apixiaoyuan.app.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 主域用户信息（`leoUserInfo`）。
 *
 * 十四个字段全部逐行来自 `smali_classes7/.../user/data/UserVO.smali`。
 * 角色常量：`ROLE_STUDENT=0`、`ROLE_PARENT=1`、`ROLE_TEACHER=2`。
 */
@Serializable
data class UserVO(
    @SerialName("userId") val userId: Int,
    @SerialName("primaryUserId") val primaryUserId: Int,
    @SerialName("nickname") val nickname: String? = null,
    @SerialName("defaultNickname") val defaultNickname: String? = null,
    @SerialName("avatarId") val avatarId: String? = null,
    @SerialName("avatarUrl") val avatarUrl: String? = null,
    @SerialName("avatarPendantId") val avatarPendantId: Int = 0,
    @SerialName("avatarPendantUrl") val avatarPendantUrl: String? = null,
    @SerialName("role") val role: Int = ROLE_STUDENT,
    @SerialName("grade") val grade: Int = 0,
    @SerialName("gradeTrusted") val gradeTrusted: Boolean = false,
    @SerialName("gradeUpdatedTime") val gradeUpdatedTime: Long = 0L,
    @SerialName("nicknameUpdatedTime") val nicknameUpdatedTime: Long = 0L,
    @SerialName("hasBindWxSrv") val hasBindWxSrv: Boolean = false,
) {
    companion object {
        const val ROLE_STUDENT = 0
        const val ROLE_PARENT = 1
        const val ROLE_TEACHER = 2
    }
}

/**
 * 学段信息（`ytkUserInfo`）。
 *
 * 四个字段来自 `YtkUserSchoolInfo.smali`：`chuzhongInfo`/`daxueInfo`/
 * `gaozhongInfo`/`xiaoxueInfo`，各自是 [UserPhaseInfo]。
 */
@Serializable
data class YtkUserSchoolInfo(
    @SerialName("xiaoxueInfo") val xiaoxueInfo: UserPhaseInfo? = null,
    @SerialName("chuzhongInfo") val chuzhongInfo: UserPhaseInfo? = null,
    @SerialName("gaozhongInfo") val gaozhongInfo: UserPhaseInfo? = null,
    @SerialName("daxueInfo") val daxueInfo: UserPhaseInfo? = null,
)

/**
 * 单个学段的学校列表。
 *
 * ⚠️ `school` 的元素类型未确证 —— `UserPhaseInfo.smali` 里只看到
 * `List` 原始签名，泛型被擦除。**先用 `List<String>` 占位**，
 * 真机验证后若不符再改。这是本次落盘里唯一一处「字段类型靠推断」。
 */
@Serializable
data class UserPhaseInfo(
    @SerialName("school") val school: List<String> = emptyList(),
)

/**
 * 账号域当前用户信息（`YtkAccountService.getCurrentUserInfo`）。
 *
 * 四个字段来自 `CurrentUserInfo.smali`：`createdTime`/`id`/`passwordExist`/`phone`。
 * 与 [LoginResponseBody.ytkUserId] 同量纲 —— `YFD_U` 应当取这里的 `id`。
 */
@Serializable
data class CurrentUserInfo(
    @SerialName("id") val id: Int,
    @SerialName("phone") val phone: String? = null,
    @SerialName("passwordExist") val passwordExist: Boolean = false,
    @SerialName("createdTime") val createdTime: Long = 0L,
)

/**
 * 用户设置（`LeoProfileApiService.getUserSetting`）。
 *
 * ⚠️ `UserSetting.smali` 只挖到**一个字段** `autoCheckEBook: Int`。
 * 这个类可能有更多字段走非标准序列化（注解式声明），
 * **先按单字段落盘**，真机抓到完整响应后再补。
 */
@Serializable
data class UserSetting(
    @SerialName("autoCheckEBook") val autoCheckEBook: Int = 0,
)
