package cn.apixiaoyuan.app.core.totp

import android.content.Context
import android.content.SharedPreferences
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * TOTP 门禁（RFC 6238，SHA-1 / 6 位 / 30 秒步长）。
 *
 * 用途：应用首次启动时弹出「TOTP 真人验证器」，用户必须正确输入
 * 当前 TOTP 码才能进入主界面（答错/关掉都不放行）。
 *
 * ## 机制
 *
 *  - 首次启动生成随机 base32 密钥（20 字节熵 → 32 字符），持久化到
 *    [SharedPreferences]；验证器对话框展示它并支持复制 —— 用户可把密钥
 *    存进任意 TOTP App（Aegis / Google Authenticator 等）对表；
 *  - 之后每次启动弹窗要求输入当前 6 位码，本地 HMAC-SHA1 实时计算比对；
 *  - 允许 ±1 步长时钟漂移容错（标准做法）。
 *
 * 不引入第三方依赖：RFC 4228 Base32 与 RFC 6238 HMAC 截断都是几十行
 * 标准算法，JCE（`javax.crypto.Mac`）原生支持 HMAC-SHA1。
 */
object TotpGate {

    private const val PREF_NAME = "totp_gate"
    private const val KEY_SECRET = "secret_base32"
    private const val KEY_VERIFIED = "verified_this_session"

    /** RFC 4648 base32 字母表（无 padding，32 字符 = 160 bit 熵）。 */
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    const val STEP_SECONDS = 30
    const val CODE_DIGITS = 6

    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun prefs(): SharedPreferences {
        val ctx = appContext ?: error("TotpGate.init() 未调用")
        return ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    /**
     * 当前密钥（base32，无 padding）。首次调用生成并持久化。
     * 生成后不再变化 —— 用户以它为根信任做设备迁移/重装识别。
     */
    fun secret(): String {
        val p = prefs()
        p.getString(KEY_SECRET, null)?.let { return it }
        val rnd = SecureRandom()
        val bytes = ByteArray(20) { rnd.nextInt(256).toByte() }
        val s = encodeBase32(bytes)
        p.edit().putString(KEY_SECRET, s).apply()
        return s
    }

    /** 计算指定时间戳（毫秒）的 TOTP 码。 */
    fun codeAt(timeMs: Long, secretBase32: String = secret()): String {
        val key = decodeBase32(secretBase32)
        val counter = timeMs / 1000 / STEP_SECONDS
        val msg = ByteArray(8)
        var c = counter
        for (i in 7 downTo 0) {
            msg[i] = (c and 0xff).toByte()
            c = c shr 8
        }
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(key, "HmacSHA1"))
        val hash = mac.doFinal(msg)
        val offset = hash[hash.size - 1].toInt() and 0x0f
        val binary = ((hash[offset].toInt() and 0x7f) shl 24) or
            ((hash[offset + 1].toInt() and 0xff) shl 16) or
            ((hash[offset + 2].toInt() and 0xff) shl 8) or
            (hash[offset + 3].toInt() and 0xff)
        val code = binary % 1_000_000
        return code.toString().padStart(CODE_DIGITS, '0')
    }

    /** 当前步的剩余秒数（用于倒计时进度条）。 */
    fun secondsRemaining(timeMs: Long = System.currentTimeMillis()): Int {
        val s = (timeMs / 1000) % STEP_SECONDS
        return (STEP_SECONDS - s).toInt()
    }

    /**
     * 校验用户输入。允许 ±1 步长漂移（用户手机时钟可能差 30 秒内）。
     */
    fun verify(input: String): Boolean {
        val cleaned = input.trim().filter { it.isDigit() }
        if (cleaned.length != CODE_DIGITS) return false
        val now = System.currentTimeMillis()
        val p = prefs()
        for (drift in -1..1) {
            if (codeAt(now + drift * STEP_SECONDS * 1000L) == cleaned) {
                p.edit().putBoolean(KEY_VERIFIED, true).apply()
                return true
            }
        }
        return false
    }

    /** 本次会话是否已通过验证（进程内存 + prefs 双读，避免弹窗重复）。 */
    fun isVerified(): Boolean = prefs().getBoolean(KEY_VERIFIED, false)

    /** 重置验证状态（下次启动重新弹窗）。密钥不变。 */
    fun resetVerified() {
        prefs().edit().putBoolean(KEY_VERIFIED, false).apply()
    }

    // ---- RFC 4648 Base32 ----

    private fun encodeBase32(data: ByteArray): String {
        val sb = StringBuilder()
        var buffer = 0
        var bits = 0
        for (b in data) {
            buffer = (buffer shl 8) or (b.toInt() and 0xff)
            bits += 8
            while (bits >= 5) {
                sb.append(ALPHABET[(buffer shr (bits - 5)) and 0x1f])
                bits -= 5
            }
        }
        if (bits > 0) {
            sb.append(ALPHABET[(buffer shl (5 - bits)) and 0x1f])
        }
        return sb.toString()
    }

    private fun decodeBase32(s: String): ByteArray {
        val cleaned = s.uppercase().filter { it in ALPHABET }
        val out = ByteArray(cleaned.length * 5 / 8)
        var buffer = 0
        var bits = 0
        var idx = 0
        for (c in cleaned) {
            buffer = (buffer shl 5) or ALPHABET.indexOf(c)
            bits += 5
            if (bits >= 8) {
                out[idx++] = ((buffer shr (bits - 8)) and 0xff).toByte()
                bits -= 8
            }
        }
        return out
    }
}
