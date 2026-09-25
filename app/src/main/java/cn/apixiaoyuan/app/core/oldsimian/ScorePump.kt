package cn.apixiaoyuan.app.core.oldsimian

import cn.apixiaoyuan.app.core.exercise.ExerciseRepository
import cn.apixiaoyuan.app.core.model.ExamData
import cn.apixiaoyuan.app.core.model.ExamQuestion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeout
import kotlin.random.Random

/**
 * 练习批量上传刷分（「自定义分数」）。
 *
 * ## 语义蓝本
 *
 * 逐行对齐参考项目 cn.nizou.sxd 的 `api/ScorePump.kt`，**但实现完全不同**：
 * 那边是 LSPosed 模块，只能靠 `XposedHelpers` 反射宿主对象 + `CountDownLatch`
 * 同步等待宿主的回调式接口；本项目是**内置客户端**，接口本身就是 `suspend`，
 * 所以这里：
 *
 * | | cn.nizou.sxd | 本项目 |
 * |---|---|---|
 * | 线程模型 | `thread {}` + `CountDownLatch` 阻塞等待 | `suspend` + 直接 `await` |
 * | 对象访问 | `XposedHelpers.getIntField/getObjectField/callMethod` | 强类型 [ExamData] / [ExamQuestion] |
 * | 停止方式 | `@Volatile stopped` 标志 | 协程取消（Job.cancel） |
 * | 超时 | `CountDownLatch.await(timeout)` | `withTimeout` |
 *
 * ## 为什么是 `uploadExamResult` 而不是 `postSavedExp`
 *
 * 参考项目已经用真机验证过这个坑（原注释照录）：
 *  - `postSavedExp` 实走 `POST /leo-star/android/exercise/rank/login/attend`，
 *    **服务端限次（实测每天约 3 次）** —— 旧「刷分/拆条」方案就是撞上了它；
 *  - `uploadExamResult` 实走 `PUT /leo-math/android/exams/v2/{examId}`，
 *    是练习成绩上传主接口（原版「自动上分」走的也是它），**无 attend 的日限语义**。
 *
 * 本项目 `LeoOralApiService.uploadExamResult` 已存在，body 带 `@NeedEncode`，
 * 编码方向已由 [cn.apixiaoyuan.app.core.native.NativeEncodeInstaller] 落地
 * （`gzip` → `libContentEncoder.so` 的 `c(byte[])`，与原版 `ds/i4.c([B])` 同链路）。
 *
 * ## 算法
 *
 * ```
 * fetchCurrentScore()                        ← 读 curWeekScore
 *   ≥ 目标 → 直接结束
 * 循环（最多 SCORE_MAX_ROUNDS 局）：
 *   getExamInfo(keypointId, limit)           ← 取真实卷子
 *     失败 → 从 1 遍历到 SCORE_KEYPOINT_MAX 找有效知识点（找到即回调上报，写入 prefs）
 *   fillFullCorrect(exam)                    ← 全对填充（答案 / 笔迹 / status=1 / costTime）
 *   uploadExamResult(examId, exam)           ← 上传
 *   fetchCurrentScore()                      ← 重新读数，达标即停
 *   delay(intervalMs)
 * ```
 *
 * 分数由服务端按**实际上传的练习记录**累计，客户端只能多刷几局逼近目标，
 * 不存在「直接改分数」的接口 —— 这一点在 UI 上必须如实告知用户。
 *
 * ## ⚠️ 已知阻塞：`solar-encoder` 417（2026-09-25 实测）
 *
 * 本文件写完后，对真机账号实测发现：
 *
 *  - **分数读取可用**：`GET /leo-star/android/exercise/rank/pre-fetch`
 *    在「设备链 + 登录 cookie」两层齐备时返回 **200 + 真实数据** ——
 *    所以 [fetchCurrentScore] 这条路是通的；
 *  - **取卷与上传不可用**：`POST /leo-math/android/exams`（取卷）与
 *    `PUT /leo-math/android/exams/v2/{examId}`（上传）都返回
 *    **417**，响应头带 **`x-block-by: solar-encoder`**。
 *
 * `solar-encoder` 是**服务端编码中间件**的标识，不是认证问题
 * （认证已过；同域下 pre-fetch 就是 200）。它要求请求体/参数经过
 * 原版 native 编码链路（`libRequestEncoder.so` / `libContentEncoder.so`）
 * 处理，而该链路的**完整口径尚未复刻** —— 已知 `@NeedEncode` 走的是
 * gzip → `libContentEncoder.so` 的 `c(byte[])`（本项目已实现），
 * 但实测仍被拦，说明还有缺的环节（可能是额外的请求头或另一层编码）。
 *
 * 结论：**刷分链路目前跑不通，卡在 417**。代码逻辑本身已按算法写好，
 * 等编码口径补齐后即可工作。不假装可用。
 */
object ScorePump {

    /**
     * 知识点遍历时的单次取题超时（毫秒）。
     *
     * 取 4000 与参考项目 `SCAN_TIMEOUT_MS` 同值：扫描阶段的目标是**快速跳过
     * 无效 ID**，超时太长会让 32768 个候选点变成不可接受的等待。
     */
    private const val SCAN_TIMEOUT_MS = 4_000L

    /** 正常取题 / 上传 / 读分数的超时（毫秒），同参考项目的 15s。 */
    private const val OP_TIMEOUT_MS = 15_000L

    /**
     * 本题耗时的随机下限 / 上限（毫秒）。
     *
     * 下限 5ms 是真机实测的边界（此前写 300 是照抄参考项目常量的错误结论），
     * 上限 450ms 仍保留 —— 刷分卷子的耗时不必贴近下限，留一点随机避免整卷同值。
     */
    private const val COST_MIN_MS = 5L
    private const val COST_MAX_MS = 450L

    /**
     * 刷到目标分数。
     *
     * **调用方负责取消**：本函数是 `suspend`，取消请 cancel 承载它的协程
     * （[cn.apixiaoyuan.app.feature.oldsimian.ScorePumpViewModel] 持有 Job）。
     * 取消时抛 [CancellationException]，由调用方识别为「用户停止」。
     *
     * @param keypointId 知识点 ID；空串表示「不知道，直接进扫描」
     * @param limit      每局题目数
     * @param intervalMs 每局之间的等待（毫秒），用于降低请求频率
     * @param target     目标分数（`curWeekScore`）
     * @param onProgress `(当前分数, 已刷局数)`；当前分数为 `-1` 表示正在扫描知识点
     * @param onKeypointFound 扫描到有效知识点时回调（供调用方写回 prefs）
     * @return 成功时返回最终分数；失败时返回异常（取消除外，取消直接抛出）
     */
    suspend fun pumpToTarget(
        keypointId: String,
        limit: Int,
        intervalMs: Long,
        target: Int,
        onProgress: (currentScore: Int, rounds: Int) -> Unit,
        onKeypointFound: (String) -> Unit,
    ): Result<Int> {
        var rounds = 0
        var kp = keypointId.trim()

        val initial = fetchCurrentScore()
            ?: return Result.failure(IllegalStateException("无法读取当前分数（登录态失效或网络异常）"))
        onProgress(initial, 0)
        if (initial >= target) return Result.success(initial)

        while (rounds < OldSimianPrefs.SCORE_MAX_ROUNDS) {
            var exam = if (kp.isEmpty()) null else fetchExam(kp, limit, OP_TIMEOUT_MS)
            if (exam == null) {
                // 知识点无效 / 未知：从 1 遍历到 2^15 找一个能出题的。
                onProgress(-1, rounds)
                val scanned = scanValidKeypoint(limit) ?: return Result.failure(
                    IllegalStateException(
                        "知识点 1~${OldSimianPrefs.SCORE_KEYPOINT_MAX} 均无法取题" +
                            "（网络异常或服务端风控），已刷 $rounds 局",
                    ),
                )
                kp = scanned.first
                exam = scanned.second
                onKeypointFound(kp)
            }

            val examId = exam.idString
                ?: return Result.failure(IllegalStateException("取到的卷子没有 idString，无法上传"))

            val uploaded = upload(examId, fillFullCorrect(exam))
            if (!uploaded) {
                return Result.failure(IllegalStateException("上传第 ${rounds + 1} 局失败，已刷 $rounds 局"))
            }

            rounds++
            val cur = fetchCurrentScore() ?: initial
            onProgress(cur, rounds)
            if (cur >= target) return Result.success(cur)
            if (intervalMs > 0) delay(intervalMs)
        }

        return Result.failure(
            IllegalStateException(
                "达到 ${OldSimianPrefs.SCORE_MAX_ROUNDS} 局上限仍未到目标，当前可能已接近，可再刷一次",
            ),
        )
    }

    /**
     * 全对填充整卷。
     *
     * 与 [cn.apixiaoyuan.app.feature.exercise.ExamViewModel] 的
     * `buildSubmitBody` 在 `autoCorrect = true` 时的口径**逐条一致**：
     *  - 每题 `userAnswer` = 服务端下发的正确答案（`answers.first()`）；
     *  - 每题 `status` = [ExamQuestion.STATUS_RIGHT]；
     *  - 每题 `costTime` = 随机 5~450ms，再过 [OldSimianPrefs.costTimeFor]
     *    （即用户若开了「自定义结算时间」，刷分也沿用同一个值）；
     *  - 每题 `script` = [OralStrokes] 按该题答案生成的笔迹 JSON。
     *    **这里不判断 `strokeEnabled`**：本函数只服务于自动刷分，而原版客户端
     *    提交时必然携带笔迹，空 `script` 反而是异常特征（参考项目也是无条件
     *    `setScript`）。生成失败时保留原 `script`，不写空数组。
     *  - 整卷 `correctCnt` = 题目数、`costTime` = 各题之和。
     *
     * 不在此处做「自定义答案」判定 —— 刷分的语义就是全对。
     */
    private fun fillFullCorrect(exam: ExamData): ExamData {
        var totalCost = 0L
        val questions = exam.questions.orEmpty().map { q ->
            val answer = q.rightAnswer ?: ""
            val base = Random.nextLong(COST_MIN_MS, COST_MAX_MS)
            val cost = OldSimianPrefs.costTimeFor(base)
            totalCost += cost
            q.copy(
                userAnswer = answer,
                status = ExamQuestion.STATUS_RIGHT,
                costTime = cost,
                script = OralStrokes.scriptJson(answer) ?: q.script,
            )
        }
        return exam.copy(
            questions = questions,
            questionCnt = questions.size,
            correctCnt = questions.size,
            costTime = totalCost,
        )
    }

    /**
     * 从 1 遍历到 [OldSimianPrefs.SCORE_KEYPOINT_MAX] 找第一个能出题的知识点。
     *
     * 与参考项目 `scanValidKeypoint` 同语义。每轮用 [SCAN_TIMEOUT_MS] 短超时，
     * 无效 ID 会快速失败（服务端返回 4xx → 被 Repository 收敛成 null）。
     *
     * @return `(知识点ID, 已取到的卷子)`；全部失败返回 null
     */
    private suspend fun scanValidKeypoint(limit: Int): Pair<String, ExamData>? {
        for (id in 1..OldSimianPrefs.SCORE_KEYPOINT_MAX) {
            val exam = fetchExam(id.toString(), limit, SCAN_TIMEOUT_MS) ?: continue
            return id.toString() to exam
        }
        return null
    }

    /**
     * 判断异常是否为「外部取消」（用户点了停止 / 宿主作用域销毁）。
     *
     * **这里是本文件最容易写错的一处**：`withTimeout` 抛出的
     * [TimeoutCancellationException] 是 [CancellationException] 的子类，
     * 如果按「是 CancellationException 就重抛」处理，那么知识点扫描时
     * 第一个 4s 超时就会把整次刷分当作「用户停止」而中断 —— 与参考项目
     * （`CountDownLatch.await(timeout)` 超时后继续下一个候选点）语义不符。
     *
     * 所以：超时 → 返回 false（调用方按「本次操作失败」降级）；真取消 → true。
     * 真取消的判据不是异常类型，而是**外层协程是否还活着** ——
     * `withTimeout` 取消的是它自己创建的子 Job，外层上下文不受影响。
     */
    private suspend fun isExternalCancel(t: Throwable): Boolean {
        if (t !is CancellationException) return false
        if (t is TimeoutCancellationException) {
            // 外层若真被取消，ensureActive() 会在这里抛出，由调用方继续上抛。
            currentCoroutineContext().ensureActive()
            return false
        }
        return true
    }

    /** 取题（失败 / 超时返回 null）。 */
    private suspend fun fetchExam(keypointId: String, limit: Int, timeoutMs: Long): ExamData? =
        try {
            withTimeout(timeoutMs) { ExerciseRepository.fetchExamRaw(keypointId, limit) }
        } catch (t: Throwable) {
            if (isExternalCancel(t)) throw t
            null
        }

    /** 上传（失败 / 超时返回 false）。 */
    private suspend fun upload(examId: String, body: ExamData): Boolean = try {
        withTimeout(OP_TIMEOUT_MS) { ExerciseRepository.uploadExam(body) != null }
    } catch (t: Throwable) {
        if (isExternalCancel(t)) throw t
        false
    }

    /** 读当前分数（`curWeekScore`）；失败返回 null。 */
    private suspend fun fetchCurrentScore(): Int? = try {
        withTimeout(OP_TIMEOUT_MS) { ExerciseRepository.fetchExp()?.curWeekScore }
    } catch (t: Throwable) {
        if (isExternalCancel(t)) throw t
        null
    }
}
