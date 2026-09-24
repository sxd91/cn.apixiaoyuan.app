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
 *  - cn.nizou.sxd 是 LSPosed 模块，开关读到后在宿主进程内 hook；
 *  - 本项目是**内置客户端**（自己发请求、自带 WebView），开关直接作用于
 *    自己组装请求体的代码路径，不需要任何 hook。
 *
 * 存储用 [SharedPreferences]（与 [cn.apixiaoyuan.app.core.session.SessionStore]
 * 同一套路数，不引入 MMKV / DataStore）。
 *
 * **Compose 可观察**：字段同时是 `mutableStateOf`，设置页改动立即触发重组，
 * 练习页提交时读到的也是最新值 —— 因为 [ExamViewModel.buildSubmitBody] 是在
 * 提交那一刻读的，不存在快照过期问题。
 *
 * 生命周期：必须在 [cn.apixiaoyuan.app.App.onCreate] 里调 [init]，
 * 否则 [prefs] 会抛错（与 SessionStore 一致，宁可早失败也不静默丢配置）。
 */
object OldSimianPrefs {

    private const val PREF_NAME = "old_simian"
    private const val KEY_AUTO_CORRECT = "auto_correct"
    private const val KEY_CUSTOM_COST_ENABLED = "custom_cost_enabled"
    private const val KEY_CUSTOM_COST_MS = "custom_cost_ms"

    /** 每题耗时的服务端下限（毫秒）。与 [cn.apixiaoyuan.app.core.model.ExamQuestion.MIN_COST_TIME_MS] 同值。 */
    const val MIN_COST_MS = 300

    /** 自定义结算时间的可调范围（毫秒）。上限给到 10s —— 再长没有实用价值且更可疑。 */
    const val COST_RANGE_MIN = 300
    const val COST_RANGE_MAX = 10_000

    @Volatile
    private var appContext: Context? = null

    /**
     * 自动全部答对。
     *
     * 开启后，练习提交时每题 `userAnswer` 取服务端下发的正确答案
     * （`ExamQuestion.answers.first()`）、`status = 1`、整卷 `correctCnt = 题目数`。
     *
     * 默认关。这是「改成绩」性质的功能，默认必须是用户显式打开。
     */
    var autoCorrect by mutableStateOf(false)

    /** 是否启用自定义结算时间。默认关。 */
    var customCostEnabled by mutableStateOf(false)

    /** 自定义每题耗时（毫秒），范围 [COST_RANGE_MIN]..[COST_RANGE_MAX]。默认取服务端下限。 */
    var customCostMs by mutableStateOf(MIN_COST_MS)

    /** 由 `App.onCreate` 调用。 */
    fun init(context: Context) {
        appContext = context.applicationContext
        val p = prefs()
        autoCorrect = p.getBoolean(KEY_AUTO_CORRECT, false)
        customCostEnabled = p.getBoolean(KEY_CUSTOM_COST_ENABLED, false)
        customCostMs = p.getInt(KEY_CUSTOM_COST_MS, MIN_COST_MS)
            .coerceIn(COST_RANGE_MIN, COST_RANGE_MAX)
    }

    /** 写盘。设置页每次改动调用一次。 */
    fun persist() {
        prefs().edit()
            .putBoolean(KEY_AUTO_CORRECT, autoCorrect)
            .putBoolean(KEY_CUSTOM_COST_ENABLED, customCostEnabled)
            .putInt(KEY_CUSTOM_COST_MS, customCostMs)
            .apply()
    }

    /**
     * 本题应填的耗时（毫秒）。
     *
     * @param fallback 未启用自定义结算时间时使用的原值（已含下限保护）。
     */
    fun costTimeFor(fallback: Long): Long =
        if (customCostEnabled) customCostMs.toLong() else fallback

    private fun prefs(): SharedPreferences {
        val ctx = appContext ?: error("OldSimianPrefs.init() 未调用")
        return ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }
}
