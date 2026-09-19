package cn.apixiaoyuan.app.core.network.api

import cn.apixiaoyuan.app.core.model.UserAccount
import cn.apixiaoyuan.app.core.network.BASE_YTK
import cn.apixiaoyuan.app.core.network.BaseUrl
import cn.apixiaoyuan.app.core.network.CheckNothing
import cn.apixiaoyuan.app.core.network.GsonConverter
import cn.apixiaoyuan.app.core.network.NotNullAndValid
import retrofit2.Call
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * 账号域 Service（旧版登录 + 短信验证码）。
 *
 * 挂 [BASE_YTK]（`ape-api.yuanfudao.com`）。五个方法逐行来自
 * `smali_classes6/com/yuanfudao/android/leo/login/api/YtkApiService.smali`。
 *
 * 注意两条路径前缀是 `/verifier/` 而不是 `/accounts/` ——
 * 发短信与校验验证码挂在这条链上。
 *
 * 详见 `docs/LOGIN-API.md`。
 */
interface YtkApiService {

    /**
     * 登出。suspend 版，返回 `Unit`。
     */
    @BaseUrl(BASE_YTK)
    @CheckNothing
    @POST("/accounts/android/logout")
    suspend fun logout()

    /**
     * 旧版密码登录。返回 `Call<UserAccount>`（**不是 suspend**）。
     *
     * 新版应走 [LeoGatewayService.passwordLogin]，此条保留兼容。
     */
    @BaseUrl(BASE_YTK)
    @NotNullAndValid
    @GsonConverter
    @FormUrlEncoded
    @POST("/accounts/android/safe/login")
    fun passwordLoginCall(
        @Query("YFD_U") yfdU: Long,
        @Field("phone") phone: String,
        @Field("password") password: String,
    ): Call<UserAccount>

    /**
     * 旧版短信登录。返回 `Call<UserAccount>`。
     */
    @BaseUrl(BASE_YTK)
    @CheckNothing
    @GsonConverter
    @FormUrlEncoded
    @POST("/accounts/android/safe/login")
    fun smsLogin(
        @Query("YFD_U") yfdU: Long?,
        @Field("phone") phone: String?,
        @Field("verification") verification: String?,
        @Field("autoRegister") autoRegister: Boolean,
    ): Call<UserAccount>

    /**
     * 发送短信验证码。
     *
     * **路径前缀是 `/verifier/`，不是 `/accounts/`。**
     *
     * @param yfdU  设备指纹派生值（原版 `Lds/i3` 指纹串经 MD5 取前 8 字节大端拼 long）。
     *              服务端 `@Nullable` —— 首次发码可省略，实测省略不报错；
     *              为 null 时 Retrofit 不拼该 query 参数。
     * @param phone 手机号 **RSA 密文**（`RSA/ECB/PKCS1PADDING` + Base64 NO_WRAP），
     *              由 [cn.apixiaoyuan.app.core.auth.PhoneEncoder.encode] 产出。
     *              服务端对明文 phone 返回 403 `{"status":403,"message":"验证码获取失败"}`；
     *              密文才走通（已实测 200 空体，验证码真下发）。
     */
    @BaseUrl(BASE_YTK)
    @CheckNothing
    @GsonConverter
    @FormUrlEncoded
    @POST("/verifier/android/sms")
    fun smsVerify(
        @Query("YFD_U") yfdU: Long? = null,
        @Field("phone") phone: String,
    ): Call<Void>

    /**
     * 校验验证码（可选步骤，`smsLogin` 前调用）。
     */
    @BaseUrl(BASE_YTK)
    @CheckNothing
    @GsonConverter
    @FormUrlEncoded
    @POST("/verifier/android/validate")
    fun validateVerifyCode(
        @Field("phone") phone: String,
        @Field("verification") verification: String,
    ): Call<Void>
}