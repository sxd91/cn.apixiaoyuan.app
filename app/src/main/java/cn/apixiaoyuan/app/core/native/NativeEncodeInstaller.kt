package cn.apixiaoyuan.app.core.native

import cn.apixiaoyuan.app.core.network.EncodeBridge
import cn.apixiaoyuan.app.core.network.PayloadEncoder
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

/**
 * 把 native 编码器装进网络层的 [EncodeBridge]。
 *
 * 与 [NativeDecodeInstaller] 严格对称 —— 后者负责响应解码，本对象负责请求编码。
 * 网络层的 `EncodeBridge`（`core/network/NeedEncode.kt`）是 [NeedEncodeInterceptor]
 * 读取编码器的唯一入口，这里只做「用真实 JNI 实现替换恒等实现」这一件事，
 * 不改动网络层。
 *
 * ## 调用链（原版逐行读出，非推测）
 *
 * `@NeedEncode` 的处理方是 Retrofit 的 `Converter.Factory`（原版 `wp/h.smali`），
 * 它把请求体字节交给 `ds/i4.smali`（`com.fenbi.android.leo.utils.NativeEncryptUtils`）
 * 的 `c([B)` 方法，该方法实现为：
 *
 * ```
 * c(byte[])
 *   -> new ByteArrayOutputStream()
 *   -> new GZIPOutputStream(...)      // 先 gzip 压缩
 *   -> write(明文)
 *   -> toByteArray()
 *   -> f(byte[])                      // 再走 native
 *        -> imgsearch/sdk/utils/e.c([B)[B   // native: libContentEncoder.so
 * ```
 *
 * 即真实编码器的顺序是：**先 gzip 压缩明文，再交给 `ContentBridge.encode()`**。
 * 这与 [NativeDecodeInstaller] 的解码顺序（`c()` 出中间层 → gzip 解压）完全互逆，
 * 两端共用同一个 so。
 *
 * ## 为什么不是 `libRequestEncoder.so`
 *
 * 本对象对应的注解此前一度被误认为走 `libRequestEncoder.so`，该判断已证伪：
 * `libRequestEncoder.so` 的两个 native 方法（`sdwioxccsd` / `zcvsd1wr2t`）的调用方
 * 分别是设备指纹生成（写入 JSON 的 `id` 字段）与 OkHttp 请求头签名拦截器，
 * 属**请求头签名链路**，与请求体编码无关。请求体编码走的是
 * `libContentEncoder.so`，本工程已内置该 so。
 *
 * ## 边界
 *
 * gzip 压缩或 native 调用失败时回退返回**原始明文** —— 让请求以未编码形态发出，
 * 上层不会因缺 so 崩溃。这与解码侧「gzip 失败回退中间层」的取舍一致：
 * 宁可给出可观测的降级行为，也不抛异常打断整条链路。
 */
object NativeEncodeInstaller {

    @Volatile
    private var installed = false

    /** 当前是否已装入真实 JNI 编码器。 */
    val isInstalled: Boolean
        get() = installed

    /**
     * 装载。幂等。
     *
     * @return true 表示 `libContentEncoder.so` 加载成功、真实编码器已注册；
     *         false 表示加载失败，`EncodeBridge` 保持恒等实现。
     */
    fun install(): Boolean {
        if (installed) return true

        if (!ContentBridge.isReady) {
            installed = false
            return false
        }

        EncodeBridge.install(NativePayloadEncoder, fromNative = true)
        installed = true
        return true
    }

    /** 卸载，把网络层 `EncodeBridge` 复位回恒等实现。 */
    fun uninstall() {
        EncodeBridge.reset()
        installed = false
    }
}

/**
 * 真实 JNI 编码器：先 GZIP 压缩明文，再交给 [ContentBridge.encode]。
 *
 * 与 `ds/i4.c([B)` 的调用顺序一致。任一步失败时回退原始明文，不抛异常。
 */
private object NativePayloadEncoder : PayloadEncoder {

    override fun encode(raw: ByteArray): ByteArray {
        if (raw.isEmpty()) return raw

        val compressed = try {
            ByteArrayOutputStream(raw.size.coerceAtLeast(64)).use { out ->
                GZIPOutputStream(out).use { gz -> gz.write(raw) }
                out.toByteArray()
            }
        } catch (t: Throwable) {
            // gzip 失败：直接把明文交给 native，保持链路可观测
            raw
        }

        return ContentBridge.encode(compressed) ?: raw
    }
}
