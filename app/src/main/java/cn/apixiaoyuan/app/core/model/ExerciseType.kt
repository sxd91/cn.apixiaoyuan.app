package cn.apixiaoyuan.app.core.model

/**
 * 数学练习出题类型。
 *
 * 逐行来自原版枚举 `smali_classes3/nj/r.smali` —— 这是「10 以内加减法 +
 * 题目数量可选」这条链路的钥匙：
 *
 * ```
 * ORAL("口算练习",   exerciseType=0,  canChooseNum=true,  chooseNumArray=[10,20,30,60,100])
 * KNOWLEDGE_USAGE(1,  "知识运用",   true,  [10,20])
 * COLUMN_METHOD(2,   "竖式计算",   true,  [10,20,60,100])
 * UNIT_CONVERSION(3, "单位换算",   true,  [10,20,30,60,100])
 * AUDIO(4,           "听算练习",   false, [10])
 * VIDEO(5,           "趣味动画",   false, [10])
 * SYNCHRONIZATION(6, "同步练习",   true,  [10,20])
 * PAPER(7,           "试卷集",     false, [10])
 * KEYPOINT(8,        "知识点讲解", false, [10])
 * PAPER_PICTURE(9,   "拍试卷",     false, [10])
 * EASY_WRONG_EXPLAIN(10, "易错题讲解", false, [10])
 * MATH_THOUGHT(11,   "数学思维",   false, [10])
 * ```
 *
 * `exerciseType` 直接作为 `GET /leo-math/android/exams/exercises/type/{type}`
 * 的 Path 参数。原版 `nj/r` 还带 `canPrint` / `frog` / `prefstoreKey` /
 * `iconBgRes` 等打印与埋点字段，与出题链路无关，这里不搬。
 *
 * 「10 以内加减法」= [ORAL]，题量可选 [10, 20, 30, 60, 100]。
 */
enum class ExerciseType(
    /** 传给 `getExercisesKeyPoints` 的 Path `type`。 */
    val exerciseType: Int,
    /** 中文名，逐字对齐原版 `exerciseName`。 */
    val displayName: String,
    /** 是否允许自选题目数量，对齐原版 `canChooseNum`。 */
    val canChooseNum: Boolean,
    /** 可选题目数量，逐值对齐原版 `chooseNumArray`。 */
    val chooseNumArray: List<Int>,
) {
    ORAL(0, "口算练习", true, listOf(10, 20, 30, 60, 100)),
    KNOWLEDGE_USAGE(1, "知识运用", true, listOf(10, 20)),
    COLUMN_METHOD(2, "竖式计算", true, listOf(10, 20, 60, 100)),
    UNIT_CONVERSION(3, "单位换算", true, listOf(10, 20, 30, 60, 100)),
    AUDIO(4, "听算练习", false, listOf(10)),
    VIDEO(5, "趣味动画", false, listOf(10)),
    SYNCHRONIZATION(6, "同步练习", true, listOf(10, 20)),
    PAPER(7, "试卷集", false, listOf(10)),
    KEYPOINT(8, "知识点讲解", false, listOf(10)),
    PAPER_PICTURE(9, "拍试卷", false, listOf(10)),
    EASY_WRONG_EXPLAIN(10, "易错题讲解", false, listOf(10)),
    MATH_THOUGHT(11, "数学思维", false, listOf(10));

    /** 默认题目数量：原版 `chooseNumArray` 第一项。 */
    val defaultNum: Int
        get() = chooseNumArray.first()

    companion object {
        /** 按 `exerciseType` 反查；未知返回 null（对应原版 `isValid()` 的 false 分支）。 */
        fun fromType(type: Int): ExerciseType? =
            entries.firstOrNull { it.exerciseType == type }

        /**
         * 客户端可出题的类型（排除纯展示/打印类）。
         *
         * 原版 `ExerciseOralActivity` 的列表页只铺可练题型；
         * VIDEO（趣味动画）/ KEYPOINT（知识点讲解）/ PAPER_PICTURE（拍试卷）
         * 都是跳别的页面的入口，不在本页出题。
         */
        val practiceable: List<ExerciseType> = listOf(
            ORAL,
            KNOWLEDGE_USAGE,
            COLUMN_METHOD,
            UNIT_CONVERSION,
            AUDIO,
            SYNCHRONIZATION,
            EASY_WRONG_EXPLAIN,
            MATH_THOUGHT,
        )
    }
}
