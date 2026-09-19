package cn.apixiaoyuan.app.core.network.api

import cn.apixiaoyuan.app.core.network.*
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * 账号域接口（`ape-api.yuanfudao.com`）。
 *
 * 覆盖验证码发送、登出等走 `ape-api` 的接口。
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
     * 短信验证码发送（`/verifier/android/sms`）。
     *
     * @param yfdU 设备指纹派生值（原版 `Lds/i3` 指纹串经 MD5 取前 8 字节大端拼 long）。
     * `@Query`，未登录时传 null。
     * @param phone 手机号。**这里是密文** —— 服务端只接受 RSA 编码后的 phone，
     *             明文直接返回 403。
     */
    @BaseUrl(BASE_YTF)
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
    @BaseUrl(BASE_YTF)
    @CheckNothing
    @GsonConverter
    @FormUrlEncoded
    @POST("/accounts/android/logout")
    suspend fun logout()
}
