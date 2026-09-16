package cn.apixiaoyuan.app.core.network

import cn.apixiaoyuan.app.core.network.api.LeoEnglishExerciseWritingApiService
import cn.apixiaoyuan.app.core.network.api.LeoExerciseCommonLegacyApiService
import cn.apixiaoyuan.app.core.network.api.LeoGatewayService
import cn.apixiaoyuan.app.core.network.api.LeoProfileApiService
import cn.apixiaoyuan.app.core.network.api.LeoUserApiService
import cn.apixiaoyuan.app.core.network.api.YtkApiService

/**
 * 全部 ApiService 的集中出口，替代原版的 `sp/n`。
 *
 * 账号域（ape-api.yuanfudao.com）与主域（xyks.yuanfudao.com）的 Service
 * 分开列，一眼能看出某个接口挂在哪套域名上。
 *
 * 注意：[LeoGatewayService] 名字像账号域，实际挂主域 ——
 * 从 `mg/h.smali` 方法链与 `sp/n.smali` 服务定位两处交叉确证。
 *
 * 注意：`by lazy` 的首次访问会触发 [RetrofitFactory.leo] / [RetrofitFactory.ytk]，
 * 因此 [RetrofitFactory.init] 必须在任何 Service 被取用之前调用（在 Application.onCreate）。
 */
object ServiceLocator {

    // ---- 账号域（ape-api.yuanfudao.com）----

    val ytkApi: YtkApiService by lazy {
        RetrofitFactory.ytk(YtkApiService::class.java)
    }

    // ---- 主域（xyks.yuanfudao.com）----

    val gateway: LeoGatewayService by lazy {
        RetrofitFactory.leo(LeoGatewayService::class.java)
    }

    val profile: LeoProfileApiService by lazy {
        RetrofitFactory.leo(LeoProfileApiService::class.java)
    }

    val user: LeoUserApiService by lazy {
        RetrofitFactory.leo(LeoUserApiService::class.java)
    }

    val exerciseLegacy: LeoExerciseCommonLegacyApiService by lazy {
        RetrofitFactory.leo(LeoExerciseCommonLegacyApiService::class.java)
    }

    val englishExercise: LeoEnglishExerciseWritingApiService by lazy {
        RetrofitFactory.leo(LeoEnglishExerciseWritingApiService::class.java)
    }
}
