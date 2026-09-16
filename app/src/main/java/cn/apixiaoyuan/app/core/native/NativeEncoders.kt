package cn.apixiaoyuan.app.core.native

/**
 * 三个 native 库的 JNI 声明。
 *
 * 方法名已从原版 smali 逐行读出（非推测）：
 *
 * | 库 | 加载点（smali） | native 方法 |
 * |---|---|---|
 * | `libRequestEncoder.so` | `com/fenbi/android/leo/utils/e` | `sdwioxccsd()String`、`zcvsd1wr2t(String,String,int)String` |
 * | `libContentEncoder.so` | `com/fenbi/android/leo/imgsearch/sdk/utils/e` | `c(byte[])byte[]` |
 * | `libRedressProcess.so` | `CheckRedressJNI`（静态导出） | `resizeWithPad` / `dewarp` / `processPoint` / `complement` |
 *
 * 三个库都放在 `app/src/main/jniLibs/arm64-v8a/`，通过 [NativeBridge] 按需加载。
 *
 * 符号表事实（`readelf -sW`）：
 *  - 前两个库的 `JNI_OnLoad` 是唯一静态导出，实际方法走 RegisterNatives 动态注册
 *  - `libRedressProcess.so` 的四个方法是静态导出，符号名完整
 *
 * **命名说明**：`sdwioxccsd` / `zcvsd1wr2t` 是原版混淆名，逐字保留 ——
 * 改成可读名会让 JNI 查表失败（JNI 按名字精确匹配）。
 */

/**
 * 请求体编码（`libRequestEncoder.so`）。
 *
 * 对应原版 `com.fenbi.android.leo.utils.e`。两个 native 方法：
 *  - [sdwioxccsd]：无参返 String，推测是取密钥 / 盐（命名无信息量，
 *    但签名明确）。**首次调用前必须确保 `loadLibrary` 成功。**
 *  - [zcvsd1wr2t]：三参 `(String, String, Int)` 返 String，推测是主体编码
 *    函数 —— 输入待编码内容、密钥 / 盐、模式编号。
 *
 * 已确证需编码的接口：`postSavedExp`（`@NeedEncode`）。
 *
 * ⚠️ 两个方法的具体语义（哪个参数是什么、返回值怎么用）**未确证** ——
 * 需要读原版调用方的 smali 才能确定。不猜参数含义，只保留签名。
 */
object RequestEncoderJNI {

    @Volatile
    private var loaded = false

    init {
        ensureLoaded()
    }

    /** 加载 `libRequestEncoder.so`。返回是否成功。幂等。 */
    fun ensureLoaded(): Boolean {
        if (loaded) return true
        return try {
            System.loadLibrary("RequestEncoder")
            loaded = true
            true
        } catch (t: Throwable) {
            false
        }
    }

    /**
     * 取密钥 / 盐（推测）。
     *
     * 原版签名：`public static native sdwioxccsd()Ljava/lang/String;`
     *
     * @return 密钥字符串；库未加载时返回 null。
     */
    external fun sdwioxccsd(): String?

    /**
     * 主体编码（推测）。
     *
     * 原版签名：`public static native zcvsd1wr2t(Ljava/lang/String;Ljava/lang/String;I)Ljava/lang/String;`
     *
     * @param content 待编码内容
     * @param key     密钥 / 盐（来自 [sdwioxccsd] 或调用方常量）
     * @param mode    模式编号（原版 Int 枚举，取值未确证）
     * @return 编码后的字符串；库未加载时返回 null。
     */
    external fun zcvsd1wr2t(content: String?, key: String?, mode: Int): String?
}

/**
 * 内容编码 / 解码（`libContentEncoder.so`）。
 *
 * 对应原版 `com.fenbi.android.leo.imgsearch.sdk.utils.e`。一个 native 方法：
 *  - [c]：`byte[]` 进、`byte[]` 出。这是 `@NeedDecode` 的响应体解码入口 ——
 *    输入密文字节，输出明文字节。
 *
 * 该类位于 `imgsearch` 包下（拍照搜题链路），但 `c()` 的字节进字节出形状
 * 与响应解码需求一致，且 `docs/LOGIN-API.md` 里三个 `@NeedDecode` 接口
 * 需要正是这类解码。**具体是否为同一入口，需真机验证。**
 *
 * 静态符号表里可见字符串 `getEncodedP`，可能是方法名或参数名，位置未定。
 */
object ContentEncoderJNI {

    @Volatile
    private var loaded = false

    init {
        ensureLoaded()
    }

    /** 加载 `libContentEncoder.so`。返回是否成功。幂等。 */
    fun ensureLoaded(): Boolean {
        if (loaded) return true
        return try {
            System.loadLibrary("ContentEncoder")
            loaded = true
            true
        } catch (t: Throwable) {
            false
        }
    }

    /**
     * 内容编解码。
     *
     * 原版签名：`public static native c([B)[B`
     *
     * @param raw 输入字节（编码时为明文，解码时为密文）
     * @return 输出字节；库未加载时返回 null。
     */
    external fun c(raw: ByteArray?): ByteArray?
}

/**
 * 试卷拍照矫正（`libRedressProcess.so`）。
 *
 * 四个方法的原生符号逐字读出：
 *  - `Java_com_yuanfudao_android_leo_check_redress_CheckRedressJNI_resizeWithPad`
 *  - `Java_com_yuanfudao_android_leo_check_redress_CheckRedressJNI_dewarp`
 *  - `Java_com_yuanfudao_android_leo_check_redress_CheckRedressJNI_processPoint`
 *  - `Java_com_yuanfudao_android_leo_check_redress_CheckRedressJNI_complement`
 *
 * **这个库与加解密无关**，属于「拍照搜题」链路的图像矫正。
 * 放在这里与另外两个 so 一并管理，但当前不声明 Java 方法 ——
 * 它的参数类型涉及 OpenCV 的 `Mat`，本工程未引入 OpenCV 依赖，
 * 声明了也编译不过。等真正需要拍照矫正功能时再补。
 */
object RedressJNI {

    @Volatile
    private var loaded = false

    /** 加载 `libRedressProcess.so`。返回是否成功。幂等。 */
    fun ensureLoaded(): Boolean {
        if (loaded) return true
        return try {
            System.loadLibrary("RedressProcess")
            loaded = true
            true
        } catch (t: Throwable) {
            false
        }
    }
}