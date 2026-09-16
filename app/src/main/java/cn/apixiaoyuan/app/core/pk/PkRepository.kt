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
     * 注意这个方法名里的 `Poems` 容易误导 —— 它走的是
     * `/leo-game-pk/android/game/homepage`，是**通用 PK 入口**，
     * 诗词只是其中一种玩法。原版把它放在 `LeoPoemsParadiseApiService`
     * 里是历史归类问题，不改动。
     *
     * 返回 `JsonElement` 占位 —— 该接口的真实返回结构未从 smali 读出，
     * 先用宽松结构接住，保证不因字段推断错误而解析失败。
     *
     * @param grade 年级 ID。原版从 `UserVO.grade` 读，这里由调用方传。
     */
    suspend fun fetchPkEntry(grade: Int): JsonElement? = runCatching {
        ServiceLocator.poemsParadise.getPoemsPkEntryData(grade)
    }.getOrNull()
}