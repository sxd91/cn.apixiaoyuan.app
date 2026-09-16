package cn.apixiaoyuan.app.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 学校节点。
 *
 * 字段从真机 `leo_user_info` 的 `currentUserXiaoxueInfoStrKey` 解出，非推测：
 * `{"school":[{"height":4,"id":836863,"initial":"","name":"","parentId":0,"regionPath":""}]}`
 *
 * 原报告里 `UserPhaseInfo.school` 写成 `List<String>` 是错的，这里修正为
 * `List<SchoolNode>`。`height` 是层级（4=省级 / 2=市级 / 1=区级，从 parentId
 * 链反推：836863.parentId=0，840051.parentId=836863，983521.parentId=840051）。
 */
@Serializable
data class SchoolNode(
    @SerialName("id") val id: Long,
    @SerialName("name") val name: String = "",
    @SerialName("height") val height: Int = 0,
    @SerialName("initial") val initial: String = "",
    @SerialName("parentId") val parentId: Long = 0L,
    @SerialName("regionPath") val regionPath: String = "",
)

/**
 * 单个学段的学校列表。
 *
 * `school` 元素类型已由真机数据确证为 [SchoolNode]。
 */
@Serializable
data class UserPhaseInfo(
    @SerialName("school") val school: List<SchoolNode> = emptyList(),
)

/**
 * 用户 VIP 信息（`currentUserVipInfoKey`）。
 *
 * 真机数据：
 * `{"avatarUrl":"...","grade":100,"name":"...","vipRightInfoVO":{...},"studyGroupRightInfo":{...},"svipRightInfoVO":{...}}`
 */
@Serializable
data class UserVipInfo(
    @SerialName("avatarUrl") val avatarUrl: String? = null,
    @SerialName("grade") val grade: Int = 0,
    @SerialName("name") val name: String? = null,
    @SerialName("vipRightInfoVO") val vipRightInfoVO: VipRightInfo = VipRightInfo(),
    @SerialName("studyGroupRightInfo") val studyGroupRightInfo: StudyGroupRightInfo = StudyGroupRightInfo(),
    @SerialName("svipRightInfoVO") val svipRightInfoVO: SvipRightInfo = SvipRightInfo(),
)

/** VIP 权益状态。 */
@Serializable
data class VipRightInfo(
    @SerialName("createTime") val createTime: Long = 0L,
    @SerialName("endTime") val endTime: Long = 0L,
    @SerialName("monthly") val monthly: Boolean = false,
    @SerialName("vipHistory") val vipHistory: Boolean = false,
    @SerialName("vipSymbol") val vipSymbol: Boolean = false,
    @SerialName("rightStatus") val rightStatus: Int = 0,
)

/** 学习小组权益。 */
@Serializable
data class StudyGroupRightInfo(
    @SerialName("rightEndTime") val rightEndTime: Long = 0L,
    @SerialName("rightStatus") val rightStatus: Int = 0,
    @SerialName("monthly") val monthly: Boolean = false,
)

/** SVIP 权益。 */
@Serializable
data class SvipRightInfo(
    @SerialName("rightEndTime") val rightEndTime: Long = 0L,
    @SerialName("rightStatus") val rightStatus: Int = 0,
    @SerialName("monthly") val monthly: Boolean = false,
)
