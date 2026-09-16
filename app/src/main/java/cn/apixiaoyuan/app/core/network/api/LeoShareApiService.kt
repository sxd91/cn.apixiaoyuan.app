package cn.apixiaoyuan.app.core.network.api

import cn.apixiaoyuan.app.core.network.BASE_LEO
import cn.apixiaoyuan.app.core.network.BaseUrl
import cn.apixiaoyuan.app.core.network.CheckNothing
import cn.apixiaoyuan.app.core.network.GsonConverter
import kotlinx.serialization.json.JsonElement
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Url

/**
 * 自定义分享信息。
 *
 * 挂主域 [BASE_LEO]（`xyks.yuanfudao.com`）。唯一方法逐行来自
 * `smali_classes3/com/fenbi/android/leo/server/LeoShareApiService.smali`。
 *
 * **参数走 `@Url`** —— 与 `LeoExerciseCommonLegacyApiService.download` 同类，
 * 调用方传完整 URL，Retrofit 不拼 base。
 *
 * 返回 `WebAppShareInfo`（原版在 `com.fenbi.android.solarlegacy.common.data`），
 * 本工程暂无该类定义，用 `JsonElement` 占位。
 *
 * 详见 `docs/API-INVENTORY.md`。
 */
interface LeoShareApiService {

    /**
     * 拉自定义分享信息（旧版 `Call`）。
     *
     * GET，`@Url` 动态 URL。
     */
    @BaseUrl(BASE_LEO)
    @CheckNothing
    @GsonConverter
    @GET
    fun getCustomShareInfo(@Url url: String): Call<JsonElement>
}