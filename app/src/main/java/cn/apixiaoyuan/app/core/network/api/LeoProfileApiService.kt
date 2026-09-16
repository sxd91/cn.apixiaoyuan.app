package cn.apixiaoyuan.app.core.network.api

import cn.apixiaoyuan.app.core.model.UserSetting
import cn.apixiaoyuan.app.core.model.UserVO
import cn.apixiaoyuan.app.core.network.BASE_LEO
import cn.apixiaoyuan.app.core.network.BaseUrl
import cn.apixiaoyuan.app.core.network.CheckNothing
import cn.apixiaoyuan.app.core.network.GsonConverter
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT

/**
 * 用户资料 Service。
 *
 * 挂主域 [BASE_LEO]。五个方法逐行来自
 * `smali_classes6/com/yuanfudao/android/leo/login/api/LeoProfileApiService.smali`。
 *
 * 注意：登录后选角色/年级的 `updateUserInfo` 也走这里。
 *
 * 详见 `docs/LOGIN-API.md`。
 */
interface LeoProfileApiService {

    /** 拉当前用户资料。 */
    @BaseUrl(BASE_LEO)
    @CheckNothing
    @GsonConverter
    @GET("/leo-profile/android/user-infos")
    suspend fun getUserInfo(): UserVO

    /** 更新用户资料（选角色 + 年级后落库）。 */
    @BaseUrl(BASE_LEO)
    @GsonConverter
    @PUT("/leo-profile/android/user-infos")
    suspend fun updateUserInfo(@Body body: UserVO): UserVO

    /** 拉用户设置。 */
    @BaseUrl(BASE_LEO)
    @CheckNothing
    @GsonConverter
    @GET("/leo-profile/android/user-setting")
    suspend fun getUserSetting(): UserSetting

    /** 更新用户设置。 */
    @BaseUrl(BASE_LEO)
    @GsonConverter
    @POST("/leo-profile/android/user-setting/update")
    suspend fun updateUserSetting(@Body body: UserSetting): Boolean

    /** 登出（主域侧）。 */
    @BaseUrl(BASE_LEO)
    @CheckNothing
    @POST("/leo-profile/android/logout")
    suspend fun leoLogout()
}
