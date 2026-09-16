package cn.apixiaoyuan.app.core.network

/**
 * 全部 ApiService 的集中出口，替代原版的 `sp/n`。
 *
 * 原版用静态类 + 静态方法返回单例；这里用 `by lazy` 达到同样效果，
 * 但依赖注入点更清楚（[RetrofitFactory]）。业务层只认这个 object，
 * 不直接碰 Retrofit。
 *
 * 账号域（ytk_base_url）与主域（leo_base_url）的 Service 分开列，
 * 一眼能看出某个接口挂在哪套域名上。
 *
 * 注意：`by lazy` 的首次访问会触发 [RetrofitFactory.leo] / [RetrofitFactory.ytk]，
 * 因此 [RetrofitFactory.init] 必须在任何 Service 被取用之前调用（在 Application.onCreate）。
 */
object ServiceLocator {

    // ---- 账号域（ytk_base_url）----
    // 当前无已落盘的账号域 Service；YtkAccountService 落盘后在此注册：
    // val ytkAccount: YtkAccountService by lazy { RetrofitFactory.ytk(YtkAccountService::class.java) }

    // ---- 主域（leo_base_url）----
    // 当前无已落盘的主域 Service；各 Leo*ApiService 落盘后在对应模块注册，例如：
    // val profile: LeoProfileApiService by lazy { RetrofitFactory.leo(LeoProfileApiService::class.java) }
    // val math: LeoMathApiService by lazy { RetrofitFactory.leo(LeoMathApiService::class.java) }
    // val chinese: LeoChineseApiService by lazy { RetrofitFactory.leo(LeoChineseApiService::class.java) }
    // val english: LeoEnglishApiService by lazy { RetrofitFactory.leo(LeoEnglishApiService::class.java) }
    // val paper: LeoPaperExerciseApiService by lazy { RetrofitFactory.leo(LeoPaperExerciseApiService::class.java) }
    // val poems: LeoPoemsParadiseApiService by lazy { RetrofitFactory.leo(LeoPoemsParadiseApiService::class.java) }
}
