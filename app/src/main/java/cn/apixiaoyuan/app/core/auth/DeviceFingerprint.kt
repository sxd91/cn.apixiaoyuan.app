package cn.apixiaoyuan.app.core.auth

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import java.security.MessageDigest
import java.util.UUID

/**
 * 设备指纹派生值 `YFD_U` 的复刻。
 *
 * 链路（逐行来自 `Lds/i3.smali` 与 `Lkv/f.smali`）：
 * ```
 * YFD_U(long) = Lkv/f;->a( Lds/i3;->c().d() )
 *             = MD5( baseString ) 的前 8 字节大端拼 long
 * ```
 *
 * `Lds/i3;->d()` 的 baseString 拼装（四段用 `|` 连接）：
 * ```
 * 段1 = Build.CPU_ABI + "_" + Build.CPU_ABI2
 * 段2 = Build.HARDWARE + "_" + Build.BOARD + "_" + Build.DEVICE + "_" + Build.PRODUCT
 * 段3 = Build.BRAND + "_" + Build.MODEL + "_" + Build.MANUFACTURER
 * 段4 = "" + null + "_" + Build.SERIAL + "_" + android_id
 * baseString = 段1 + "|" + 段2 + "|" + 段3 + "|" + 段4
 * 指纹 = UUID.nameUUIDFromBytes(baseString.getBytes()).toString()
 * ```
 *
 * **语义修正**：`YFD_U` 不是用户 ID，是**设备级频控键**。服务端按它限制
 * 同一设备的请求频率。原版在未同意隐私协议时 `d()` 返回空串，
 * 此时 `MD5("")` 前 8 字节大端 = `0xd41d8cd98f00b204`（固定常量）。
 * 本工程没有隐私协议开关，直接走「已同意」路径返回真实指纹。
 *
 * **与 `SessionStore.yfdU` 的区别**：
 *  - 本类是**设备维度**（装机就有，与登录无关）—— 登录/发码接口用它
 *  - `SessionStore.yfdU` 是**用户维度**（来自 `userid` cookie，登录后才有）
 *  - 原版 `wo/d.smali` 的登录链用的是本类
 */
object DeviceFingerprint {

    @Volatile
    private var cached: Long? = null

    private var appContext: Context? = null

    /** 由 [cn.apixiaoyuan.app.App] 在 `onCreate` 里注入。 */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /**
     * 取设备指纹派生值。结果缓存 —— 设备信息在进程生命周期内不变。
     *
     * @return MD5 前 8 字节大端拼成的 long
     */
    fun yfdU(): Long = cached ?: compute().also { cached = it }

    /** 复刻 `Lds/i3;->d()` → `Lkv/f;->a()`。 */
    @SuppressLint("HardwareIds", "MissingPermission")
    private fun compute(): Long {
        val baseString = buildString {
            // 段1
            append(Build.CPU_ABI).append('_').append(Build.CPU_ABI2)
            append('|')
            // 段2
            append(Build.HARDWARE).append('_')
                .append(Build.BOARD).append('_')
                .append(Build.DEVICE).append('_')
                .append(Build.PRODUCT)
            append('|')
            // 段3
            append(Build.BRAND).append('_')
                .append(Build.MODEL).append('_')
                .append(Build.MANUFACTURER)
            append('|')
            // 段4：原版是 `"" + null + "_" + SERIAL + "_" + android_id`，
            // 中间那个 null 拼接后就是字符串 "null"，逐字保留。
            append("").append("null").append('_')
                .append(runCatching { Build.SERIAL }.getOrDefault("unknown")).append('_')
                .append(androidId())
        }

        // UUID.nameUUIDFromBytes 等价于 MD5 后按 v3 格式加工；
        // 原版 `Lds/i3;->d()` 返回的正是这个 UUID 字符串。
        val fingerprint = UUID.nameUUIDFromBytes(baseString.toByteArray(Charsets.UTF_8)).toString()

        // `Lkv/f;->a(String)`：MD5(指纹串) → 取前 8 字节大端拼 long
        return md5First8BytesBigEndian(fingerprint)
    }

    /** `Settings.System.getString(cr, "android_id")`。 */
    private fun androidId(): String {
        val ctx = appContext ?: return ""
        return runCatching {
            Settings.System.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull() ?: ""
    }

    /**
     * `Lkv/f;->a(String)` → `Lkv/g;->a` → `Lkv/g;->b`。
     *
     * `Lkv/g;->a`：`getBytes("UTF-8")` → MD5 digest。
     * `Lkv/g;->b`：取 digest 的 byte[0..7]，**大端**拼 long
     * （byte[0] 在最高位，位移量 `(7 - i) * 8`）。
     */
    private fun md5First8BytesBigEndian(input: String): Long {
        val digest = MessageDigest.getInstance("MD5")
            .digest(input.toByteArray(Charsets.UTF_8))

        var result = 0L
        for (i in 0 until 8) {
            val b = (digest[i].toLong() and 0xFFL)
            result = result or (b shl ((7 - i) * 8))
        }
        return result
    }
}
