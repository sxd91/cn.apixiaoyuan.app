package cn.apixiaoyuan.app.core.auth

import android.util.Base64
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher

/**
 * 手机号 RSA 编码器 —— 复刻原版 `Lkv/k;->b(String)String`（Kotlin 参数名 `encodePhone`）。
 *
 * 用途：`/verifier/android/sms` 的 `phone` 字段。**服务端只接受密文**，
 * 明文直接返回 403 `{"status":403,"message":"验证码获取失败"}`。
 *
 * 算法与格式（逐行对照 smali 确证）：
 *  - `Cipher.getInstance("RSA/ECB/PKCS1PADDING", "BC")`，`ENCRYPT_MODE`
 *  - `doFinal(phone.getBytes("UTF-8"))`
 *  - `Lkv/a;->f([B, 2)` → `Base64.encodeToString(bytes, Base64.NO_WRAP)`
 *    （flag 2 = `NO_WRAP`，不换行，末尾也不补 `\n`）
 *
 * PKCS#1 v1.5 随机填充，所以**同一手机号每次加密结果不同**，服务端解密正常。
 * 这是预期行为，不是 bug。
 *
 * 公钥来自原版 `Lkv/k;->a` 的硬编码 X509 SubjectPublicKeyInfo Base64。
 * 与后续 `/verifier/android/validate`、`/accounts/android/safe/login` 无关
 * ——那两个接口的 `phone` 是**明文** `@Field`，不走这里。
 *
 * Android 自带 BouncyCastle（provider 名 `"BC"`），无需额外依赖。
 */
object PhoneEncoder {

    /**
     * 原版硬编码公钥（X509 SubjectPublicKeyInfo，DER 的 Base64）。
     * 来源：`apktool_out/smali_classes?/kv/k.smali` 的静态字段 `a`。
     */
    private const val PUBLIC_KEY_BASE64: String =
        "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDSovT1rrwzrGoMCFb6z8e+5lzVdAD5o8krGIwdfxrV" +
            "E2OnMijUZdkQk7etPJvZ2JOVXghthAGUUJkDUE8n2ZMNFKPjMrQJI49ewVzqWOKOvgU6Iu60Sn0xpei" +
            "etP1wWXBkszdV1WfNBJUo2hhPDnIPMGzzdfLW5rMu+tczeUriJQIDAQAB"

    /** 公钥只解析一次。首次失败即抛，不做静默降级。 */
    private val publicKey by lazy {
        val der = Base64.decode(PUBLIC_KEY_BASE64, Base64.DEFAULT)
        KeyFactory.getInstance("RSA", "BC").generatePublic(X509EncodedKeySpec(der))
    }

    /**
     * 把明文手机号编码成接口要的密文串。
     *
     * @param phone 11 位明文手机号
     * @return Base64（NO_WRAP）密文
     * @throws IllegalStateException 加密失败。不静默回退明文 —— 传明文必然 403，
     *         静默回退只会把真实原因藏起来，更难排查。
     */
    fun encode(phone: String): String = try {
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding", "BC")
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)
        val cipherBytes = cipher.doFinal(phone.toByteArray(Charsets.UTF_8))
        Base64.encodeToString(cipherBytes, Base64.NO_WRAP)
    } catch (t: Throwable) {
        throw IllegalStateException("手机号 RSA 编码失败：${t.javaClass.simpleName}: ${t.message}", t)
    }
}
