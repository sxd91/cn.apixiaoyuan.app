package cn.apixiaoyuan.app.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * 星级任务首页数据（`getCurrentUserTasks` 返回）。
 *
 * ⚠️ 字段靠推断：`LeoCurrentTaskInfo` 的真实字段未从 smali 读出，
 * 这里按「任务列表 + 完成状态」的常见形态落盘。真机抓包后按实际响应修正。
 * 这是本批交付里唯一「字段类型靠推断」的两个类之一。
 */
@Serializable
data class LeoCurrentTaskInfo(
    @SerialName("tasks") val tasks: List<LeoTaskItem> = emptyList(),
    @SerialName("finishedCount") val finishedCount: Int = 0,
    @SerialName("totalCount") val totalCount: Int = 0,
)

/** 单个任务项。字段同样为推断，待真机确认。 */
@Serializable
data class LeoTaskItem(
    @SerialName("taskId") val taskId: Long = 0L,
    @SerialName("title") val title: String? = null,
    @SerialName("finished") val finished: Boolean = false,
    @SerialName("exp") val exp: Int = 0,
)

/**
 * 当前用户经验值（`getCurrentUserExp` 返回）。
 *
 * ⚠️ 字段靠推断：真机 `leo_user_mmkv` 的 `cachedPetStatusDataKey` 里有
 * `currentExp` / `currentLevel` / `nextLevelExp` 三个字段，与此接口
 * 返回的数据同量纲，据此落盘。待真机抓完整响应确认。
 */
@Serializable
data class LeoUserCurrentExpData(
    @SerialName("currentExp") val currentExp: Int = 0,
    @SerialName("currentLevel") val currentLevel: Int = 1,
    @SerialName("nextLevelExp") val nextLevelExp: Int = 0,
    @SerialName("maxLevelReached") val maxLevelReached: Boolean = false,
)

/**
 * 今日练习列表（`postSavedExp` 的请求体）。
 *
 * ⚠️ 该接口带 `@NeedEncode` —— 请求体在发出前需 native 编码。
 * 字段未确证，这里用宽松结构占位（`JsonElement` 保留任意 JSON），
 * 保证序列化不丢字段。native 编码器接入前，这个占位不影响编译。
 */
@Serializable
data class LeoTodayExerciseListData(
    @SerialName("exerciseList") val exerciseList: List<JsonElement> = emptyList(),
    @SerialName("totalExp") val totalExp: Int = 0,
    @SerialName("date") val date: String? = null,
)

/** 英语练习章节（`getEnglishExercisesSections` 返回）。字段靠推断。 */
@Serializable
data class ExerciseEnglishSectionVO(
    @SerialName("sections") val sections: List<ExerciseSection> = emptyList(),
)

/** 单个章节。 */
@Serializable
data class ExerciseSection(
    @SerialName("sectionId") val sectionId: Long = 0L,
    @SerialName("title") val title: String? = null,
    @SerialName("unitIds") val unitIds: List<String> = emptyList(),
)

/**
 * 英语听写内容（`getEnglishExercisesDictationContentList` 返回）。
 *
 * ⚠️ 字段靠推断。真机抓包后修正。
 */
@Serializable
data class ExerciseEnglishDictationContent(
    @SerialName("units") val units: List<DictationUnit> = emptyList(),
)

/** 听写单元。 */
@Serializable
data class DictationUnit(
    @SerialName("unitId") val unitId: String? = null,
    @SerialName("title") val title: String? = null,
    @SerialName("words") val words: List<DictationWord> = emptyList(),
)

/** 听写单词。 */
@Serializable
data class DictationWord(
    @SerialName("word") val word: String? = null,
    @SerialName("translation") val translation: String? = null,
    @SerialName("audioUrl") val audioUrl: String? = null,
)

/**
 * 线上听写内容（`repeatEnglishOnlineDictation` 返回）。
 *
 * ⚠️ 字段靠推断。
 */
@Serializable
data class OnlineExerciseEnglishDictationContent(
    @SerialName("exerciseId") val exerciseId: Long = 0L,
    @SerialName("units") val units: List<DictationUnit> = emptyList(),
)

/**
 * 听写历史结果（`getEnglishDictationExerciseResult` 返回）。
 *
 * ⚠️ 字段靠推断。
 */
@Serializable
data class EnglishListenExerciseHistoryResultVO(
    @SerialName("exerciseId") val exerciseId: Long = 0L,
    @SerialName("score") val score: Int = 0,
    @SerialName("correctCount") val correctCount: Int = 0,
    @SerialName("totalCount") val totalCount: Int = 0,
    @SerialName("finishedTime") val finishedTime: Long = 0L,
)

/**
 * 听写评测结果（`queryEnglish` 返回）。
 *
 * ⚠️ 字段靠推断。
 */
@Serializable
data class EnglishListenExerciseResultVO(
    @SerialName("exerciseId") val exerciseId: Long = 0L,
    @SerialName("score") val score: Int = 0,
    @SerialName("details") val details: List<JsonElement> = emptyList(),
)