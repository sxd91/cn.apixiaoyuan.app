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
     * 注意首参是 **`J`（primitive long，非空）** —— 与 [smsLogin] 的
     * `Ljava/lang/Long;`（可空）不同。`@Query` 参数类型据此定为非空 `Long`。
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

    /**
     * 阿里一键登录（运营商通道）。
     *
     * smali 签名：
     * `aliLoginWithOperator(ILjava/lang/String;ZLjava/lang/String;Lkotlin/coroutines/Continuation;)`
     *
     * 四个业务参数（逐行来自 `LeoGatewayService.smali`）：
     *  - p1 `I`            -> `@Field("operatorId")`
     *  - p2 `String`       -> `@Field("token")`（非空）
     *  - p3 `Z`            -> `@Field("autoRegister")`
     *  - p4 `String?`      -> `@Field("pMask")` ← **此前未记录的新字段**
     *
     * 注意本方法**没有 `@Query("YFD_U")`** —— 与其余登录方法不同。
     *
     * @param pMask 脱敏标记位（推测）。原版此参可空，未确证语义前按可空落盘。
     */
    @BaseUrl(BASE_LEO)
    @CheckNothing
    @GsonConverter
    @FormUrlEncoded
    @POST("/leo-gateway/android/auth/ali-login-with-operator")
    suspend fun aliLoginWithOperator(
        @Field("operatorId") operatorId: Int,
        @Field("token") token: String,
        @Field("autoRegister") autoRegister: Boolean,
        @Field("pMask") pMask: String?,
    ): LoginResponse

    /**
     * 中国移动一键登录。
     *
     * smali 签名：
     * `cmccLogin(Ljava/lang/String;Ljava/lang/String;ZLjava/lang/String;Lkotlin/coroutines/Continuation;)`
     *
     * 四个业务参数：
     *  - p1 `String`   -> `@Field("token")`
     *  - p2 `String?`  -> `@Field("phone")`
     *  - p3 `Z`        -> `@Field("autoRegister")`
     *  - p4 `String?`  -> `@Field("pMask")`
     */
    @BaseUrl(BASE_LEO)
    @CheckNothing
    @GsonConverter
    @FormUrlEncoded
    @POST("/leo-gateway/android/auth/cmcc")
    suspend fun cmccLogin(
        @Field("token") token: String,
        @Field("phone") phone: String?,
        @Field("autoRegister") autoRegister: Boolean,
        @Field("pMask") pMask: String?,
    ): LoginResponse

    // ---- 四条厂商登录：路径与参数形状已从 smali 逐行确证 ----
    //
    // huaweiLogin   POST /leo-gateway/android/auth/huawei-login
    // honorLogin    POST /leo-gateway/android/auth/honor-login
    // vivoLogin     POST /leo-gateway/android/auth/vivo-login
    // xiaomiLogin   POST /leo-gateway/android/auth/xiaomi-login
    //
    // 四条签名完全一致：
    //   `(Ljava/lang/Long;Ljava/lang/String;ZLkotlin/coroutines/Continuation;)`
    //   p1 Long?   -> @Query("YFD_U")
    //   p2 String? -> @Field("token")
    //   p3 Z       -> @Field("autoRegister")
    // **这四条均无 `pMask`**（与 ali/cmcc 不同，已逐块核对）。
    //
    // 未落盘原因：本工程暂不接厂商一键登录（需要各厂商 SDK 与 AppKey），
    // 落空方法会被接口浏览器当成可用入口，造成误导。路径已记录在此，
    // 待真正接入时按上面形状补。`docs/LOGIN-API.md` 的「待读」标记应更新为已确证。
}
