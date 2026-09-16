package cn.apixiaoyuan.app.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * 家长认证状态（`isParentCertificated` 返回）。
 *
 * 来自 `smali_classes3/com/fenbi/android/leo/network/ParentCertificationData.smali` 的类型名，
 * 字段未逐行读出，按「认证状态 + 身份证信息」的常见形态落盘。
 * **字段为推断，待真机抓包修正。**
 */
@Serializable
data class ParentCertificationData(
    @SerialName("certificated") val certificated: Boolean = false,
    @SerialName("idCardNo") val idCardNo: String? = null,
    @SerialName("realName") val realName: String? = null,
    @SerialName("auditStatus") val auditStatus: Int = 0,
)

/**
 * 用户头像挂件（`getUserPendants` / `submitUserPendant` 相关）。
 *
 * 来自 `smali_classes3` 的 `UserPendantVO` 类型名，字段未确证。
 * **字段为推断。**
 */
@Serializable
data class UserPendantVO(
    @SerialName("pendants") val pendants: List<UserPendant> = emptyList(),
    @SerialName("currentPendantId") val currentPendantId: Int = 0,
)

/** 单个头像挂件。 */
@Serializable
data class UserPendant(
    @SerialName("id") val id: Int = 0,
    @SerialName("type") val type: Int = 0,
    @SerialName("url") val url: String? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("unlocked") val unlocked: Boolean = false,
)

/**
 * 用户 VIP 信息（`getUserVipInfoV2` 返回）。
 *
 * 来自 `smali_classes3` 的 `UserVipVO` 类型名。字段结构参考真机
 * `leo_user_info` 的 `currentUserVipInfoKey`（含 `vipRightInfoVO` /
 * `studyGroupRightInfo` / `svipRightInfoVO` 三子结构），但顶层字段名未确证。
 * **字段为推断。**
 */
@Serializable
data class UserVipVO(
    @SerialName("vipInfos") val vipInfos: List<JsonElement> = emptyList(),
    @SerialName("vipRightInfoVO") val vipRightInfoVO: VipRightInfo = VipRightInfo(),
    @SerialName("studyGroupRightInfo") val studyGroupRightInfo: StudyGroupRightInfo = StudyGroupRightInfo(),
    @SerialName("svipRightInfoVO") val svipRightInfoVO: SvipRightInfo = SvipRightInfo(),
)