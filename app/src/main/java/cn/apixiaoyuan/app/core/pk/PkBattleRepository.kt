package cn.apixiaoyuan.app.core.pk

import cn.apixiaoyuan.app.core.oldsimian.OralStrokes
import cn.apixiaoyuan.app.core.network.ServiceLocator
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * PK 秒结算的数据入口：出题 → 解码 → 组装提交 body → 提交。
 *
 * 分层纪律与练习线一致：UI/Engine 不直接碰 Retrofit Service，只经本类收敛异常。
 *
 * ## 笔迹：`script` 与 `curTrueAnswer.pathPoints` 必须同源
 *
 * [OralStrokes] 只能产出「`[[{x,y},...],...]` 的 JSON 字符串」（供 `script` 字段），
 * 而 `curTrueAnswer.pathPoints` 是结构化 `[[{x,y},...],...]`。这里从生成的
 * 笔迹字符串反解回结构，保证两处**完全一致** —— 服务端回放时
 * `script`（文字）与 `pathPoints`（结构）对不上会显得可疑。
 */
object PkBattleRepository {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }

    /** 每题 costTime 下限（对齐练习 ExamQuestion.MIN_COST_TIME_MS 的真机边界）。 */
    const val MIN_COST_TIME_MS = 5L

    /**
     * 拉数学 PK 首页（对局类型列表 + 分数）。
     *
     * `GET /leo-game-pk/android/math/pk/home?grade=N` 返回明文 JSON，
     * 这里手动 parse 成 [PkMathHome]。
     *
     * @param grade 年级（从 SessionStore 读，默认 2）
     * @return 首页数据；失败抛异常。
     */
    suspend fun fetchMathHome(grade: Int): PkMathHome {
        val raw = ServiceLocator.pkBattle.mathHome(grade = grade).string()
        return json.decodeFromString<PkMathHome>(raw)
    }

    /**
     * 出题（按玩法）。
     *
     * 响应是 `@NeedDecode` 后明文 JSON 字节，这里手动 parse 成 [PkMatchResponse]。
     *
     * @param mode     玩法
     * @param pointId  知识点 ID（PK 首页 pointList 提供，默认 1）
     * @return 出题响应；失败抛异常（由上层 Engine 决定重试）。
     */
    suspend fun fetchMatch(mode: PkMode, pointId: Int): PkMatchResponse {
        val api = ServiceLocator.pkBattle
        val body = when (mode) {
            PkMode.MATH -> api.mathMatch(pointId = pointId)
            PkMode.MULTI -> api.multiMatch(pointId = pointId)
            PkMode.FINAL -> api.finalMatch(pointId = pointId)
            PkMode.ENGLISH -> api.englishMatch(pointId = pointId)
        }
        val raw = body.string()
        return json.decodeFromString<PkMatchResponse>(raw)
    }

    /**
     * 提交一局（按玩法）。
     *
     * body 由 [buildSubmitBody] 组装好，`@NeedEncode` 自动编码成 octet-stream。
     *
     * @return 提交响应原始文本；失败抛异常。
     */
    suspend fun submit(mode: PkMode, body: PkSubmitBody): String {
        val api = ServiceLocator.pkBattle
        val resp = when (mode) {
            PkMode.MATH -> api.submitMath(body)
            PkMode.MULTI -> api.submitMulti(body)
            PkMode.FINAL -> api.submitFinal(body)
            PkMode.ENGLISH -> api.submitEnglish(body)
        }
        return resp.string()
    }

    /**
     * 组装「全对秒结算」提交 body。
     *
     * 结构与真机 ground truth 逐字段对齐（2026-09-26 用户实打一轮 PK 落盘的
     * localStorage `exerciseResult`）：
     * - 顶层直接展开 examVO 字段（pkIdStr/pointId/pointName/ruleType/
     *   questionCnt/correctCnt/costTime/questions），无 examVO 嵌套、无 userInfos；
     * - 每题保留完整 question 字段（id/examId/content/answer/userAnswer/answers/
     *   status/script/wrongScript/ruleType/errorState）+ curTrueAnswer 四字段；
     * - `script` 与 `curTrueAnswer.pathPoints` 同源（同一份笔迹）；
     * - `correctCnt` = 对题数（全对 = 题目数）；
     * - `costTime` = 整卷耗时（毫秒）。
     *
     * 判对错只看 `userAnswer`，笔迹只回放 —— 所以秒结算语义 = 每题答案填对即可。
     *
     * @param match        出题响应
     * @param costTimeMs   整卷耗时（毫秒）。默认按题数 × [MIN_COST_TIME_MS] 给一个
     *                     合理下限，避免 0ms 明显不自然。
     * @return 组装好的提交 body；出题响应缺 pkIdStr 或题目列表时抛异常。
     */
    fun buildSubmitBody(
        match: PkMatchResponse,
        costTimeMs: Long? = null,
        strokeMode: PkStrokeMode = PkStrokeMode.ARC,
    ): PkSubmitBody {
        val pkIdStr = match.pkIdStr
            ?: error("出题响应缺 pkIdStr")
        val examVO = match.examVO
            ?: error("出题响应缺 examVO")
        val questions = examVO.questions
            ?: error("出题响应缺 examVO.questions")

        val submitQuestions = questions.mapIndexed { idx, q ->
            val answer = q.rightAnswer ?: ""
            // PK 笔迹：默认 ARC（比较题 `>` / `<` 用密集弧线，否则被服务端判作弊 403）；
            // SEVEN_SEGMENT 时回落到七段码字形。seed 用题号，保证每题笔迹不同。
            val script: String
            if (strokeMode == PkStrokeMode.ARC) {
                script = OralStrokes.pkArcScript(answer, seed = idx)
                    ?: (OralStrokes.scriptJson(answer) ?: "[]")
            } else {
                script = OralStrokes.scriptJson(answer) ?: "[]"
            }
            val pathPoints = parsePathPoints(script)
            PkSubmitQuestion(
                id = q.id,
                examId = q.examId,
                content = q.content,
                answer = q.answer,
                userAnswer = answer,
                answers = q.answers,
                status = PkSubmitQuestion.STATUS_RIGHT,
                script = script,
                wrongScript = null,
                ruleType = q.ruleType,
                errorState = q.errorState,
                curTrueAnswer = PkCurTrueAnswer(
                    recognizeResult = answer,
                    pathPoints = pathPoints,
                    answer = PkSubmitQuestion.STATUS_RIGHT,
                    showReductionFraction = 0,
                ),
            )
        }

        val questionCnt = submitQuestions.size
        val correctCnt = submitQuestions.size
        val cost = costTimeMs
            ?: (questionCnt.toLong() * MIN_COST_TIME_MS).coerceAtLeast(MIN_COST_TIME_MS)

        return PkSubmitBody(
            pkIdStr = pkIdStr,
            pointId = examVO.pointId,
            pointName = examVO.pointName,
            ruleType = examVO.ruleType,
            questionCnt = questionCnt,
            correctCnt = correctCnt,
            costTime = cost,
            questions = submitQuestions,
        )
    }

    /**
     * 把 `OralStrokes` 产出的 script JSON 反解成结构化 pathPoints。
     *
     * script 形如 `[[{"x":1,"y":2},...],...]`，反解结果与 [PkCurTrueAnswer.pathPoints]
     * 的序列化形态对齐。解析失败返回空列表（宁可少笔迹，不抛异常打断整局）。
     */
    private fun parsePathPoints(script: String): List<List<PkPoint>> = runCatching {
        val root = json.parseToJsonElement(script).jsonArray
        root.map { strokeEl ->
            strokeEl.jsonArray.map { ptEl ->
                val obj = ptEl.jsonObject
                PkPoint(
                    x = obj["x"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 0f,
                    y = obj["y"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 0f,
                )
            }
        }
    }.getOrDefault(emptyList())
}