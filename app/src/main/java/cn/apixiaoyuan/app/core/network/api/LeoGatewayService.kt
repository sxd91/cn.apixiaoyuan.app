package cn.apixiaoyuan.app.core.network.api

import cn.apixiaoyuan.app.core.model.LoginResponse
import cn.apixiaoyuan.app.core.network.BASE_LEO
import cn.apixiaoyuan.app.core.network.BaseUrl
import cn.apixiaoyuan.app.core.network.CheckNothing
import cn.apixiaoyuan.app.core.network.GsonConverter
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * 登录网关。
 *
 * **挂主域 [BASE_LEO]（`xyks.yuanfudao.com`），不是账号域** —— 名字叫「网关」
 * 容易误导，从 `mg/h.smali` 的方法链与 `sp/n.smali` 的服务定位两处交叉确证。
 *
 * 九个方法全部 `suspend` + `@FormUrlEncoded` + `@GsonConverter` +
 * `@CheckNothing`，返回 [LoginResponse]。方法签名逐行来自
 * `smali_classes6/com/yuanfudao/android/leo/login/api/LeoGatewayService.smali`。
 *
 * 详见 `docs/LOGIN-API.md`。
 */
interface LeoGatewayService {

    /**
     * 密码登录。
     *
     * smali 签名：
     * `passwordLogin(JLjava/lang/String;Ljava/lang/String;Lkotlin/coroutines/Continuation;)`
     *
     * @param yfdU     `@Query("YFD_U")` —— 猿辅导用户 ID。**首次登录传 0**。
     * @param phone    `@Field("phone")`
     * @param password `@Field("password")`
     */
    @BaseUrl(BASE_LEO)
    @CheckNothing
    @GsonConverter
    @FormUrlEncoded
    @POST("/leo-gateway/android/auth/password")
    suspend fun passwordLogin(
        @Query("YFD_U") yfdU: Long,
        @Field("phone") phone: String,
        @Field("password") password: String,
    ): LoginResponse

    /**
     * 短信验证码登录。
     *
     * smali 签名：
     * `smsLogin(Ljava/lang/Long;Ljava/lang/String;Ljava/lang/String;ZLkotlin/coroutines/Continuation;)`
     *
     * @param yfdU         `@Query("YFD_U")` —— 可为 null
     * @param phone        `@Field("phone")`
     * @param verification `@Field("verification")` —— 验证码
     * @param autoRegister `@Field("autoRegister")` —— 未注册时自动注册
     */
    @BaseUrl(BASE_LEO)
    @CheckNothing
    @GsonConverter
    @FormUrlEncoded
    @POST("/leo-gateway/android/auth/sms")
    suspend fun smsLogin(
        @Query("YFD_U") yfdU: Long?,
        @Field("phone") phone: String?,
        @Field("verification") verification: String?,
        @Field("autoRegister") autoRegister: Boolean,
    ): LoginResponse

    /**
     * token 登录（会话续期 / 第三方渠道换登录态）。
     *
     * smali 签名：
     * `tokenLogin(Ljava/lang/Long;Ljava/lang/String;Lkotlin/coroutines/Continuation;)`
     *
     * @param yfdU  `@Query("YFD_U")`
     * @param token `@Field("token")`
     */
    @BaseUrl(BASE_LEO)
    @CheckNothing
    @GsonConverter
    @FormUrlEncoded
    @POST("/leo-gateway/android/auth/token-login")
    suspend fun tokenLogin(
        @Query("YFD_U") yfdU: Long?,
        @Field("token") token: String?,
    ): LoginResponse

    // ---- 五条厂商登录：参数形状已从 smali 确证，但 @POST 路径未读出 ----
    //
    // huaweiLogin / honorLogin / vivoLogin / xiaomiLogin：
    //   @Query("YFD_U") Long?, @Field("token") String?, @Field("autoRegister") Boolean
    // cmccLogin：
    //   @Field("token") String, @Field("phone") String?, @Field("autoRegister") Boolean, + 第四参
    // aliLoginWithOperator：
    //   @Field("operatorId") Int, @Field("token") String, @Field("autoRegister") Boolean, + 第四参
    //
    // 路径未确证前不写死 —— 写了错路径比不写更坏。
    // 确证方法：读 LeoGatewayService.smali 对应方法的 @POST 注解。
}