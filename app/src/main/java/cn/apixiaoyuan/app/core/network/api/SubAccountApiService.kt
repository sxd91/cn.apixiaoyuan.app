package cn.apixiaoyuan.app.core.network.api

import cn.apixiaoyuan.app.core.model.LoginResponse
import cn.apixiaoyuan.app.core.model.UserAccount
import cn.apixiaoyuan.app.core.model.UserVO
import cn.apixiaoyuan.app.core.network.BASE_LEO
import cn.apixiaoyuan.app.core.network.BASE_YTK
import cn.apixiaoyuan.app.core.network.BaseUrl
import cn.apixiaoyuan.app.core.network.CheckNothing
import cn.apixiaoyuan.app.core.network.GsonConverter
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * 子账号（「切换宝贝学习账号」）接口。
 *
 * 五组方法逐行来自两处 smali：
 *  - `smali_classes2/com/fenbi/android/leo/business/user/subaccount/api/YtkAccountsService.smali`
 *  - `smali_classes6/com/yuanfudao/android/leo/login/api/LeoProfileApiService.smali`（batchGet）
 *
 * ## 拼写纪律（**必须逐字照抄，纠正即 404**）
 *
 * 原版有历史拼写错误，服务端按错拼注册，改正拼写会让接口不存在：
 *  - `deregsiterSubUser`（正确是 deregister，原版写 deregsiter）
 *  - `destorySubAccount`（正确是 destroy）
 *  - `registerSonSubUser`（Son 不是 Sun）
 *
 * 这些不是笔误，是**协议的一部分**。
 *
 * ## 删除子账号有三条通道
 *
 * | 通道 | 路径 | 返回 | 备注 |
 * |---|---|---|---|
 * | A | `POST /accounts/android/directly/deregisterSubUser` | `UserAccount` | 账号域，Field 传参 |
 * | B | `POST /leo-gateway/android/accounts/directly/deregisterSubUser` | `LoginResponse` | 主域网关 |
 * | C | `POST /accounts/android/directly/subDeregister` | Unit | Query 传参，见 [YtkUserCenterApiService] |
 *
 * 本项目**只落 A 与 C**：B 是网关版，实测网关版登录接口一律 401
 * （见 [YtkApiService.passwordLogin] 的 KDoc），对本项目无额外价值。
 */
interface SubAccountApiService {

    /**
     * 拉子账号（宝贝学习账号）列表。
     *
     * GET `/leo-profile/android/user-infos/batchGet`（**主域** `leo_base_url`），
     * **无任何参数** —— 直接返回当前账号名下的 `List<UserVO>`。
     *
     * ## 2026-09-26 修正：此前多传了 `userIds`
     *
     * 原版 `LeoProfileApiService`（MT APK MCP 逐行）：
     * ```smali
     * .annotation runtime Lcom/fenbi/android/leo/network/annotations/BaseUrl;
     *     value = "leo_base_url"
     * .method public abstract getSubAccounts(Lkotlin/coroutines/Continuation;)Ljava/lang/Object;
     *     @GET("/leo-profile/android/user-infos/batchGet")
     *     → List<com.yuanfudao.android.leo.user.data.UserVO>
     * .end method
     * ```
     * 方法名是 `getSubAccounts`，**没有 `userIds` 形参**，也没有 `@Query`。
     * 服务端按当前登录态（cookie）决定返回谁的名下账号，客户端不需要、也不该
     * 先有 ID 列表。
     *
     * 此前本接口写成 `@Query("userIds") userIds: String`，其值来自登录响应的
     * `UserAccount.subUserInfos.project2SubUserInfo["6"].subUserIds` —— 而该字段
     * 在 3.141.1 的 dex 里**根本不存在**（`dex_names` / `dex_strings` 双查 0 命中），
     * 于是 ID 列表恒空 → 接口从不被调用 → **小号列表恒空**。
     */
    @BaseUrl(BASE_LEO)
    @CheckNothing
    @GsonConverter
    @GET("/leo-profile/android/user-infos/batchGet")
    suspend fun getSubAccounts(): List<UserVO>

    /**
     * 创建子账号（宝贝学习账号）。
     *
     * POST `/accounts/android/registerSonSubUser`（账号域），**无 body、无 query**。
     *
     * 注意拼写是 `registerSonSubUser`。
     */
    @BaseUrl(BASE_YTK)
    @CheckNothing
    @GsonConverter
    @POST("/accounts/android/registerSonSubUser")
    suspend fun registerSonSubUser(): UserAccount

    /**
     * 切换当前宝贝学习账号。
     *
     * POST `/leo-gateway/android/accounts/switch`（主域），`@FormUrlEncoded` +
     * `@Field("targetUserId")` —— 注意是 **Field 不是 Query**。
     *
     * 逐行来自 `smali_classes2/.../subaccount/api/LeoGatewayService.smali`：
     * `switchSubAccount(I)`，返回 `LoginResponse`。
     */
    @BaseUrl(BASE_LEO)
    @CheckNothing
    @GsonConverter
    @FormUrlEncoded
    @POST("/leo-gateway/android/accounts/switch")
    suspend fun switchAccount(
        @Field("targetUserId") targetUserId: Int,
    ): LoginResponse

    /**
     * 删除子账号（通道 B，主域网关版）。
     *
     * POST `/leo-gateway/android/accounts/directly/deregisterSubUser`。
     *
     * 逐行来自 `smali_classes2/.../subaccount/api/LeoGatewayService.smali` 的
     * `directlyDeregisterSubAccount(J, J, String)`：
     *  - 三个参数**全是 `@Query`**（不是 Field），前两个是 primitive `J`
     *  - 返回 `LoginResponse`（带 `code` 信封）
     *
     * **与通道 A 的区别**：A 走账号域、Field 传参、返回裸 `UserAccount`；
     * B 走主域、Query 传参、返回带业务码的 `LoginResponse`。
     */
    @BaseUrl(BASE_LEO)
    @CheckNothing
    @GsonConverter
    @POST("/leo-gateway/android/accounts/directly/deregisterSubUser")
    suspend fun directlyDeregisterSubAccount(
        @Query("deregsiterSubUser") deregsiterSubUser: Long,
        @Query("targetPrimarySubUserId") targetPrimarySubUserId: Long,
        @Query("verification") verification: String,
    ): LoginResponse

    /**
     * 删除子账号（通道 A，账号域直连版，**实测可用**）。
     *
     * POST `/accounts/android/directly/deregisterSubUser`（**账号域**）。
     *
     * 逐行来自 `smali_classes2/.../subaccount/api/YtkAccountsService.smali` 的
     * `destorySubAccount(IILjava/lang/String;)`：三个参数都是 `@Field`，
     * 前两个是 primitive `I`，返回裸 `UserAccount`。
     *
     * ## `verification` 必须 RSA 加密（2026-09-25 实测确证）
     *
     * 传明文验证码会得到 **403 `{"message":"Decrypt failed, data =000000"}`**
     * —— 服务端在读 `verification` 时**先做 RSA 解密**，拿到明文才继续。
     * 换成 `PhoneEncoder.encode(验证码)` 后该错误消失（变成 500，
     * 因为测试用的是不存在的子账号 ID），**证明加密口径正确、链路打通**。
     *
     * 注意 `deregsiterSubUser` 与 `targetPrimarySubUserId` 两个 ID
     * **不加密**（它们不是「Decrypt failed」指向的字段）。
     *
     * @param deregsiterSubUser       要删的子账号 ID。**字段名就是原版的错拼**。
     * @param targetPrimarySubUserId  主账号 ID
     * @param verification            短信验证码，**调用方须传 RSA 密文**
     */
    @BaseUrl(BASE_YTK)
    @CheckNothing
    @GsonConverter
    @FormUrlEncoded
    @POST("/accounts/android/directly/deregisterSubUser")
    suspend fun deregisterSubUser(
        @Field("deregsiterSubUser") deregsiterSubUser: Int,
        @Field("targetPrimarySubUserId") targetPrimarySubUserId: Int,
        @Field("verification") verification: String,
    ): UserAccount

    /**
     * 删除子账号（通道 C，账号域，Query 传参）。
     *
     * POST `/accounts/android/directly/subDeregister`。
     *
     * 与 [deregisterSubUser] 是**两条并存通道**，服务端都在线。本方法
     * `verification` 与 `account` 都可空 —— 原版有免验证码的调用路径。
     */
    @BaseUrl(BASE_YTK)
    @CheckNothing
    @GsonConverter
    @POST("/accounts/android/directly/subDeregister")
    suspend fun subDeregister(
        @Query("YFD_U") yfdU: Long?,
        @Query("verification") verification: String?,
        @Query("account") account: String?,
    )
}

/**
 * 子账号列表项。
 *
 * 字段来自主域 `/leo-profile/android/user-infos/batchGet` 的真实响应，
 * 与 [cn.apixiaoyuan.app.core.model.UserVO] 同构 —— 这个接口返回的就是
 * `List<UserVO>`。单独定义别名而不是直接用 `List<UserVO>`，是为了在
 * 签名里留下可读的语义名。
 */
typealias SubAccountVO = cn.apixiaoyuan.app.core.model.UserVO
