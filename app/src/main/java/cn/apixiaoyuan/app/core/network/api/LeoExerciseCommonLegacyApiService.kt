package cn.apixiaoyuan.app.core.network.api

import cn.apixiaoyuan.app.core.model.LeoCurrentTaskInfo
import cn.apixiaoyuan.app.core.model.LeoTodayExerciseListData
import cn.apixiaoyuan.app.core.model.LeoUserCurrentExpData
import cn.apixiaoyuan.app.core.network.BASE_LEO
import cn.apixiaoyuan.app.core.network.BaseUrl
import cn.apixiaoyuan.app.core.network.CheckNothing
import cn.apixiaoyuan.app.core.network.GsonConverter
import cn.apixiaoyuan.app.core.network.NeedEncode
import cn.apixiaoyuan.app.core.network.NotNullAndValid
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Url

/**
 * 通用练习（旧版链路）。
 *
 * 挂主域 [BASE_LEO]（`xyks.yuanfudao.com`）。五个方法逐行来自
 * `smali_classes2/com/fenbi/android/leo/api/LeoExerciseCommonLegacyApiService.smali`。
 *
 * 三条业务线：
 *  - `/leo-star/` —— 星级任务与排名（exp / task / rank attend）
 *  - `/leo-chinese/` —— 语文练习同步
 *  - `@Url` 动态下载 —— `download` 方法走完整 URL，不拼 base
 *
 * **关键新发现**：`postSavedExp` 的参数带 `@NeedEncode` 注解 —— 这是
 * **编码方向**的 native 依赖（请求体需要 native 编码后再发），与
 * [cn.apixiaoyuan.app.core.network.NeedDecode] 的响应解码方向相反。
 * 已确证需编码的接口目前只有这一条，编码器实现在 `libRequestEncoder.so`。
 * 本工程已落 [cn.apixiaoyuan.app.core.network.EncodeBridge] 编码出口 +
 * [cn.apixiaoyuan.app.core.network.NeedEncodeInterceptor] 拦截器，当前挂
 * 恒等实现挡着；native 实现（`zcvsd1wr2t`）由 `core/native/` 装配替换。
 *
 * 详见 `docs/LOGIN-API.md` 与 `docs/API-INVENTORY.md`。
 */
interface LeoExerciseCommonLegacyApiService {

    /**
     * 动态 URL 下载。
     *
     * smali 签名：`download(String, Continuation) -> Object`
     * 参数带 `@Url` —— Retrofit 用完整 URL 替换 base，不拼 `/`。
     * 返回 `Response<ResponseBody>` 而非解析后的对象。
     */
    @BaseUrl(BASE_LEO)
    @CheckNothing
    @GsonConverter
    @GET
    suspend fun download(@Url url: String): Response<ResponseBody>

    /**
     * 拉当前用户经验值（星级任务前置数据）。
     *
     * GET `/leo-star/android/exercise/rank/pre-fetch`，返回 `LeoUserCurrentExpData`。
     */
    @BaseUrl(BASE_LEO)
    @GsonConverter
    @NotNullAndValid
    @GET("/leo-star/android/exercise/rank/pre-fetch")
    suspend fun getCurrentUserExp(): LeoUserCurrentExpData

    /**
     * 拉当前用户任务（首页任务卡）。
     *
     * GET `/leo-star/android/exercise/task/home`，返回 `LeoCurrentTaskInfo`。
     */
    @BaseUrl(BASE_LEO)
    @CheckNothing
    @GsonConverter
    @GET("/leo-star/android/exercise/task/home")
    suspend fun getCurrentUserTasks(): LeoCurrentTaskInfo

    /**
     * 语文练习首页同步（旧版 `Call`，返回 `Integer`）。
     *
     * POST `/leo-chinese/android/exercises/homePage/sync`，Query `textbookKeypointId: Long`。
     */
    @BaseUrl(BASE_LEO)
    @GsonConverter
    @NotNullAndValid
    @POST("/leo-chinese/android/exercises/homePage/sync")
    fun postChineseExercise(@Query("textbookKeypointId") textbookKeypointId: Long): Call<Int>

    /**
     * 上报今日练习列表（保存星级经验）。
     *
     * POST `/leo-star/android/exercise/rank/login/attend`，Body 是
     * `LeoTodayExerciseListData`。
     *
     * **参数带 `@NeedEncode`** —— 请求体在发出前需 native 编码。
     * 本工程当前无此编码器，先用恒等实现挡着；接入后若服务端拒绝
     * 未编码的 body，会在 4xx 暴露。
     */
    @BaseUrl(BASE_LEO)
    @GsonConverter
    @NotNullAndValid
    @NeedEncode
    @POST("/leo-star/android/exercise/rank/login/attend")
    suspend fun postSavedExp(@Body body: LeoTodayExerciseListData)
}