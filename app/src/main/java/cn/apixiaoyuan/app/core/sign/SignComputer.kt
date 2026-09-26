package cn.apixiaoyuan.app.core.sign

import android.content.Context
import android.util.Log
import java.io.File

/**
 * 主域请求签名（`sign`）计算器。
 *
 * ## 背景
 *
 * 主域（`xyks.yuanfudao.com`）业务接口要求 URL 带 32 位 MD5 的 `sign` 参数，
 * 缺它一律 417 `x-block-by: solar-encoder`。该值是
 * `Lcom/fenbi/android/leo/utils/e;->zcvsd1wr2t(path, "wdi4n2t8edr", ts)` 的返回值，
 * 即 `libRequestEncoder.so` 内 JNI_OnLoad 动态注册的 native 方法。
 *
 * ## 为什么直接调 so 而不是用 Kotlin 复刻
 *
 * 该 native 方法（内部 chain 函数，so 偏移 `JNI_OnLoad + 0x4078`）的完整形态是：
 *
 * ```
 * s = path + salt
 * d1 = md5hex(s);  s += d1 + path
 * d2 = md5hex(s);  s += d2 + T
 * d3 = md5hex(s);  s += d3 + salt
 * sign = md5hex(s)
 * ```
 *
 * 其中 `T` 由内部函数（so 偏移 `+0x2dc8`）生成，长度 410 字符，内容是
 * **78 次 `ostream << unsigned` 的十进制直接拼接**，取值是 `time()/60` 经
 * 一串 SIMD 浮点除法/位运算得到的混合量（`M//9`、`M//3`、`M+16`、`2^24` …）。
 *
 * 纯 Kotlin 复刻 `T` 需要逐条还原那 78 个表达式，风险高且宿主一旦升级即失效。
 * 而 `libRequestEncoder.so` 本身**不校验调用方**（`JNI_OnLoad` 里注册失败会被
 * 静默吞掉，chain 函数无任何环境依赖，只读入参 + `time()`），因此直接
 * `dlopen` 它并按偏移调用 chain 是精确、稳定且零维护的做法。
 *
 * ## 为什么不走 `System.loadLibrary` + `external fun`
 *
 * `libRequestEncoder.so` 的方法名在 so 内被加密，运行时经 `RegisterNatives`
 * 注册到 `com/yuanfudao/android/leo/stub/SecureStub` —— 该类在本工程不存在，
 * `System.loadLibrary` 会因 `RegisterNatives` 找不到目标类而失败。
 * 因此本类改为 `dlopen` + 直接按偏移调用（见 `sign_jni.cpp`）。
 *
 * ## 偏移的稳健性
 *
 * `+0x4078` 是以 `JNI_OnLoad` 符号地址为基的相对偏移，与 so 在内存中的加载
 * 基址无关，因此 ASLR 下同样成立。已用真机 harness 逐字节验证：
 * `chain("/leo-goblin-mall/android/goods/click-read/display", "wdi4n2t8edr", 0)`
 * 输出 `e4e851febf67146e2db256a78d6ec357`，与真机抓包一致。
 *
 * ## ⚠️ so 版本与偏移强绑定（重要）
 *
 * 偏移**只对当前内置的这一份 so 成立**。`jniLibs` 里此前存在另一份旧版
 * `libRequestEncoder.so`（919,568 字节，md5 `e6a9e427…`），它的 chain 入口是
 * `JNI_OnLoad + 0x406c`（整体比设备版小 0x10 / 0x1c），算法输出与抓包对不上
 * （实测给出 `cdf8c5a2…`，而真机是 `e4e851fe…`）。
 *
 * 现在内置的是**设备实际运行的版本**（919,600 字节，md5 `1d9d8e3be5f9f1511d2862b0b1b0addb`），
 * 它是验证通过的那一份。若将来替换 so，必须重新定位 chain 偏移（方法见
 * `sign_jni.cpp` 头注释），否则 sign 静默算错、全部 417。
 */
object SignComputer {

    private const val TAG = "SignComputer"

    /** 主域签名盐。原版 `ds/c5` 里硬编码，逐字保留。 */
    const val SALT = "wdi4n2t8edr"

    /** 桥接库名（本工程 `cpp/sign_jni.cpp` 编译产物）。 */
    private const val BRIDGE_LIB = "signbridge"

    /** `libRequestEncoder.so` 的 JNI_OnLoad 相对偏移。 */
    private const val CHAIN_OFFSET = 0x4078

    @Volatile
    private var ready = false

    /** 是否已就绪（so 已加载、chain 可调用）。 */
    val isReady: Boolean
        get() = ready

    // ---- native 桥 ----
    private external fun nativeInit(path: String): Boolean
    private external fun nativeReady(): Boolean
    private external fun nativeSign(a: String, b: String, c: Int): String?

    /**
     * 初始化。幂等。
     *
     * @param context 任意 Context，用于定位 `nativeLibraryDir`。
     * @return true 表示桥接库与 `libRequestEncoder.so` 均加载成功。
     */
    fun init(context: Context): Boolean {
        if (ready) return true
        val loaded = try {
            System.loadLibrary(BRIDGE_LIB)
            true
        } catch (t: Throwable) {
            Log.w(TAG, "loadLibrary($BRIDGE_LIB) failed: ${t.message}")
            false
        }
        if (!loaded) return false
        val so = File(context.applicationInfo.nativeLibraryDir, "libRequestEncoder.so")
        if (!so.exists()) {
            Log.w(TAG, "libRequestEncoder.so not found at ${so.absolutePath}")
            return false
        }
        val ok = nativeInit(so.absolutePath) && nativeReady()
        ready = ok
        Log.i(TAG, "init ok=$ok (so=${so.absolutePath}, chainOffset=0x${CHAIN_OFFSET.toString(16)})")
        return ok
    }

    /**
     * 计算主域签名。
     *
     * @param path 请求路径（`url.encodedPath()`，不含 host 与 query）。
     * @param ts   时间偏移秒（原版来自 prefs `time.delta`，默认 0）。
     * @return 32 位小写 hex；未就绪时返回 null。
     */
    fun sign(path: String, ts: Int = 0): String? {
        if (!ready) return null
        return try {
            nativeSign(path, SALT, ts)
        } catch (t: Throwable) {
            Log.w(TAG, "sign failed: ${t.message}")
            null
        }
    }
}