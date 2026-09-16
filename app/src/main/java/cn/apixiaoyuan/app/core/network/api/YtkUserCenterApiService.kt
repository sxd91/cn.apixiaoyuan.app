package cn.apixiaoyuan.app.core.network.api

import cn.apixiaoyuan.app.core.model.YtkUserSchoolInfo
import cn.apixiaoyuan.app.core.network.BASE_YTK
import cn.apixiaoyuan.app.core.network.BaseUrl
import cn.apixiaoyuan.app.core.network.CheckNothing
import cn.apixiaoyuan.app.core.network.GsonConverter
import kotlinx.serialization.json.JsonElement
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Query

/**
 * 账号域用户中心。
 *
 * 挂账号域 [BASE_YTK]（`ape-api.yuanfudao.com`）。九个方法逐行来自
 * `smali_classes3/com/fenbi/android/leo/network/YtkUserCenterApiService.smali`。
 *
 * 三条业务线：
 *  - `/accounts/` —— 注销账号、重置密码、发短信
 *  - `/fenbi-school/` —— 学校查询（省/市/区三级 + 定位 + 搜索）
 *  - `/profile/` —— 更新学段信息
 *
 * 与 [YtkApiService] 的关系：后者是登录链（`/accounts/android/safe/login`
 * + `/verifier/`），本类是账号管理链。两者同挂账号域但路径不重叠。
 *
 * **注意**：`smsVerify` 与 [YtkApiService.smsVerify] 同路径
 * `/verifier/android/sms`，但参数形状不同——本类是
 * `@Query("YFD_U") + @Field("phone")`，后者是纯 `@Field`。两条并存是
 * 原版历史遗留，发短信优先用本类（参数更完整）。
 *
 * `getNextLvSchools` / `getProvinces` / `getSchoolByLocation` / `getSchoolSearch`
 * 四条返回 `Call<SchoolNewVO>`，本工程暂无该类定义，用 `JsonElement` 占位。
 *
 * 详见 `docs/API-INVENTORY.md`。
 */
interface YtkUserCenterApiService {

    /**
     * 注销账号。
     *
     * POST `/accounts/android/deregister`，
     * Query `YFD_U`（可空）+ `verification`。
     */
    @BaseUrl(BASE_YTK)
    @CheckNothing
    @GsonConverter
    @POST("/accounts/android/deregister")
    suspend fun deregisterAccount(
        @Query("YFD_U") yfdU: Long?,
        @Query("verification") verification: String,
    )

    /**
     * 直接注销子账号。
     *
     * POST `/accounts/android/directly/subDeregister`，
     * Query `YFD_U`（可空）+ `verification` + `account`。
     */
    @BaseUrl(BASE_YTK)
    @CheckNothing
    @GsonConverter
    @POST("/accounts/android/directly/subDeregister")
    suspend fun directlyDeregisterAccount(
        @Query("YFD_U") yfdU: Long?,
        @Query("verification") verification: String?,
        @Query("account") account: String?,
    )

    /**
     * 拉下一级学校列表（旧版 `Call`）。
     *
     * GET `/fenbi-school/android/schools`，Query `parentId: Int`。
     * 返回 `Call<SchoolNewVO>`，本工程用 `JsonElement` 占位。
     */
    @BaseUrl(BASE_YTK)
    @CheckNothing
    @GsonConverter
    @GET("/fenbi-school/android/schools")
    fun getNextLvSchools(@Query("parentId") parentId: Int): Call<JsonElement>

    /**
     * 拉省份列表（旧版 `Call`）。
     *
     * GET `/fenbi-school/android/provinces`，Query `phaseId: Int`。
     */
    @BaseUrl(BASE_YTK)
    @CheckNothing
    @GsonConverter
    @GET("/fenbi-school/android/provinces")
    fun getProvinces(@Query("phaseId") phaseId: Int): Call<JsonElement>

    /**
     * 按经纬度查最近学校（旧版 `Call`）。
     *
     * GET `/fenbi-school/android/schools/nearest`，
     * Query `lat: Double` + `lng: Double` + `limit: Int` + `phaseId: Int`。
     */
    @BaseUrl(BASE_YTK)
    @CheckNothing
    @GsonConverter
    @GET("/fenbi-school/android/schools/nearest")
    fun getSchoolByLocation(
        @Query("lat") lat: Double,
        @Query("lng") lng: Double,
        @Query("limit") limit: Int,
        @Query("phaseId") phaseId: Int,
    ): Call<JsonElement>

    /**
     * 按关键词搜学校（旧版 `Call`）。
     *
     * GET `/fenbi-school/android/schools/suggest`，
     * Query `query: String` + `phaseId: Int`。
     */
    @BaseUrl(BASE_YTK)
    @CheckNothing
    @GsonConverter
    @GET("/fenbi-school/android/schools/suggest")
    fun getSchoolSearch(
        @Query("query") query: String,
        @Query("phaseId") phaseId: Int,
    ): Call<JsonElement>

    /**
     * 重置密码。
     *
     * POST `/accounts/android/password/reset`，
     * Query `YFD_U`（可空），Field `encryptedPhone` + `verification` + `password`。
     */
    @BaseUrl(BASE_YTK)
    @CheckNothing
    @GsonConverter
    @FormUrlEncoded
    @POST("/accounts/android/password/reset")
    suspend fun resetPassword(
        @Query("YFD_U") yfdU: Long?,
        @Field("encryptedPhone") encryptedPhone: String?,
        @Field("verification") verification: String?,
        @Field("password") password: String?,
    )

    /**
     * 发送短信验证码。
     *
     * POST `/verifier/android/sms`，
     * Query `YFD_U`（可空），Field `phone`（可空）。
     */
    @BaseUrl(BASE_YTK)
    @CheckNothing
    @GsonConverter
    @FormUrlEncoded
    @POST("/verifier/android/sms")
    suspend fun smsVerify(
        @Query("YFD_U") yfdU: Long?,
        @Field("phone") phone: String?,
    )

    /**
     * 更新学段信息（旧版 `Call`）。
     *
     * PUT `/profile/android/user-info`，Body 是原始 `RequestBody`，
     * 返回 `Call<YtkUserSchoolInfo>`。
     */
    @BaseUrl(BASE_YTK)
    @CheckNothing
    @GsonConverter
    @PUT("/profile/android/user-info")
    fun updateUserInfo(@Body body: okhttp3.RequestBody): Call<YtkUserSchoolInfo>
}