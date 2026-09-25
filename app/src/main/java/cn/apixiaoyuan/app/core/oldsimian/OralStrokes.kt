package cn.apixiaoyuan.app.core.oldsimian

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
        '>' to listOf(listOf(0.25f to 0.20f, 0.75f to 0.50f, 0.25f to 0.80f)),
        '<' to listOf(listOf(0.75f to 0.20f, 0.25f to 0.50f, 0.75f to 0.80f)),
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
