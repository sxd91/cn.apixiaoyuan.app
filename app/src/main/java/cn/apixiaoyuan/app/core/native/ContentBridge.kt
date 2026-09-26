package cn.apixiaoyuan.app.core.native

import android.content.Context
import android.util.Log
import java.io.File

/**
 * `libContentEncoder.so` 的桥接（内容编解码，`([B)[B`）。
 *
 * ## 为什么不用 `System.loadLibrary` + `external fun`
 *
 * 与 `libRequestEncoder.so` 同构：该 so 的 `JNI_OnLoad` 内 `RegisterNatives`
 * 把它唯一的方法注册到类 `com/fenbi/android/leo/imgsearch/sdk/utils/e`，
 * 该类在本工程不存在，`System.loadLibrary` 会因注册失败而抛异常
 * （[cn.apixiaoyuan.app.core.native.NativeEncoders] 里的旧实现就是这种写法，
 * 实际不可用）。
 *
 * ## 静态确证的注册信息（2026-09-26）
 *
 * ```
 * JNI_OnLoad @ 0x1ee2c   → FindClass("com/fenbi/android/leo/imgsearch/sdk/utils/e")
 *                        → RegisterNatives(clazz, methods, 1)
 * methods @ vaddr 0x45bf8（三个 R_AARCH64_RELATIVE）：
 *   name  addend 0x1466f  → "c"
 *   sig   addend 0x13959  → "([B)[B"
 *   fnPtr addend 0x1ecf0  → 函数入口
 * ```
 *
 * `0x1ecf0` 函数体是标准 JNI 形态（`GetArrayLength` → `NewByteArray` →
 * 内部转换 → `SetByteArrayRegion`），因此可以按 JNI 原生 ABI 直接调用。
 *
 * ## 用法
 *
 * ```kotlin
 * ContentBridge.init(context)
 * val out = ContentBridge.encode(rawBytes)   // 编码或解码同一入口（对称）
 * ```
 *
 * 注意：原版里这个 `c([B)[B` 既是编码也是解码的同一入口（对称变换），
 * 请求侧与响应侧共用（见 [cn.apixiaoyuan.app.core.network.NeedEncode] /
 * `NeedDecode` 的 KDoc）。
 */
object ContentBridge {

    private const val TAG = "ContentBridge"

    /** 桥接库名（`cpp/content_jni.cpp` 编译产物，与 sign 共用 signbridge）。 */
    private const val BRIDGE_LIB = "signbridge"

    private const val SO_NAME = "libContentEncoder.so"

    /** 设备上该 so 的字节数，用于校验取到的是正确版本。 */
    private const val SO_SIZE = 298_144L

    @Volatile
    private var ready = false

    val isReady: Boolean get() = ready

    private external fun nativeInit(path: String): Boolean
    private external fun nativeReady(): Boolean
    private external fun nativeEncode(raw: ByteArray): ByteArray?

    /**
     * 初始化。幂等。
     *
     * @param context 任意 Context，用于定位 / 解压 so。
     * @return true 表示桥接库与 `libContentEncoder.so` 均加载成功、函数可调。
     */
    fun init(context: Context): Boolean {
        if (ready) return true
        val libOk = try {
            System.loadLibrary(BRIDGE_LIB)
            true
        } catch (t: Throwable) {
            // 若 sign 侧已加载过同一 bridge，会走 AlreadyLoaded 分支 —— 视作成功。
            Log.w(TAG, "loadLibrary($BRIDGE_LIB): ${t.message}")
            true
        }
        if (!libOk) return false
        val so = NativeSoExtractor.resolve(context, SO_NAME, SO_SIZE) ?: run {
            Log.w(TAG, "$SO_NAME unavailable")
            return false
        }
        val ok = nativeInit(so.absolutePath) && nativeReady()
        ready = ok
        Log.i(TAG, "init ok=$ok (so=${so.absolutePath})")
        return ok
    }

    /**
     * 内容编解码（对称）。
     *
     * @param raw 明文（编码时）或密文（解码时）字节。
     * @return 变换后的字节；未就绪或失败时返回 null。
     */
    fun encode(raw: ByteArray): ByteArray? {
        if (!ready) return null
        return try {
            nativeEncode(raw)
        } catch (t: Throwable) {
            Log.w(TAG, "encode failed: ${t.message}")
            null
        }
    }
}

/**
 * 从 APK 里取出内置 so（AGP 默认 `extractNativeLibs=false`，
 * `nativeLibraryDir` 是空目录，必须自己解压）。
 *
 * 三个候选依次尝试：
 *  1. `nativeLibraryDir` —— `extractNativeLibs=true` 或部分 ROM 会解压到此；
 *  2. `filesDir/native/` 缓存 —— 上次已解压的副本；
 *  3. 从 `sourceDir` / `splitSourceDirs` 的 `lib/arm64-v8a/` 条目解压并缓存。
 *
 * 每步都用 [expectedSize] 校验，避免拿到被截断或版本不符的文件
 * （so 版本与内部偏移强绑定，拿错版本会静默算错）。
 */
internal object NativeSoExtractor {

    fun resolve(context: Context, soName: String, expectedSize: Long): File? {
        val direct = File(context.applicationInfo.nativeLibraryDir, soName)
        if (direct.exists() && direct.length() == expectedSize) return direct

        val cache = File(context.filesDir, "native/$soName")
        if (cache.exists() && cache.length() == expectedSize) return cache

        val sources = buildList {
            context.applicationInfo.sourceDir?.let { add(File(it)) }
            context.applicationInfo.splitSourceDirs?.forEach { add(File(it)) }
        }
        for (apk in sources) {
            if (!apk.exists()) continue
            val out = runCatching {
                java.util.zip.ZipFile(apk).use { zip ->
                    val entry = zip.getEntry("lib/arm64-v8a/$soName") ?: return@use null
                    cache.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        cache.outputStream().use { output -> input.copyTo(output) }
                    }
                    if (cache.length() == expectedSize) cache else null
                }
            }.getOrNull()
            if (out != null) return out
        }
        return null
    }
}