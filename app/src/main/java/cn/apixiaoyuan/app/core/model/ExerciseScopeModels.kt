package cn.apixiaoyuan.app.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * 数学练习知识点数据（`getExercisesKeyPoints` 返回）。
 *
 * 字段逐行来自 `smali_classes3/com/fenbi/android/leo/exercise/data/ExerciseVO.smali`：
 * ```
 * borrowKeypointTip : String?
 * type              : Int
 * defaultKeypoint   : ExerciseScopeKeypointData?
 * keypointStarMap   : Map<Int, Int>?
 * sections          : List<ExerciseSectionVO>?
 * keypointProgress  : Int
 * ```
 *
 * 原版是 `BaseData` 子类（`com/yuanfudao/android/vgo/data/BaseData`），
 * 这里不继承，直接拍平成普通 data class —— 本工程不引入 vgo 依赖。
 */
@Serializable
data class ExerciseScopeData(
    @SerialName("type") val type: Int = 0,
    @SerialName("borrowKeypointTip") val borrowKeypointTip: String? = null,
    @SerialName("defaultKeypoint") val defaultKeypoint: ExerciseScopeKeypoint? = null,
    @SerialName("keypointStarMap") val keypointStarMap: Map<Int, Int>? = null,
    @SerialName("sections") val sections: List<ExerciseScopeSection>? = null,
    @SerialName("keypointProgress") val keypointProgress: Int = 0,
) {
    /**
     * 是否有可练的知识点。
     *
     * 对应原版 `ExerciseVO.isValid()`：`defaultKeypoint` 有效或 `type` 命中
     * `nj/r` 枚举任一 `exerciseType` 即算有效。
     */
    val isValid: Boolean
        get() = (defaultKeypoint?.isValid == true) ||
            ExerciseType.fromType(type) != null

    /** 拍平所有 section 下的知识点，供 UI 直接遍历。 */
    val allKeypoints: List<ExerciseScopeKeypoint>
        get() = sections.orEmpty().flatMap { it.keypoints.orEmpty() }
}

/**
 * 练习分组（`ExerciseSectionVO`）。
 *
 * ```
 * keypoints   : List<ExerciseScopeKeypointData>?
 * sectionName : String   （原版是 final 且非空，构造时置 ""）
 * ```
 */
@Serializable
data class ExerciseScopeSection(
    @SerialName("keypoints") val keypoints: List<ExerciseScopeKeypoint>? = null,
    @SerialName("sectionName") val sectionName: String = "",
)

/**
 * 单个知识点（`ExerciseScopeKeypointData`）。
 *
 * 只保留**非 transient** 字段 —— 原版里
 * `type` / `isLastExercise` / `isSectionFirst` / `isSectionLast` / `printCnt`
 * 都是 `transient`（Gson 不参与序列化），JSON 里根本不会出现，
 * 硬塞进 data class 只会永远拿到默认值。
 *
 * 非 transient 字段（逐行对照 smali 的 `.field private xxx`）：
 * ```
 * id, name, sample, sampleImageUrl, sampleImageHeight,
 * starCnt, tag, practiceCnt, questionCnt, sectionName, keypointCityRank
 * ```
 */
@Serializable
data class ExerciseScopeKeypoint(
    @SerialName("id") val id: Int = 0,
    @SerialName("name") val name: String? = null,
    @SerialName("sample") val sample: String? = null,
    @SerialName("sampleImageUrl") val sampleImageUrl: String? = null,
    @SerialName("sampleImageHeight") val sampleImageHeight: Int = 0,
    @SerialName("starCnt") val starCnt: Int? = null,
    @SerialName("tag") val tag: String? = null,
    @SerialName("practiceCnt") val practiceCnt: Int = 0,
    @SerialName("questionCnt") val questionCnt: Int = 0,
    @SerialName("sectionName") val sectionName: String = "",
    @SerialName("keypointCityRank") val keypointCityRank: JsonElement? = null,
) {
    /** 对应原版 `ExerciseScopeKeypointData.isValid()`：id > 0 且 name 非空非空白。 */
    val isValid: Boolean
        get() = id > 0 && !name.isNullOrBlank()
}
