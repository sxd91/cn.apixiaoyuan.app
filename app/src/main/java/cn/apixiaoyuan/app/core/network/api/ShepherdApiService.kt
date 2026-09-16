package cn.apixiaoyuan.app.core.network.api

import cn.apixiaoyuan.app.core.network.BASE_LEO
import cn.apixiaoyuan.app.core.network.BaseUrl
import cn.apixiaoyuan.app.core.network.CheckNothing
import kotlinx.serialization.json.JsonElement
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * 消息同步（Shepherd）。
 *
 * 挂主域 [BASE_LEO]（`xyks.yuanfudao.com`）。唯一方法逐行来自
 * `smali_classes7/com/yuanfudao/android/leo/shepherd/ShepherdApiService.smali`。
 *
 * **关键差异点**：这个方法用的是 `@MoshiConverter` 而不是 `@GsonConverter`，
 * 是全项目唯一一个 Moshi 链路的接口。本工程的 RetrofitFactory 只装了
 * kotlinx.serialization converter，所以这条方法**当前发出去会解析失败**——
 * 这是已知限制，等引入 Moshi converter 或改成 `JsonElement` 占位后解决。
 *
 * 三个参数全部是 Query，且 `sessionId` / `lid` 可空：
 *  - `did` —— 设备 ID（对应真机 `leo_shepherd_id` 里的 `didKey`）
 *  - `sessionId` —— 会话 ID
 *  - `lid` —— 本地 ID
 *
 * 返回 `SyncLevel`（见 `smali_classes7/com/yuanfudao/android/leo/shepherd/SyncLevel.smali`），
 * 本工程暂无该类定义，用 `JsonElement` 占位。
 *
 * 详见 `docs/API-INVENTORY.md`。
 */
interface ShepherdApiService {

    /**
     * 同步设备消息。
     *
     * POST `/leo-msg/android/sync/du`，
     * Query `did`（必填）+ `sessionId`（可空）+ `lid`（可空）。
     *
     * @param did       设备 ID，必填
     * @param sessionId 会话 ID，可空
     * @param lid       本地 ID，可空
     */
    @BaseUrl(BASE_LEO)
    @CheckNothing
    @POST("/leo-msg/android/sync/du")
    suspend fun syncDu(
        @Query("did") did: String,
        @Query("sessionId") sessionId: String?,
        @Query("lid") lid: String?,
    ): JsonElement
}