package cn.apixiaoyuan.app.feature.exercise

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.apixiaoyuan.app.core.exercise.ExerciseRepository
import cn.apixiaoyuan.app.core.model.ExerciseScopeData
import cn.apixiaoyuan.app.core.model.ExerciseScopeKeypoint
import cn.apixiaoyuan.app.core.model.ExerciseSection
import cn.apixiaoyuan.app.core.model.ExerciseType
import cn.apixiaoyuan.app.core.model.LeoCurrentTaskInfo
import cn.apixiaoyuan.app.core.model.LeoUserCurrentExpData
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch

/**
 * 练习页状态机。
 *
 * 两条链路：
 *
 * **一、概览（进页面就拉）**
 *  - 任务卡（`/leo-star/android/exercise/task/home`）
 *  - 经验值（`/leo-star/android/exercise/rank/pre-fetch`）
 *  - 英语章节（`/leo-english/android/exercise/{type}`）
 *
 * **二、数学出题（「10 以内加减法 + 题目数量可选」）**
 *  - [selectedType] 决定题型，默认 [ExerciseType.ORAL]（口算练习，type=0）
 *  - [selectedNum] 决定题目数量，默认取 `type.chooseNumArray.first()`
 *  - 拉 `getExercisesKeyPoints(type, grade, semester, book)` → [mathScope]
 *  - [mathScope] 里 `sections[].keypoints[]` 即可选知识点，点进去出题
 *
 * 题型与题量口径**严格照原版** `nj/r` 枚举：口算练习可选
 * `[10, 20, 30, 60, 100]`，其余题型见 [ExerciseType]。
 */
class ExerciseViewModel : ViewModel() {

    // ---- 概览 ----

    /** 是否正在拉取概览。 */
    var loading by mutableStateOf(false)
        private set

    /** 任务卡数据。null 表示未拉到。 */
    var tasks by mutableStateOf<LeoCurrentTaskInfo?>(null)
        private set

    /** 经验值数据。null 表示未拉到。 */
    var exp by mutableStateOf<LeoUserCurrentExpData?>(null)
        private set

    /** 英语章节列表。空列表表示未拉到或确无数据。 */
    var englishSections by mutableStateOf<List<ExerciseSection>>(emptyList())
        private set

    /** 概览错误提示。null 表示无错误。 */
    var errorMessage by mutableStateOf<String?>(null)
        private set

    // ---- 数学出题 ----

    /** 当前选中的题型。默认口算练习（type=0），即「10 以内加减法」入口。 */
    var selectedType by mutableStateOf(ExerciseType.ORAL)
        private set

    /** 当前选中的题目数量。默认取题型的第一个可选值（口算是 10）。 */
    var selectedNum by mutableStateOf(ExerciseType.ORAL.defaultNum)
        private set

    /** 当前题型的知识点树。null 表示未拉到或失败。 */
    var mathScope by mutableStateOf<ExerciseScopeData?>(null)
        private set

    /** 数学出题链路是否加载中。 */
    var mathLoading by mutableStateOf(false)
        private set

    /** 数学出题链路错误。null 表示无错误。 */
    var mathError by mutableStateOf<String?>(null)
        private set

    /** 拍平后的知识点列表，UI 直接遍历。 */
    val keypoints: List<ExerciseScopeKeypoint>
        get() = mathScope?.allKeypoints.orEmpty()

    /**
     * 拉取概览三件套。三路并发。
     *
     * 英语章节的四个参数当前用常量占位（grade=2 是真机 UserVO.grade 实测值），
     * 待会话存储落盘 UserVO 后替换。
     */
    fun load() {
        if (loading) return
        loading = true
        errorMessage = null

        viewModelScope.launch {
            val tasksDeferred = async { ExerciseRepository.fetchTasks() }
            val expDeferred = async { ExerciseRepository.fetchExp() }
            val sectionsDeferred = async {
                ExerciseRepository.fetchEnglishSections(
                    type = DEFAULT_ENGLISH_TYPE,
                    grade = DEFAULT_GRADE,
                    semester = DEFAULT_SEMESTER,
                    book = DEFAULT_BOOK,
                )
            }

            awaitAll(tasksDeferred, expDeferred, sectionsDeferred)

            tasks = tasksDeferred.await()
            exp = expDeferred.await()
            englishSections = sectionsDeferred.await()?.sections ?: emptyList()

            loading = false

            // 三份全空 —— 最可能是未登录或 cookie 失效。
            if (tasks == null && exp == null && englishSections.isEmpty()) {
                errorMessage = "未登录或网络异常，请先在「登录」页完成登录"
            }
        }

        // 概览拉的同时把数学出题链路也拉起来，进页面即可直接选知识点。
        loadMathScope()
    }

    /**
     * 切换题型。题量自动回落到新题型的第一个可选值
     * （原版 `chooseNumArray` 第一项），并重新拉知识点树。
     */
    fun selectType(type: ExerciseType) {
        if (type == selectedType) return
        selectedType = type
        selectedNum = type.defaultNum
        loadMathScope()
    }

    /** 切换题目数量。只允许 [ExerciseType.chooseNumArray] 内的值。 */
    fun selectNum(num: Int) {
        if (num !in selectedType.chooseNumArray) return
        selectedNum = num
    }

    /**
     * 拉当前题型的知识点树。
     *
     * `type` 用 [ExerciseType.exerciseType] 作为 Path 参数，
     * grade/semester/book 与英语章节同源（当前常量占位）。
     */
    fun loadMathScope() {
        if (mathLoading) return
        mathLoading = true
        mathError = null

        viewModelScope.launch {
            val scope = ExerciseRepository.fetchMathScope(
                type = selectedType,
                grade = DEFAULT_GRADE,
                semester = DEFAULT_SEMESTER,
                book = DEFAULT_BOOK,
            )
            mathScope = scope
            mathLoading = false
            if (scope == null) {
                mathError = "「${selectedType.displayName}」知识点拉取失败"
            } else if (scope.allKeypoints.isEmpty()) {
                mathError = "「${selectedType.displayName}」暂无可练知识点"
            }
        }
    }

    /** 概览重试：清错误后重新拉。 */
    fun retry() {
        errorMessage = null
        load()
    }

    /** 数学链路重试。 */
    fun retryMath() {
        mathError = null
        loadMathScope()
    }

    companion object {
        /** 英语练习类型：听写。原版 Int 枚举，取值待真机确认。 */
        private const val DEFAULT_ENGLISH_TYPE = 1

        /** 年级：2。真机 UserVO.grade 实测值（`leo_user_info` 的 currentUserInfoStrKey）。 */
        private const val DEFAULT_GRADE = 2

        /** 学期：上学期。 */
        private const val DEFAULT_SEMESTER = 1

        /** 教材版本：默认版。 */
        private const val DEFAULT_BOOK = 1
    }
}
