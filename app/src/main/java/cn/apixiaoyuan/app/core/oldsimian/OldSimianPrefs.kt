package cn.apixiaoyuan.app.core.oldsimian

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 「老挂戏老叟」功能开关存储。
 *
 * 命名沿用参考项目 cn.nizou.sxd 的功能名（老挂戏老叟），但**实现完全不同**：
 *
 *  - cn.nizou.sxd 是 LSPosed 模块，开关读到后在**宿主进程内 hook**；
 *  - 本项目是**内置客户端**（自己发请求、自带 WebView），开关直接作用于
 *    自己组装请求体 / 自己注入 JS 的代码路径，不需要任何 hook。
 *
 * 存储用 [SharedPreferences]（与 [cn.apixiaoyuan.app.core.session.SessionStore]
 * 同一套路数，不引入 MMKV / DataStore）。
 *
 * **Compose 可观察**：字段同时是 `mutableStateOf`，设置页改动立即触发重组，
 * 练习页提交时读到的也是最新值 —— 因为 [cn.apixiaoyuan.app.feature.exercise.ExamViewModel.buildSubmitBody]
 * 是在提交那一刻读的，不存在快照过期问题。
 *
 * 生命周期：必须在 [cn.apixiaoyuan.app.App.onCreate] 里调 [init]，
 * 否则 [prefs] 会抛错（与 SessionStore 一致，宁可早失败也不静默丢配置）。
 *
 * ## 默认值纪律
 *
 * **所有会改变提交内容 / 改变 H5 行为的开关，默认一律关。**
 * 这些功能性质上属于「改成绩」「改包」，必须是用户显式打开才生效；
 * 默认开会在用户不知情时伪造练习记录，是绝对不能接受的。
 */
object OldSimianPrefs {

    private const val PREF_NAME = "old_simian"

    private const val KEY_AUTO_CORRECT = "auto_correct"
    private const val KEY_CUSTOM_ANSWER_ENABLED = "custom_answer_enabled"
    private const val KEY_CUSTOM_ANSWER_TEXT = "custom_answer_text"
    private const val KEY_STROKE_ENABLED = "stroke_enabled"
    private const val KEY_CUSTOM_COST_ENABLED = "custom_cost_enabled"
    private const val KEY_CUSTOM_COST_MS = "custom_cost_ms"
    private const val KEY_IGNORE_NICKNAME = "ignore_nickname_restriction"
    private const val KEY_CUSTOM_SCORE_ENABLED = "custom_score_enabled"
    private const val KEY_CUSTOM_SCORE_VALUE = "custom_score_value"
    private const val KEY_CUSTOM_SCORE_KEYPOINT = "custom_score_keypoint"
    private const val KEY_CUSTOM_SCORE_LIMIT = "custom_score_limit"
    private const val KEY_CUSTOM_SCORE_INTERVAL_MS = "custom_score_interval"
    private const val KEY_AUTO_NEXT_ROUND = "auto_next_round"
    private const val KEY_NEXT_ROUND_INTERVAL_MS = "next_round_interval_ms"
    private const val KEY_NO_RANKING_ANIM = "no_ranking_anim"

    /**
     * 每题耗时的下限（毫秒）。与
     * [cn.apixiaoyuan.app.core.model.ExamQuestion.MIN_COST_TIME_MS] 同值。
     *
     * 真机实测 5ms 可正常提交 —— 此前写 300 是照抄参考项目常量的错误结论。
     */
    const val MIN_COST_MS = 5

    /** 自定义结算时间的可调范围（毫秒）。上限给到 10s —— 再长没有实用价值且更可疑。 */
    const val COST_RANGE_MIN = 5
    const val COST_RANGE_MAX = 10_000

    /** 「结束页自动化」自动开下一局的间隔范围（毫秒）。 */
    const val NEXT_ROUND_INTERVAL_MIN = 0
    const val NEXT_ROUND_INTERVAL_MAX = 10_000

    /** 「自定义答案」文本长度上限 —— 只用于手写数字/符号，超过就是误输入。 */
    const val CUSTOM_ANSWER_MAX_LEN = 16

    /** 刷分每局题目数范围。上限对齐 cn.nizou.sxd 的 `coerceIn(1, 200)`。 */
    const val SCORE_LIMIT_MIN = 1
    const val SCORE_LIMIT_MAX = 200
    const val SCORE_LIMIT_DEFAULT = 10

    /** 刷分每局间隔范围（毫秒）。0 = 不等待。 */
    const val SCORE_INTERVAL_MIN = 0
    const val SCORE_INTERVAL_MAX = 60_000
    const val SCORE_INTERVAL_DEFAULT = 2000

    /**
     * 刷分知识点自动扫描上限。
     *
     * 取值 [1 shl 15]（32768），与 cn.nizou.sxd `ScorePump.MAX_KEYPOINT_ID` 同值 ——
     * 该值是参考项目真机验证过的「1..2^15 覆盖全部有效知识点」的结论。
     */
    const val SCORE_KEYPOINT_MAX = 1 shl 15

    /** 刷分最多刷多少局（防死循环），同 cn.nizou.sxd `ScorePump.MAX_ROUNDS`。 */
    const val SCORE_MAX_ROUNDS = 1000

    @Volatile
    private var appContext: Context? = null

    // ---- 练习：作答内容 ----

    /**
     * 自动全部答对（对应老挂戏老叟的「任意答案均改为正确答案」）。
     *
     * 开启后，练习提交时每题 `userAnswer` 取服务端下发的正确答案
     * （`ExamQuestion.answers.first()`）、`status = 1`、整卷 `correctCnt = 题目数`。
     *
     * 默认关。这是「改成绩」性质的功能，默认必须是用户显式打开。
     */
    var autoCorrect by mutableStateOf(false)

    /** 是否启用「自定义答案」（提交时 `userAnswer` 固定为 [customAnswerText]）。默认关。 */
    var customAnswerEnabled by mutableStateOf(false)

    /**
     * 自定义答案文本。
     *
     * 语义与老挂戏老叟的「自定义答案」一致：不管题目正确答案是什么，提交的
     * `userAnswer` 都是这个值；`status` 按「它是否恰好等于正确答案」判定
     * （等于记对、不等记错），**不伪造 status** —— 伪造判分结果需要同时改
     * `correctCnt`，那是 [autoCorrect] 的职责，两个开关语义不重叠。
     *
     * 空串视为未配置（等价于开关关闭），避免用户误开开关后提交空答案。
     */
    var customAnswerText by mutableStateOf("")

    /**
     * 提交画笔（对应老挂戏老叟的「提交画笔」/「按题目数量提交等量画笔」）。
     *
     * 开启后，练习提交时每题的 `script`（笔迹 JSON）由 [OralStrokes] 按该题
     * 实际提交的 `userAnswer` 生成 —— 有 N 道题就生成 N 条笔迹，正是「按题目
     * 数量提交等量画笔」。
     *
     * 服务端 `script` 只用于笔迹回放展示，不参与判分（判分看 `userAnswer` /
     * `status`）；但原版客户端**总是**带笔迹提交，空 `script` 反而是异常特征，
     * 所以这个开关默认关、打开后补齐笔迹。
     */
    var strokeEnabled by mutableStateOf(false)

    // ---- 练习：结算时间 ----

    /** 是否启用自定义结算时间。默认关。 */
    var customCostEnabled by mutableStateOf(false)

    /** 自定义每题耗时（毫秒），范围 [COST_RANGE_MIN]..[COST_RANGE_MAX]。默认取下限。 */
    var customCostMs by mutableStateOf(MIN_COST_MS)

    // ---- 账号：名字限制 ----

    /**
     * 无视名字限制。
     *
     * ⚠️ **本项目当前无对应链路**（诚实标注，不假装实现）：
     *  - 老挂戏老叟 hook 的是**宿主**的昵称校验器（`cs.p7.a` GBK 字节长度 ≤16
     *    与昵称格式 `Pattern`），因为昵称是在宿主的「个人资料」页改的；
     *  - 本项目目前**没有昵称编辑入口**（`UserVO.nickname` 只读展示），
     *    且即使有，校验最终由**服务端**执行，客户端放开本地校验没有意义。
     *
     * 所以这个开关只作为**配置占位**保留：等本项目接入「个人资料编辑」时，
     * 由那条链路的客户端预校验读它。当前它不影响任何行为。
     */
    var ignoreNicknameRestriction by mutableStateOf(false)

    // ---- 分数 ----

    /**
     * 自定义分数（刷分）总开关。
     *
     * **已接线**（2026-09-25）：链路走 `PUT /leo-math/android/exams/v2/{examId}`
     * （`uploadExamResult`，练习成绩上传主接口），而不是旧方案猜的
     * `POST /leo-star/android/exercise/rank/login/attend`。
     *
     * 取证依据（逐行确证）：
     *  - 参考项目 cn.nizou.sxd `api/ScorePump.kt` 的注释与实现：`postSavedExp`
     *    实走 attend 接口，**服务端限次（真机实测每天约 3 次）**；
     *    `uploadExamResult` 才是「自动上分」走的接口，**无 attend 的日限语义**；
     *  - 本项目 `LeoOralApiService.uploadExamResult` 已存在且带 `@NeedEncode`；
     *  - `@NeedEncode` 的真实实现已确证为
     *    `gzip 压缩 → libContentEncoder.so 的 c(byte[])`（原版 `ds/i4.c([B])`），
     *    本项目已内置该 so，编码方向由
     *    [cn.apixiaoyuan.app.core.native.NativeEncodeInstaller] 落地。
     *
     * 本开关只表示「刷分功能可用」；真正开始刷分由
     * [cn.apixiaoyuan.app.feature.oldsimian.ScorePumpViewModel] 按用户点击触发。
     * 默认关。
     */
    var customScoreEnabled by mutableStateOf(false)

    /**
     * 刷分的目标分数（`curWeekScore`）。
     *
     * 语义是「刷到 ≥ 该值就停」，**不是**「把分数改成该值」—— 分数由服务端
     * 按实际上传的练习记录累计，客户端只能多刷几局逼近。0 表示未配置。
     */
    var customScoreValue by mutableStateOf(0)

    /**
     * 刷分用的知识点 ID（字符串形态，`getExamInfo` 的 `keypointId`）。
     *
     * 留空（空串）= 自动扫描：取题失败时从 1 遍历到 [SCORE_KEYPOINT_MAX]，
     * 找到第一个能出题的知识点即写入本字段（对齐 cn.nizou.sxd 的
     * `custom_score_keypoint` 行为）。
     */
    var customScoreKeypoint by mutableStateOf("")

    /** 刷分每局题目数。范围 [SCORE_LIMIT_MIN]..[SCORE_LIMIT_MAX]。 */
    var customScoreLimit by mutableStateOf(SCORE_LIMIT_DEFAULT)

    /** 刷分每局间隔（毫秒），用于降低请求频率。范围 [SCORE_INTERVAL_MIN]..[SCORE_INTERVAL_MAX]。 */
    var customScoreIntervalMs by mutableStateOf(SCORE_INTERVAL_DEFAULT)

    // ---- PK / H5 ----

    /**
     * 结束页自动化：PK 结算页自动开下一局。
     *
     * 实现方式（**内置架构的天然优势**）：PK 是 H5，本项目自带 WebView，
     * 直接在 `onPageFinished` 后注入 `assets/js/pk_auto_next.js` 即可 ——
     * 老挂戏老叟那边要靠 hook 宿主的 `loadUrl` 才能注入，这边不需要。
     */
    var autoNextRound by mutableStateOf(false)

    /** 自动开下一局的间隔（毫秒）。 */
    var nextRoundIntervalMs by mutableStateOf(1500)

    /**
     * 去除排行榜展示动效。
     *
     * 注入 `assets/js/pk_no_anim.js`：把 CSS 动画/过渡压到 0s 并静音音效，
     * 排行榜与切题的展示动画随之消失。与 [autoNextRound] 相互独立
     * （只想去动画、不想自动连开的人可以只开这个）。
     */
    var noRankingAnim by mutableStateOf(false)

    /** 由 `App.onCreate` 调用。 */
    fun init(context: Context) {
        appContext = context.applicationContext
        val p = prefs()
        autoCorrect = p.getBoolean(KEY_AUTO_CORRECT, false)
        customAnswerEnabled = p.getBoolean(KEY_CUSTOM_ANSWER_ENABLED, false)
        customAnswerText = p.getString(KEY_CUSTOM_ANSWER_TEXT, "").orEmpty()
        strokeEnabled = p.getBoolean(KEY_STROKE_ENABLED, false)
        customCostEnabled = p.getBoolean(KEY_CUSTOM_COST_ENABLED, false)
        customCostMs = p.getInt(KEY_CUSTOM_COST_MS, MIN_COST_MS)
            .coerceIn(COST_RANGE_MIN, COST_RANGE_MAX)
        ignoreNicknameRestriction = p.getBoolean(KEY_IGNORE_NICKNAME, false)
        customScoreEnabled = p.getBoolean(KEY_CUSTOM_SCORE_ENABLED, false)
        customScoreValue = p.getInt(KEY_CUSTOM_SCORE_VALUE, 0).coerceAtLeast(0)
        customScoreKeypoint = p.getString(KEY_CUSTOM_SCORE_KEYPOINT, "").orEmpty()
        customScoreLimit = p.getInt(KEY_CUSTOM_SCORE_LIMIT, SCORE_LIMIT_DEFAULT)
            .coerceIn(SCORE_LIMIT_MIN, SCORE_LIMIT_MAX)
        customScoreIntervalMs = p.getInt(KEY_CUSTOM_SCORE_INTERVAL_MS, SCORE_INTERVAL_DEFAULT)
            .coerceIn(SCORE_INTERVAL_MIN, SCORE_INTERVAL_MAX)
        autoNextRound = p.getBoolean(KEY_AUTO_NEXT_ROUND, false)
        nextRoundIntervalMs = p.getInt(KEY_NEXT_ROUND_INTERVAL_MS, 1500)
            .coerceIn(NEXT_ROUND_INTERVAL_MIN, NEXT_ROUND_INTERVAL_MAX)
        noRankingAnim = p.getBoolean(KEY_NO_RANKING_ANIM, false)
    }

    /** 写盘。设置页每次改动调用一次。 */
    fun persist() {
        prefs().edit()
            .putBoolean(KEY_AUTO_CORRECT, autoCorrect)
            .putBoolean(KEY_CUSTOM_ANSWER_ENABLED, customAnswerEnabled)
            .putString(KEY_CUSTOM_ANSWER_TEXT, customAnswerText)
            .putBoolean(KEY_STROKE_ENABLED, strokeEnabled)
            .putBoolean(KEY_CUSTOM_COST_ENABLED, customCostEnabled)
            .putInt(KEY_CUSTOM_COST_MS, customCostMs)
            .putBoolean(KEY_IGNORE_NICKNAME, ignoreNicknameRestriction)
            .putBoolean(KEY_CUSTOM_SCORE_ENABLED, customScoreEnabled)
            .putInt(KEY_CUSTOM_SCORE_VALUE, customScoreValue)
            .putString(KEY_CUSTOM_SCORE_KEYPOINT, customScoreKeypoint)
            .putInt(KEY_CUSTOM_SCORE_LIMIT, customScoreLimit)
            .putInt(KEY_CUSTOM_SCORE_INTERVAL_MS, customScoreIntervalMs)
            .putBoolean(KEY_AUTO_NEXT_ROUND, autoNextRound)
            .putInt(KEY_NEXT_ROUND_INTERVAL_MS, nextRoundIntervalMs)
            .putBoolean(KEY_NO_RANKING_ANIM, noRankingAnim)
            .apply()
    }

    /**
     * 本题应填的耗时（毫秒）。
     *
     * @param fallback 未启用自定义结算时间时使用的原值（已含下限保护）。
     */
    fun costTimeFor(fallback: Long): Long =
        if (customCostEnabled) customCostMs.toLong() else fallback

    /**
     * 本题实际要提交的作答文本。
     *
     * 优先级（高 → 低）：
     *  1. [autoCorrect] —— 用服务端下发的正确答案（`rightAnswer`）；
     *  2. [customAnswerEnabled] 且 [customAnswerText] 非空 —— 用自定义答案；
     *  3. 用户自己作的答（`mark` 转成的原始作答）。
     *
     * @param rightAnswer 服务端下发的正确答案（可空）
     * @param userAnswer  用户实际作答（可空）
     */
    fun answerFor(rightAnswer: String?, userAnswer: String?): String = when {
        autoCorrect -> rightAnswer ?: userAnswer ?: ""
        customAnswerEnabled && customAnswerText.isNotBlank() -> customAnswerText.trim()
        else -> userAnswer ?: ""
    }

    private fun prefs(): SharedPreferences {
        val ctx = appContext ?: error("OldSimianPrefs.init() 未调用")
        return ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }
}