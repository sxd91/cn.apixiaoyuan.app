package cn.apixiaoyuan.app.feature.exercise

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.apixiaoyuan.app.core.exercise.ExerciseRepository
import cn.apixiaoyuan.app.core.model.ExerciseSection
import cn.apixiaoyuan.app.core.model.LeoCurrentTaskInfo
import cn.apixiaoyuan.app.core.model.LeoUserCurrentExpData
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch

/**
 * 练习页状态机（读链路）。
 *
 * 首屏并发拉三份数据：任务卡、经验值、英语章节。三者在
 * [ExerciseRepository] 里各自 try 兜底，所以这里只需处理
 * 「拿到 / 没拿到」两态，不用管异常分类。
 *
 * 拉取策略：
 *  - 任务卡 + 经验值是首页必现内容，进页面就拉
 *  - 英语章节依赖四个参数（type/grade/semester/book），
 *    原版这几个值存在 [cn.apixiaoyuan.app.core.session.SessionStore]
 *    对应的用户资料里（`UserVO.grade`），当前先用常量占位，
 *    等模块 3 的会话存储把 UserVO 落盘后替换成真实值
 *
 * 空态处理：任一请求失败即显示「未登录或网络异常」，因为这三个接口
 * 都要求登录态（cookie 由 [cn.apixiaoyuan.app.core.session.PersistentCookieJar]
 * 自动带上）。
 */
class ExerciseViewModel : ViewModel() {

    /** 是否正在拉取首屏。 */
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

    /** 错误提示。null 表示无错误。 */
    var errorMessage by mutableStateOf<String?>(null)
        private set

    /** 是否已经拉过一次（区分「首屏加载中」与「拉完为空」）。 */
    var loaded by mutableStateOf(false)
        private set

    /**
     * 拉取首屏全部数据。三路并发。
     *
     * 英语章节的四个参数当前用常量占位：
     *  - type=1（原版听写类型，待真机确认枚举值）
     *  - grade=2（真机 UserVO.grade 实测值，见 `leo_user_info`）
     *  - semester=1
     *  - book=1
     * 待会话存储落盘 UserVO 后，从 `UserVO.grade` 读真实值。
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
                    type = DEFAULT_TYPE,
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
            loaded = true

            // 三份全空 —— 最可能是未登录或 cookie 失效。
            if (tasks == null && exp == null && englishSections.isEmpty()) {
                errorMessage = "未登录或网络异常，请先在「登录」页完成登录"
            }
        }
    }

    /** 重试：清错误后重新拉。 */
    fun retry() {
        errorMessage = null
        load()
    }

    companion object {
        /** 英语练习类型：听写。原版 Int 枚举，取值待真机确认。 */
        private const val DEFAULT_TYPE = 1

        /** 年级：2。真机 UserVO.grade 实测值（`leo_user_info` 的 currentUserInfoStrKey）。 */
        private const val DEFAULT_GRADE = 2

        /** 学期：上学期。 */
        private const val DEFAULT_SEMESTER = 1

        /** 教材版本：默认版。 */
        private const val DEFAULT_BOOK = 1
    }
}