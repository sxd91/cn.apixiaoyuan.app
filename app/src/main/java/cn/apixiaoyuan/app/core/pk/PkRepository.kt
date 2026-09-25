package cn.apixiaoyuan.app.core.pk

import cn.apixiaoyuan.app.core.network.NetworkConfig
import cn.apixiaoyuan.app.core.network.ServiceLocator
import kotlinx.serialization.json.JsonElement

/**
 * 口算 PK 的数据入口。
 *
 * 口算 PK 是 **H5**，不是原生页面 —— 这点从 `docs/API-INVENTORY.md` 与
 * 原版 `scheme` 拦截逻辑两处确证：
 *
 *  - H5 页面：`{leo_base}/bh5/leo-web-oral-pk/pk.html#/`
 *  - 原生只做入口 + 埋点，真正交互在 WebView 里
 *
 * 所以本类只做两件事：
 *  1. 拼 H5 入口 URL（原生侧唯一需要构造的东西）
 *  2. 拉 PK 入口数据（`/leo-game-pk/android/game/homepage`），用于
 *     原生侧展示「进入 PK」前的状态（是否可玩、今日次数、段位等）
 *
 * **未做的事**：PK 的答题协议不在这里 —— 那是 H5 内部的私有协议，
 * 原生不参与。若后续要抓它，走协议请求台（`feature/repl`）或 mitmproxy。
 */
object PkRepository {

    /**
     * PK 入口页在 H5 侧的路径。
     *
     * 从原版 `leo://openWebView?url={encoded}` 的 url 参数逐字读出：
     * `https://xyks.yuanfudao.com/bh5/leo-web-oral-pk/pk.html#/`
     */
    private const val PK_H5_PATH = "/bh5/leo-web-oral-pk/pk.html#/"

    /**
     * 拼 PK 入口完整 URL。
     *
     * 域名走 [NetworkConfig.leoBaseUrl]，与主域 Service 一致 ——
     * 原版这个 H5 就挂在主域下，不是独立域。
     */
    fun pkH5Url(): String = NetworkConfig.leoBaseUrl() + PK_H5_PATH

    /**
     * 拉诗词 PK 入口数据（`getPoemsPkEntryData`）。
     *
     * @param grade 年级 ID。原版从 `UserVO.grade` 读，这里由调用方传。
     *
     * ## 实测状态（2026-09-25）
     *
     * 本接口（`/leo-game-pk/android/game/homepage`）实测返回
     * **401 `unauthorized`**（响应头 `x-block-by: SolarAuthFilter`）——
     * 与练习链路的 `417 solar-encoder` **根因不同**：
     *  - 401 = **认证**没过（两层 cookie 缺一层，或设备链未导入）；
     *  - 417 = 认证过了，卡在 `sign` 参数。
     *
     * 所以 PK 侧缺的不是 sign，是**登录态本身**。用 [probeAuth] 可提前判明。
     */
    suspend fun fetchPkEntry(grade: Int): JsonElement? = runCatching {
        ServiceLocator.poemsParadise.getPoemsPkEntryData(grade)
    }.getOrNull()

    /**
     * 主域登录态自检探针。
     *
     * 打 `/leo-star/android/exercise/rank/pre-fetch` —— 这是主域上**唯一实测恒 200**
     * 的端点（2026-09-25 逐端点实测确证），且它是纯 GET、无副作用，
     * 适合当「两层 cookie 是否齐全」的探针。
     *
     * 主域认证是两层，缺一即 401：
     *
     * | 层 | cookie | 本项目能否自取 |
     * |---|---|---|
     * | 设备认证 | `sid` + `ks_sess` + `ks_deviceid` | **否**，只能用户从原版导入 |
     * | 用户认证 | `sess` / `userid` / `g_sess` / `persistent` | 是，登录即可 |
     *
     * @return true = 两层齐全（主域业务可打）；false = 缺设备链或未登录
     */
    suspend fun probeAuth(): Boolean = runCatching {
        ServiceLocator.exerciseLegacy.getCurrentUserExp() != null
    }.getOrDefault(false)
}