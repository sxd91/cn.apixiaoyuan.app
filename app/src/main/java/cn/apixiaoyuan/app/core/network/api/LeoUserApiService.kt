package cn.apixiaoyuan.app.core.network.api

import cn.apixiaoyuan.app.core.model.ParentCertificationData
import cn.apixiaoyuan.app.core.model.UserPendantVO
import cn.apixiaoyuan.app.core.model.UserVipVO
import cn.apixiaoyuan.app.core.model.UserVO
import cn.apixiaoyuan.app.core.network.BASE_LEO
import cn.apixiaoyuan.app.core.network.BaseUrl
import cn.apixiaoyuan.app.core.network.CheckNothing
import cn.apixiaoyuan.app.core.network.GsonConverter
import cn.apixiaoyuan.app.core.network.NotNullAndValid
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Query

/**
 * 主域用户服务（扩展版，比 [LeoProfileApiService] 覆盖更广）。
 *
 * 挂主域 [BASE_LEO]（`xyks.yuanfudao.com`）。十二个方法逐行来自
 * `smali_classes3/com/fenbi/android/leo/network/LeoUserApiService.smali`。
 *
 * 与 [LeoProfileApiService] 的关系：后者只有 5 个方法（用户资料 + 设置），
 * 本类多出 7 条——设备注册、VIP 信息、头像挂件、手机号更换、图片上传等。
 * 两者路径不重叠，可共存；[LeoProfileApiService] 更早落盘、命名更贴近
 * 「资料」，本类补全原版 `LeoUserApiService` 的完整方法面。
 *
 * 路径分组：
 *  - `/leo-profile/` —— 用户资料、身份证、手机号更换
 *  - `/leo-auth/` —— 设备注册
 *  - `/leo-alchemy-account/` —— VIP、头像挂件
 *  - `/leo-exam/` —— 试卷免费试用
 *  - `/leo-gallery/` —— 图片上传
 *
 * **关键实现点**：
 *  - `register` 与 `getUserInfo`、`updateUserInfoLegacy`、`uploadImageLegacy`
 *    是旧版 `Call` 返回；其余是 `suspend`。
 *  - `uploadImage` 收 `MultipartBody.Part`，走多部分表单。
 *  - `getUserPendants` 的 `type: Int` 与 `submitUserPendant` 的
 *    `userPendantId: Int` + `type: Int` 都是 Query 参数。
 *
 * 详见 `docs/API-INVENTORY.md`。
 */
interface LeoUserApiService {

    /**
     * 注册设备（旧版 `Call`）。
     *
     * POST `/leo-auth/android/user-devices`，
     * Query `device` + `deviceInfo`，返回 `Void`。
     */
    @BaseUrl(BASE_LEO)
    @CheckNothing
    @GsonConverter
    @POST("/leo-auth/android/user-devices")
    fun register(
        @Query("device") device: String,
        @Query("deviceInfo") deviceInfo: String,
    ): Call<Void>

    /**
     * 拉用户资料（旧版 `Call`）。
     *
     * GET `/leo-profile/android/user-infos`，返回 `UserVO`。
     *
     * 注意与 [LeoProfileApiService.getUserInfo] 同路径但返回类型不同——
     * 后者是 `suspend` 直返 `UserVO`，本方法是 `Call<UserVO>`。两条并存
     * 是原版历史遗留，新代码优先用 suspend 版。
     */
    @BaseUrl(BASE_LEO)
    @CheckNothing
    @GsonConverter
    @GET("/leo-profile/android/user-infos")
    fun getUserInfo(): Call<UserVO>

    /**
     * 关闭家长认证。
     *
     * GET `/leo-profile/android/user-id-card/delete`。
     */
    @BaseUrl(BASE_LEO)
    @CheckNothing
    @GsonConverter
    @GET("/leo-profile/android/user-id-card/delete")
    suspend fun closeParentCertification()

    /**
     * 查询家长认证状态。
     *
     * GET `/leo-profile/android/user-id-card`，返回 `ParentCertificationData`。
     */
    @BaseUrl(BASE_LEO)
    @CheckNothing
    @GsonConverter
    @GET("/leo-profile/android/user-id-card")
    suspend fun isParentCertificated(): ParentCertificationData

    /**
     * 拉试卷免费试用次数。
     *
     * GET `/leo-exam/android/photograph/paper/experience`。
     */
    @BaseUrl(BASE_LEO)
    @CheckNothing
    @GsonConverter
    @GET("/leo-exam/android/photograph/paper/experience")
    suspend fun getPaperFreeTrials()

    /**
     * 拉用户头像挂件列表。
     *
     * GET `/leo-alchemy-account/android/user-pendant`，
     * Query `biz: Int`，返回 `UserPendantVO`。
     */
    @BaseUrl(BASE_LEO)
    @CheckNothing
    @GsonConverter
    @GET("/leo-alchemy-account/android/user-pendant")
    suspend fun getUserPendants(@Query("biz") biz: Int): UserPendantVO

    /**
     * 拉用户 VIP 信息 v2。
     *
     * GET `/leo-alchemy-account/android/vip/user/info/v2`，
     * Query `bizTypes: List<Int>`（多值），返回 `UserVipVO`。
     */
    @BaseUrl(BASE_LEO)
    @CheckNothing
    @GsonConverter
    @GET("/leo-alchemy-account/android/vip/user/info/v2")
    suspend fun getUserVipInfoV2(@Query("bizTypes") bizTypes: List<Int>): UserVipVO

    /**
     * 更换手机号。
     *
     * PUT `/leo-profile/android/user-infos/replace-phone`，
     * Query `phone` + `verification`。
     */
    @BaseUrl(BASE_LEO)
    @CheckNothing
    @GsonConverter
    @PUT("/leo-profile/android/user-infos/replace-phone")
    suspend fun replacePhone(
        @Query("phone") phone: String,
        @Query("verification") verification: String,
    )

    /**
     * 提交用户头像挂件。
     *
     * PUT `/leo-alchemy-account/android/user-pendant`，
     * Query `pendantId: Int` + `pendantType: Int`。
     */
    @BaseUrl(BASE_LEO)
    @CheckNothing
    @GsonConverter
    @PUT("/leo-alchemy-account/android/user-pendant")
    suspend fun submitUserPendant(
        @Query("pendantId") pendantId: Int,
        @Query("pendantType") pendantType: Int,
    )

    /**
     * 更新用户资料（旧版 `Call`）。
     *
     * PUT `/leo-profile/android/user-infos`，Body 是原始 `RequestBody`。
     * 新版应走 [LeoProfileApiService.updateUserInfo]。
     */
    @BaseUrl(BASE_LEO)
    @CheckNothing
    @GsonConverter
    @PUT("/leo-profile/android/user-infos")
    fun updateUserInfoLegacy(@Body body: RequestBody): Call<Void>

    /**
     * 上传图片（新版本，多部分表单）。
     *
     * POST `/leo-gallery/android/uploads`，`@Part` 字段 `img`。
     */
    @BaseUrl(BASE_LEO)
    @Multipart
    @POST("/leo-gallery/android/uploads")
    suspend fun uploadImage(@Part img: MultipartBody.Part)

    /**
     * 上传图片（旧版 `Call`，与 [uploadImage] 同路径）。
     */
    @BaseUrl(BASE_LEO)
    @Multipart
    @POST("/leo-gallery/android/uploads")
    fun uploadImageLegacy(@Part img: MultipartBody.Part): Call<Void>
}