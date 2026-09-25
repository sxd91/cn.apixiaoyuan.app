package cn.apixiaoyuan.app.core.network.api

import cn.apixiaoyuan.app.core.model.UserAccount
import cn.apixiaoyuan.app.core.network.*
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * 账号域接口（`ape-api.yuanfudao.com`）。
 *
 * 覆盖短信登录、验证码发送、登出等走 `ape-api` 的接口。
 *
 * 方法全部 `suspend` —— Retrofit 的 `suspend` 版本内部切到 IO 线程，
 * 调用方在主线程（[kotlinx.coroutines.Dispatchers.Main]）直接 await 也不会
 * 抛 `NetworkOnMainThreadException`。非 2xx 响应抛 [retrofit2.HttpException]，
 * 传输层失败抛 [java.io.IOException]，都由调用方 catch。
 *
 * 早先这里是 `Call<T>` + `.execute()` 的同步形态，`AuthRepository.sendSmsCode`
 * 在 `viewModelScope` 里调它，真机上直接 `NetworkOnMainThreadException`。
 */
interface YtkApiService {

    /**
     * 短信验证码登录（直连版）。
     *
     * POST `/accounts/android/safe/login`，返回 [UserAccount]（旧版结构）。
     *
     * **与 [LeoGatewayService.smsLogin] 是两条并存通道**：本方法走账号域
     * `ape-api.yuanfudao.com`，网关版走主域 `xyks.yuanfudao.com` 的
     * `/leo-gateway/android/auth/sms`。原版 `wo/d.smali` 走的是本方法，
     * `lp/d$c.smali` 走网关版。
     *
     * **参数事实（逐行来自 `wo/d.smali:630-694`，非注解推断）**：
     * ```
     * v2 = Lkv/k;->b(verification)   // 验证码 RSA 加密
     * v3 = Lkv/k;->b(phone)          // 手机号 RSA 加密
     * v4 = Lkv/f;->a(Lds/i3;->c().d())  // YFD_U = 设备指纹派生
     * v5 = true                      // autoRegister 硬编码 true
     * smsLogin(Long(v4), v3, v2, v5)
     * ```
     * 注解层只写 `@Field`，看不出加密 —— **调用点才是事实**。
     *
     * @param yfdU         `@Query("YFD_U")` —— 设备指纹派生值，可空
     * @param phone        `@Field("phone")` —— **RSA 密文**
     * @param verification `@Field("verification")` —— **RSA 密文**
     * @param autoRegister `@Field("autoRegister")` —— 原版硬编码 true
     */
    @BaseUrl(BASE_YTK)
    @CheckNothing
    @GsonConverter
    @FormUrlEncoded
    @POST("/accounts/android/safe/login")
    suspend fun smsLogin(
        @Query("YFD_U") yfdU: Long?,
        @Field("phone") phone: String,
        @Field("verification") verification: String,
        @Field("autoRegister") autoRegister: Boolean,
    ): UserAccount

    /**
     * 密码登录（直连版，**实测可用**）。
     *
     * POST `/accounts/android/safe/login`，返回 [UserAccount]。
     *
     * ## 为什么是这条而不是 [LeoGatewayService.passwordLogin]
     *
     * 2026-09-25 对真机账号实测：
     *  - 主域网关版 `POST /leo-gateway/android/auth/password`（`@Field` phone/password）
     *    无论密码是明文还是 RSA 密文，**一律 401 `unauthorized`**；
     *  - 本直连版 `POST /accounts/android/safe/login` + **RSA 加密的 password** →
     *    **HTTP 200**，返回完整 `UserAccount`（`id` / `primarySubUserId` / `subUserInfos` …），
     *    并下发 `sess` / `userid` / `g_sess` / `persistent` 四个 cookie。
     *
     * 且明文 password 打到本接口会得到**语义明确的** `401 {"message":"密码错误"}`
     * —— 说明服务端确实在读 `password` 字段，只是要求密文。
     *
     * ## 加密口径（与手机号同一把公钥）
     *
     * `Lkv/k;->b(String)` 的 `ENCRYPT_MODE` 分支：RSA/ECB/PKCS1PADDING + 硬编码公钥 +
     * Base64。本项目 [cn.apixiaoyuan.app.core.auth.PhoneEncoder] 就是这条链路的复刻
     * （`a()` 是加密、`b()` 是解密），所以**直接复用**，不另建类。
     *
     * 原版 `YtkApiService.passwordLoginCall` 的注解层同样只写 `@Field`，
     * 看不出加密 —— 与 [smsLogin] 一样，**调用点/实测才是事实**。
     *
     * @param yfdU     `@Query("YFD_U")` —— 设备指纹派生值
     * @param phone    `@Field("phone")` —— 明文（实测明文可用）
     * @param password `@Field("password")` —— **RSA 密文**
     */
    @BaseUrl(BASE_YTK)
    @CheckNothing
    @GsonConverter
    @FormUrlEncoded
    @POST("/accounts/android/safe/login")
    suspend fun passwordLogin(
        @Query("YFD_U") yfdU: Long,
        @Field("phone") phone: String,
        @Field("password") password: String,
    ): UserAccount

    /**
     * 短信验证码发送（`/verifier/android/sms`）。
     *
     * @param yfdU 设备指纹派生值（原版 `Lds/i3` 指纹串经 MD5 取前 8 字节大端拼 long）。
     * `@Query`，未登录时传 null。
     * @param phone 手机号。**这里是密文** —— 服务端只接受 RSA 编码后的 phone，
     *             明文直接返回 403。
     */
    @BaseUrl(BASE_YTK)
    @CheckNothing
    @GsonConverter
    @FormUrlEncoded
    @POST("/verifier/android/sms")
    suspend fun smsVerify(
        @Query("YFD_U") yfdU: Long? = null,
        @Field("phone") phone: String,
    )

    /**
     * 登出。
     *
     * 服务端清会话。失败不阻塞本地登出，由 [cn.apixiaoyuan.app.core.auth.AuthRepository.logout]
     * 用 `runCatching` 包住。
     */
    @BaseUrl(BASE_YTK)
    @CheckNothing
    @GsonConverter
    @FormUrlEncoded
    @POST("/accounts/android/logout")
    suspend fun logout()
}
