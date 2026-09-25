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
 * 字段来自原版 `com/fenbi/android/leo/data/LeoUserCurrentExpData`
 * （`smali_classes2`，来自 `leo-exercise-common-legacy_release`）逐行读出，
 * 构造签名为 `(LeoExpectedMultipleData;IJLjava/util/List;)V`：
 *
 *  - [expectedMultiple]：非空对象，只含一个 `multiple` 字段
 *  - [curWeekScore]：本周分数 —— **刷分链路读的就是它**
 *  - [rankVersion]：排行榜版本号
 *  - [multiInfoList]：多倍任务列表，非空，默认空表
 *
 * 此前本类曾按真机 `leo_user_mmkv` 的 `cachedPetStatusDataKey`
 * （`currentExp` / `currentLevel` / `nextLevelExp`）推断字段，**已证伪**：
 * 那是本地缓存键，与本接口响应不同源。刷分需要读 [curWeekScore]，
 * 字段名不匹配会直接拿到 0，因此这里按原版逐字对齐。
 *
 * ## 2026-09-25 实测响应（真机账号，主域认证打通后）
 *
 * 请求 `GET /leo-star/android/exercise/rank/pre-fetch` → **HTTP 200**：
 * ```json
 * {"ver":"1.0","status":200,"message":"","data":{
 *   "curRank":0,"curWeekScore":0,
 *   "expectedMultiple":{"multiple":1,"continuousCheckInCount":1},
 *   "multiInfoList":null,"rankVersion":0,
 *   "preExerciseContext":{...}}}
 * ```
 *
 * 三点实证：
 *  1. **响应有信封** —— 真实结构是 `{ver, status, message, data}`，
 *     业务字段在 `data` 里，不是平铺。本类（以及 `GsonConverter` 链路）
 *     直接反序列化 `data` 的内容，因此这里不建信封类；
 *  2. [expectedMultiple] 实测比原版 smali 多一个
 *     `continuousCheckInCount` 字段，本类已有默认值能接住，不报错；
 *  3. [multiInfoList] 实测可为 **null**（不是空数组）—— 本类声明为
 *     非空 `List` 带默认值 `emptyList()`，kotlinx 的
 *     `coerceInputValues = true` 会把显式 null 转成默认值，安全。
 *
 * [curWeekScore] 实测为 0（新账号 / 当周未练习），字段名已确证。
 */
@Serializable
data class LeoUserCurrentExpData(
    @SerialName("expectedMultiple") val expectedMultiple: LeoExpectedMultipleData = LeoExpectedMultipleData(),
    @SerialName("curWeekScore") val curWeekScore: Int = 0,
    @SerialName("rankVersion") val rankVersion: Long = 0L,
    @SerialName("multiInfoList") val multiInfoList: List<MultipleTask> = emptyList(),
)

/**
 * 期望倍率（原版 `LeoExpectedMultipleData`）。
 *
 * 原版 smali 里该类只有一个 `private final multiple:I` 字段（构造签名 `(I)V`）。
 *
 * **2026-09-25 实测发现服务端多下发了一个字段** ——
 * `{"multiple":1,"continuousCheckInCount":1}`。原版能忽略它（Gson 宽松），
 * 本项目也补上以免依赖 `ignoreUnknownKeys`（那是全局兜底，
 * 显式声明更清楚，也便于后续用这个值）。
 */
@Serializable
data class LeoExpectedMultipleData(
    @SerialName("multiple") val multiple: Int = 0,
    /** 连续打卡天数。原版 smali 未声明，服务端实测下发。 */
    @SerialName("continuousCheckInCount") val continuousCheckInCount: Int = 0,
)

/**
 * 多倍任务（原版 `MultipleTask`）。
 *
 * 三个字段：`private final expireTime:J` / `private final multiType:I` /
 * `private final multiple:I`。
 */
@Serializable
data class MultipleTask(
    @SerialName("expireTime") val expireTime: Long = 0L,
    @SerialName("multiType") val multiType: Int = 0,
    @SerialName("multiple") val multiple: Int = 0,
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