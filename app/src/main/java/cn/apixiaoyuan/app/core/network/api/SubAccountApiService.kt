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
     * 拉子账号列表。
     *
     * GET `/leo-profile/android/user-infos/batchGet`（主域）。
     *
     * Query 是**逗号分隔的 userId 列表** —— 原版把 `UserAccount.subUserInfos`
     * 里拿到的 id 拼成串传进来，一次批量查回全部子账号的 `UserVO`。
     */
    @BaseUrl(BASE_LEO)
    @CheckNothing
    @GsonConverter
    @GET("/leo-profile/android/user-infos/batchGet")
    suspend fun batchGetUserInfos(
        @Query("userIds") userIds: String,
    ): List<UserVO>

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
     * POST `/leo-gateway/android/accounts/switch`（主域）。
     *
     * `@FormUrlEncoded` + `@Field("targetUserId")` —— 注意是 **Field 不是 Query**，
     * 写成 Query 服务端读不到会当成没传。
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
     * 删除子账号（通道 A，账号域直连版）。
     *
     * POST `/accounts/android/directly/deregisterSubUser`。
     *
     * @param deregsiterSubUser       要删的子账号 ID。**字段名就是原版的错拼**。
     * @param targetPrimarySubUserId  主账号 ID
     * @param verification            短信验证码
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
