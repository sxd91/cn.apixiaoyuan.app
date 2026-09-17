package cn.apixiaoyuan.app.core.native

import cn.apixiaoyuan.app.core.network.DecodeBridge
import cn.apixiaoyuan.app.core.network.PayloadDecoder
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream

/**
 * 把 native 解码器装进网络层的 [DecodeBridge]。
 *
 * 网络层的 `DecodeBridge`（`core/network/NeedDecode.kt`）是拦截器读取解码器的
 * 唯一入口 —— 这里只做「用真实 JNI 实现替换恒等实现」这件事，不改动网络层。
 *
 * 调用链（从 `smali_classes3/com/fenbi/android/leo/imgsearch/sdk/utils/y.smali`
 * 逐行读出，非推测）：
 *
 * ```
 * y.a(byte[])                          // 公开入口
 *   -> y.b(byte[])                     // 私有
 *        -> ContentEncoderJNI.c([B)[B  // native: libContentEncoder.so
 *   -> new GZIPInputStream(...)        // b() 之后再走一层 gzip
 * ```
 *
 * `y.a()` 的两个已确证事实：
 *  - 入参为 null 或空数组时直接返回 null（`if-eqz p1, :cond_1`）
 *  - `b()` 之后固定接 `Ljava/util/zip/GZIPInputStream;` 解压
 *
 * 因此真实解码器的顺序与原版一致：先 `c()` 出中间层，再 gzip 解出明文。
 *
 * gzip 解压失败时回退返回 `c()` 的原始输出 —— 不同接口的包装层数可能不同，
 * 硬解失败给出可观测的中间结果，比直接抛异常更适合当前调试阶段。
 *
 * ## 边界
 *
 * `libContentEncoder.so` 位于原版 `imgsearch`（拍照搜题）包下，`c()` 的
 * 字节进字节出形状与 `@NeedDecode` 需求吻合，但**两者是否为同一入口尚未
 * 真机验证**。因此 [install] 只在 native 库加载成功时才替换解码器，加载
 * 失败时保持网络层的恒等实现 —— 上层不会因缺 so 崩溃。
 */
object NativeDecodeInstaller {

    @Volatile
    private var installed = false

    /** 当前是否已装入真实 JNI 解码器。 */
    val isInstalled: Boolean
        get() = installed

    /**
     * 装载。幂等。
     *
     * @return true 表示 `libContentEncoder.so` 加载成功、真实解码器已注册；
     *         false 表示加载失败，`DecodeBridge` 保持恒等实现。
     */
    fun install(): Boolean {
        if (installed) return true

        if (!ContentEncoderJNI.ensureLoaded()) {
            installed = false
            return false
        }

        DecodeBridge.install(NativePayloadDecoder, fromNative = true)
        installed = true
        return true
    }

    /** 卸载，把网络层 `DecodeBridge` 复位回恒等实现。 */
    fun uninstall() {
        DecodeBridge.reset()
        installed = false
    }
}

/**
 * 真实 JNI 解码器：[ContentEncoderJNI.c] 之后接一层 GZIP 解压。
 *
 * 与 `y.smali` 的调用顺序一致。gzip 失败时回退中间层，不抛异常 ——
 * 抛异常会让拦截器把整条响应判为失败，而回退中间层能让调用方看到
 * native 输出的原始字节，便于判断是「native 解错」还是「包装层不同」。
 */
private object NativePayloadDecoder : PayloadDecoder {

    override fun decode(raw: ByteArray): ByteArray {
        val mid = ContentEncoderJNI.c(raw) ?: return raw
        return try {
            GZIPInputStream(mid.inputStream()).use { gz ->
                val out = ByteArrayOutputStream(mid.size.coerceAtLeast(64))
                val buf = ByteArray(8192)
                while (true) {
                    val n = gz.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                }
                out.toByteArray()
            }
        } catch (t: Throwable) {
            // 中间层不是 gzip 包装：回退原生输出，保留可观测性
            mid
        }
    }
}