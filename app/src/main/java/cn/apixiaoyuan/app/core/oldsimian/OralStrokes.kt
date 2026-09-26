package cn.apixiaoyuan.app.core.oldsimian

import kotlin.random.Random

/**
 * 「提交画笔」的字形库 —— 纯 Kotlin 实现，**替代 cn.nizou.sxd 的 libauto_oral.so**。
 *
 * ## 为什么不用 libauto_oral.so
 *
 * 参考项目 cn.nizou.sxd 的笔迹生成走 `util/Strokes.kt` → `String.nativeStrokes`
 * （`external get`），背后是 Rust(jni crate) 编译的 `libauto_oral.so`：
 *
 *  - JNI 符号与 `cn.nizou.sxd` 的包名/类名绑定，直接搬过来加载必然 `UnsatisfiedLinkError`；
 *  - 它是 LSPosed 模块里「注入宿主进程」用的，本项目是**内置客户端**，没有宿主进程；
 *  - 该库的加载还要处理 `extractNativeLibs=false` 时从模块 APK 解压 .so 的兼容分支
 *    （见 cn.nizou.sxd `extractLibFromApk`），在内置架构下纯属多余复杂度。
 *
 * 所以这里用**纯 Kotlin 字形库**重写：把答案文本按字符切分，每个字符查表得到
 * 归一化（0..1）笔画折线，再按格子缩放，输出服务端要的 `script` JSON。
 *
 * ## 字形来源
 *
 * 坐标逐值取自 cn.nizou.sxd `assets/js/quick.js` 的 `GLYPHS` 表（该表是
 * 2026-08-29 真机验证过、能在 H5 canvas 上被前端 `recognize` 认出的形状）。
 * 与 JS 版的区别只有一处：**JS 版 `'4'`/`'9'`/`'*'` 的后续笔画被写成了裸点
 * `[x, y]` 而不是点数组**（`dispatchStroke` 读 `pts[i][0]` 会得到 `undefined`，
 * 属于原实现的容错遗漏）；这里统一规范成「每个字符 = 若干条折线，每条折线 = 若干点」。
 *
 * ## 坐标语义
 *
 * 归一化坐标（相对字符所在格子的左上角，0..1）。缩放到目标尺寸由调用方传
 * `cellWidth` / `cellHeight` 决定 —— 与 quick.js `drawAnswer` 里
 * `cellW = rect.width / n`、`sx = cellW * 0.72` 的口径一致（0.72 / 0.78 是
 * 真机调出来的内缩系数，避免笔画贴边被裁）。
 */
object OralStrokes {

    /** 单个点（归一化坐标）。 */
    private typealias Pt = Pair<Float, Float>

    /** 一条折线 = 一串点。 */
    private typealias Stroke = List<Pt>

    /** 一个字符 = 若干条折线。 */
    private typealias Glyph = List<Stroke>

    /** 字形在格子内的横向内缩系数（同 quick.js `sx = cellW * 0.72`）。 */
    private const val INSET_X = 0.72f

    /** 字形在格子内的纵向内缩系数（同 quick.js `sy = cellH * 0.78`）。 */
    private const val INSET_Y = 0.78f

    /**
     * 字形表。
     *
     * 逐值来自 cn.nizou.sxd `assets/js/quick.js` 的 `GLYPHS`。
     * 覆盖：数字 0-9、`+ - * × / = < > . : ?` 与空格。
     */
    private val GLYPHS: Map<Char, Glyph> = mapOf(
        '0' to listOf(
            listOf(
                0.35f to 0.12f, 0.60f to 0.12f, 0.74f to 0.22f, 0.80f to 0.40f,
                0.80f to 0.60f, 0.74f to 0.80f, 0.60f to 0.90f, 0.40f to 0.90f,
                0.26f to 0.80f, 0.20f to 0.60f, 0.20f to 0.40f, 0.26f to 0.22f,
                0.35f to 0.12f,
            ),
        ),
        '1' to listOf(
            listOf(0.42f to 0.35f, 0.52f to 0.15f, 0.56f to 0.20f, 0.56f to 0.85f),
        ),
        '2' to listOf(
            listOf(
                0.20f to 0.35f, 0.24f to 0.20f, 0.45f to 0.12f, 0.66f to 0.22f,
                0.72f to 0.40f, 0.50f to 0.58f, 0.25f to 0.75f, 0.70f to 0.85f,
                0.78f to 0.90f,
            ),
        ),
        '3' to listOf(
            listOf(
                0.24f to 0.18f, 0.55f to 0.10f, 0.70f to 0.25f, 0.62f to 0.42f,
                0.38f to 0.45f, 0.68f to 0.55f, 0.74f to 0.72f, 0.60f to 0.88f,
                0.30f to 0.88f,
            ),
        ),
        // 竖 + 横折（JS 版后三点写成裸点，这里规范为一条折线）。
        '4' to listOf(
            listOf(0.58f to 0.10f, 0.58f to 0.85f),
            listOf(0.30f to 0.55f, 0.32f to 0.42f, 0.72f to 0.42f),
        ),
        '5' to listOf(
            listOf(
                0.22f to 0.14f, 0.70f to 0.14f, 0.70f to 0.40f, 0.30f to 0.42f,
                0.24f to 0.55f, 0.30f to 0.75f, 0.50f to 0.88f, 0.70f to 0.85f,
                0.78f to 0.72f,
            ),
        ),
        '6' to listOf(
            listOf(
                0.35f to 0.15f, 0.65f to 0.20f, 0.76f to 0.40f, 0.74f to 0.70f,
                0.60f to 0.88f, 0.40f to 0.88f, 0.26f to 0.70f, 0.24f to 0.50f,
                0.40f to 0.40f, 0.62f to 0.45f,
            ),
        ),
        '7' to listOf(
            listOf(0.20f to 0.15f, 0.75f to 0.15f, 0.50f to 0.40f, 0.45f to 0.85f),
        ),
        '8' to listOf(
            listOf(
                0.30f to 0.12f, 0.62f to 0.20f, 0.68f to 0.38f, 0.50f to 0.50f,
                0.30f to 0.50f, 0.25f to 0.35f, 0.40f to 0.28f, 0.62f to 0.38f,
                0.70f to 0.60f, 0.65f to 0.80f, 0.45f to 0.90f, 0.28f to 0.82f,
                0.25f to 0.60f, 0.45f to 0.50f,
            ),
        ),
        // 圆 + 尾钩（JS 版尾钩写成裸点，这里规范为折线）。
        '9' to listOf(
            listOf(
                0.40f to 0.12f, 0.62f to 0.20f, 0.72f to 0.40f, 0.70f to 0.60f,
                0.55f to 0.75f, 0.38f to 0.72f, 0.30f to 0.55f, 0.45f to 0.45f,
                0.65f to 0.50f,
            ),
            listOf(0.52f to 0.88f, 0.50f to 0.85f),
        ),
        '+' to listOf(
            listOf(0.50f to 0.20f, 0.50f to 0.80f),
            listOf(0.20f to 0.50f, 0.80f to 0.50f),
        ),
        '-' to listOf(listOf(0.20f to 0.50f, 0.80f to 0.50f)),
        '×' to listOf(
            listOf(0.25f to 0.20f, 0.75f to 0.80f),
            listOf(0.75f to 0.20f, 0.25f to 0.80f),
        ),
        'x' to listOf(
            listOf(0.25f to 0.20f, 0.75f to 0.80f),
            listOf(0.75f to 0.20f, 0.25f to 0.80f),
        ),
        'X' to listOf(
            listOf(0.25f to 0.20f, 0.75f to 0.80f),
            listOf(0.75f to 0.20f, 0.25f to 0.80f),
        ),
        // JS 版后四条写成裸点，这里规范为「竖 + 横 + 两斜」。
        '*' to listOf(
            listOf(0.50f to 0.20f, 0.50f to 0.80f),
            listOf(0.20f to 0.50f, 0.80f to 0.50f),
            listOf(0.30f to 0.30f, 0.70f to 0.70f),
            listOf(0.70f to 0.30f, 0.30f to 0.70f),
        ),
        '/' to listOf(listOf(0.25f to 0.15f, 0.75f to 0.85f)),
        '=' to listOf(
            listOf(0.20f to 0.35f, 0.80f to 0.35f),
            listOf(0.20f to 0.65f, 0.80f to 0.65f),
        ),
        '>' to listOf(
            listOf(
                0.3004f to 0.0000f, 0.3662f to 0.0088f, 0.4594f to 0.0212f, 0.5636f to 0.0347f,
                0.6787f to 0.0488f, 0.7939f to 0.0612f, 0.8893f to 0.0718f, 0.9496f to 0.0781f,
                1.0000f to 0.1076f, 0.9496f to 0.2098f, 0.9046f to 0.2664f, 0.8355f to 0.3398f,
                0.7467f to 0.4272f, 0.6524f to 0.5158f, 0.5592f to 0.6023f, 0.4649f to 0.6860f,
                0.3739f to 0.7633f, 0.2862f to 0.8330f, 0.1985f to 0.8917f, 0.1151f to 0.9448f,
                0.0000f to 1.0000f,
            ),
        ),
        '<' to listOf(
            listOf(
                0.2020f to 0.0000f, 0.2636f to 0.0000f, 0.3293f to 0.0000f, 0.4051f to 0.0017f,
                0.5030f to 0.0073f, 0.6162f to 0.0168f, 0.7273f to 0.0289f, 0.8202f to 0.0379f,
                0.8848f to 0.0432f, 0.9566f to 0.0565f, 1.0000f to 0.1009f, 0.9586f to 0.1893f,
                0.9212f to 0.2388f, 0.8525f to 0.3139f, 0.7424f to 0.4197f, 0.6192f to 0.5337f,
                0.5071f to 0.6340f, 0.4081f to 0.7155f, 0.3253f to 0.7788f, 0.2556f to 0.8309f,
                0.2040f to 0.8704f, 0.1556f to 0.9021f, 0.0697f to 0.9551f, 0.0000f to 1.0000f,
            ),
        ),
        '.' to listOf(listOf(0.45f to 0.70f, 0.55f to 0.70f)),
        ':' to listOf(
            listOf(0.50f to 0.30f, 0.50f to 0.35f),
            listOf(0.50f to 0.65f, 0.50f to 0.70f),
        ),
        '?' to listOf(
            listOf(
                0.25f to 0.35f, 0.30f to 0.20f, 0.50f to 0.12f, 0.68f to 0.25f,
                0.60f to 0.42f, 0.45f to 0.50f, 0.45f to 0.65f,
            ),
            listOf(0.45f to 0.82f, 0.45f to 0.85f),
        ),
        ' ' to emptyList(),
    )

    /**
     * 把答案文本转成笔迹折线集合（已按格子缩放，坐标为像素）。
     *
     * 布局与 quick.js `drawAnswer` 一致：文本按字符横向平分画布宽度，
     * 每个字符在自己的格子里居中绘制。
     *
     * @param answer     答案文本，如 `78`、`+`、`12`
     * @param width      可绘制区域宽度（像素）
     * @param height     可绘制区域高度（像素）
     * @param originX    可绘制区域左上角 X（像素）
     * @param originY    可绘制区域左上角 Y（像素）
     * @return 折线列表；无法识别的字符（不在字形表里）会被跳过
     */
    fun buildStrokes(
        answer: String,
        width: Float,
        height: Float,
        originX: Float = 0f,
        originY: Float = 0f,
    ): List<Stroke> {
        if (answer.isEmpty() || width <= 0f || height <= 0f) return emptyList()
        val chars = answer.toCharArray()
        val cellW = width / chars.size
        val cellH = height
        val sx = cellW * INSET_X
        val sy = cellH * INSET_Y
        val result = mutableListOf<Stroke>()
        chars.forEachIndexed { ci, ch ->
            val glyph = GLYPHS[ch] ?: return@forEachIndexed
            val ox = originX + cellW * ci + (cellW - sx) / 2f
            val oy = originY + (cellH - sy) / 2f
            glyph.forEach { stroke ->
                result += stroke.map { (nx, ny) -> (ox + nx * sx) to (oy + ny * sy) }
            }
        }
        return result
    }

    /**
     * 生成练习提交用的 `script` 字段值（JSON 字符串）。
     *
     * 格式与 cn.nizou.sxd `util/Strokes.kt` 的 `List<Array<*>>.toJsonString()`
     * **逐字段对齐** —— 那个函数把笔迹序列化成
     * `[[{"x":1.0,"y":2.0},{"x":..,"y":..}], ...]`（外层数组 = 笔画，内层 = 点）。
     * 服务端读的就是这个结构（原版 `QuestionVO.script` 是笔迹 JSON 字符串）。
     *
     * 坐标用归一化基准（0..1 乘以 1000），避免依赖真机画布尺寸：
     * 服务端只做笔迹回放展示，不参与判分（判分看 `userAnswer` / `status`）。
     *
     * @param answer 答案文本
     * @return JSON 字符串；答案为空或全是不认识字符时返回 `null`
     *         （调用方应保留原 `script`，不要写空数组 —— 空笔迹比无笔迹更可疑）
     */
    fun scriptJson(answer: String): String? {
        val strokes = buildStrokes(
            answer = answer,
            width = CANVAS_SIZE,
            height = CANVAS_SIZE,
        )
        if (strokes.isEmpty()) return null
        return buildString {
            append('[')
            strokes.forEachIndexed { si, stroke ->
                if (si > 0) append(',')
                append('[')
                stroke.forEachIndexed { pi, (x, y) ->
                    if (pi > 0) append(',')
                    append("{\"x\":").append(trimFloat(x))
                    append(",\"y\":").append(trimFloat(y)).append('}')
                }
                append(']')
            }
            append(']')
        }
    }

    // =====================================================================
    // PK 弧线笔迹（2026-09-26 本地验证通过：密集弧线 200，稀疏点 403）
    // =====================================================================
    //
    // PK 提交的笔迹与练习不同：练习判分只看 userAnswer，笔迹只回放；但 PK 的
    // 服务端会校验笔迹「像不像真人手写」。七段码里的 `>` / `<` 只有 3 个点
    // （稀疏折线），被判作弊 → 403；换成 20+ 个密集点、含自然转折的弧线后
    // 提交 200。坐标口径与真机 ground truth 一致（像素级，x 约 150..270、
    // y 约 450..560），不是归一化 0..1。

    /** PK 弧线模板：`<` 形（左上→中→右上，然后折回）。相对第一点。 */
    private val PK_ARC_LT: List<Pair<Float, Float>> = listOf(
        0.0f to 0.0f, 6.1f to 0.0f, 12.6f to 0.0f, 20.1f to 0.13f, 29.8f to 0.56f,
        41.0f to 1.28f, 52.0f to 2.21f, 61.2f to 2.89f, 67.6f to 3.30f, 74.7f to 4.31f,
        79.0f to 7.70f, 74.9f to 14.45f, 71.2f to 18.23f, 64.4f to 23.96f, 53.5f to 32.04f,
        41.3f to 40.74f, 30.2f to 48.40f, 20.4f to 54.62f, 12.2f to 59.45f, 5.3f to 63.43f,
        0.2f to 66.45f, -4.6f to 68.87f, -13.1f to 72.91f, -20.0f to 76.34f,
    )

    /** PK 弧线模板：`>` 形（右上→中→左上，然后折回）。相对第一点。 */
    private val PK_ARC_GT: List<Pair<Float, Float>> = listOf(
        0.0f to 0.0f, 6.0f to 0.67f, 14.5f to 1.62f, 24.0f to 2.65f, 34.5f to 3.73f,
        45.0f to 4.68f, 53.7f to 5.49f, 59.2f to 5.97f, 63.8f to 8.23f, 59.2f to 16.04f,
        55.1f to 20.37f, 48.8f to 25.98f, 40.7f to 32.66f, 32.1f to 39.44f, 23.6f to 46.05f,
        15.0f to 52.45f, 6.7f to 58.36f, -1.3f to 63.69f, -9.3f to 68.18f, -16.9f to 72.24f,
        -27.4f to 76.46f,
    )

    /**
     * 生成 PK 提交用的弧线笔迹（像素坐标，像真人手写）。
     *
     * 与 pk_arc.py 的 `make_path` 完全对齐：模板 + 每个点 ±1.5 手抖 + 整体偏移，
     * 让每题笔迹都不同但都像真人。PK 的 `>` / `<` 比较题只有这两种符号，
     * 其它符号（数字等）仍回落到 [scriptJson] 的七段码字形。
     *
     * @param answer 答案文本（PK 比大小题通常是 `>` 或 `<`）
     * @param seed   随机种子（同一题 seed 固定则笔迹可复现）
     * @return 弧线笔迹点集（一笔 = 一个点数组）；非 `>` / `<` 返回 null，调用方回落。
     */
    fun pkArcPathPoints(answer: String, seed: Int): List<List<Pair<Float, Float>>>? {
        val tmpl = when (answer.trim()) {
            "<" -> PK_ARC_LT
            ">" -> PK_ARC_GT
            else -> return null
        }
        val rnd = Random(seed)
        // 整体偏移（每题位置不同），与 pk_arc.py 同区间。
        val ox = rnd.nextFloat() * 90f + 150f   // 150..240
        val oy = rnd.nextFloat() * 50f + 450f   // 450..500
        val pts = tmpl.map { (dx, dy) ->
            val x = ox + dx + (rnd.nextFloat() * 3f - 1.5f)
            val y = oy + dy + (rnd.nextFloat() * 3f - 1.5f)
            x to y
        }
        return listOf(pts)
    }

    /** 把 PK 弧线笔迹点集序列化成 `[[{"x":..,"y":..},...]]` 的 JSON 字符串。 */
    fun pkArcScript(answer: String, seed: Int): String? {
        val strokes = pkArcPathPoints(answer, seed) ?: return null
        return buildString {
            append('[')
            strokes.forEachIndexed { si, stroke ->
                if (si > 0) append(',')
                append('[')
                stroke.forEachIndexed { pi, (x, y) ->
                    if (pi > 0) append(',')
                    append("{\"x\":").append(trimFloat4(x))
                    append(",\"y\":").append(trimFloat4(y)).append('}')
                }
                append(']')
            }
            append(']')
        }
    }

    /** 四舍五入到 4 位小数（与 pk_arc.py 的 `round(x,4)` 对齐）。 */
    private fun trimFloat4(v: Float): String {
        val rounded = Math.round(v * 10000f) / 10000f
        return if (rounded == rounded.toLong().toFloat()) {
            rounded.toLong().toString()
        } else {
            rounded.toString()
        }
    }

    /**
     * 提交笔迹的归一化画布边长。
     *
     * 取 1000 是为了让小数位在 JSON 里保持 3 位精度、又不产生 `0.1+0.2` 那种长尾浮点。
     * 真机若发现服务端对 `script` 有具体尺寸要求，改这一个常量即可（不影响字形比例）。
     */
    private const val CANVAS_SIZE = 1000f

    /** 去掉浮点尾巴：`12.0` → `12`，`12.345` → `12.345`。 */
    private fun trimFloat(v: Float): String {
        val rounded = Math.round(v * 1000f) / 1000f
        return if (rounded == rounded.toLong().toFloat()) {
            rounded.toLong().toString()
        } else {
            rounded.toString()
        }
    }
}
