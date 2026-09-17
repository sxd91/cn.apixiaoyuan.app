package cn.apixiaoyuan.app.core.samples

import cn.apixiaoyuan.app.core.database.AppDatabase
import cn.apixiaoyuan.app.core.database.RequestHistory
import cn.apixiaoyuan.app.core.database.Sample
import kotlinx.coroutines.flow.Flow

/**
 * 样本库数据入口。
 *
 * 分层纪律与 [cn.apixiaoyuan.app.core.exercise.ExerciseRepository] 一致：
 * 这是唯一碰 [AppDatabase] 的层，上层（ViewModel / Screen）只见 [Sample] 与
 * 本类的函数，不直接接触 DAO 或 Room。
 *
 * 管两件事：样本表读写（含重放记录回写）、重放后的请求历史落库。
 * 重放本身的网络逻辑放在 [cn.apixiaoyuan.app.feature.samples.SamplesViewModel]
 * 里 —— Repository 负责数据，ViewModel 负责动作。
 *
 * 历史写入放在这一层而不是 ViewModel：ViewModel 只该描述「发生了什么」，
 * 不该知道 Room 或 DAO 长什么样。`SamplesViewModel` 调 [recordReplay]，
 * 由这里拼出 [RequestHistory] 并落库。
 */
class SampleRepository(
    private val db: AppDatabase,
) {

    /** 全部样本，按创建时间倒序。
     * 直接用 DAO 的 Flow，不在这里转成一次性列表 —— 样本被删除或新增时
     * 页面要自动刷新，一次性快照做不到。
     */
    fun observeAll(): Flow<List<Sample>> = db.sampleDao().observeAll()

    /** 按 id 取单个样本。重放前调用，拿最新快照。 */
    suspend fun findById(id: Long): Sample? = db.sampleDao().findById(id)

    /** 按名字取单个样本。创建前查重用。 */
    suspend fun findByName(name: String): Sample? = db.sampleDao().findByName(name)

    /**
     * 插入样本。
     *
     * 名字重复时 DAO 会抛异常（`OnConflictStrategy.ABORT`），这里用
     * [runCatching] 兜底并返回 null —— 调用方据此展示「名字已存在」，
     * 而不是让异常冒到 UI 层崩掉。
     */
    suspend fun create(sample: Sample): Long? =
        runCatching { db.sampleDao().insert(sample) }.getOrNull()

    /** 回写重放结果。重放后调用。 */
    suspend fun markReplayed(id: Long, at: Long, success: Boolean): Boolean =
        runCatching {
            val current = db.sampleDao().findById(id) ?: return@runCatching false
            db.sampleDao().update(
                current.copy(
                    lastReplayedAt = at,
                    lastReplaySuccess = success,
                )
            )
            true
        }.getOrDefault(false)

    /**
     * 记录一次重放到请求流水。
     *
     * 每次重放都落一行，无论成败 —— 失败记录同样有价值（它说明这个样本
     * 在当前登录态 \/ 当前服务端版本下打不通）。
     *
     * `requestId` 用 [java.util.UUID]，`path` 从 URL 里截出（不含 host 与
     * 查询串）便于按接口聚合。`requestEncoded` \/ `responseDecoded` 直接
     * 取自样本的两个标记位，与 [cn.apixiaoyuan.app.feature.samples.SamplesViewModel]
     * 实际走的编解码方向一致。
     *
     * 落库后顺手裁剪到 [HISTORY_KEEP] 条，避免长期使用后单次查询变慢。
     *
     * @return 落库的 requestId；失败时返回 null。
     */
    suspend fun recordReplay(
        sample: Sample,
        statusCode: Int,
        success: Boolean,
        durationMs: Long,
        error: String?,
    ): String? = runCatching {
        val requestId = java.util.UUID.randomUUID().toString()
        val history = RequestHistory(
            requestId = requestId,
            timestamp = System.currentTimeMillis(),
            method = sample.method.uppercase(),
            url = sample.url,
            path = pathOf(sample.url),
            headers = sample.headers,
            requestBody = sample.requestBody,
            requestEncoded = sample.needEncode,
            statusCode = statusCode,
            success = success,
            durationMs = durationMs,
            responseDecoded = sample.needDecode,
            error = error,
            bodyTruncated = false,
        )
        db.requestHistoryDao().insert(history)
        db.requestHistoryDao().trimTo(HISTORY_KEEP)
        requestId
    }.getOrNull()

    /**
     * 从原始请求字段直接落一条历史。给协议请求台的「发送」用。
     *
     * 与 [recordReplay] 的区别：那个从 [Sample] 出发（样本重放），
     * 这个从裸字段出发（手工发的请求）。两条路径最终写的是同一张表、
     * 同一组语义，因此共用 [pathOf] 与 [HISTORY_KEEP]。
     *
     * @return 落库的 requestId；失败时返回 null。
     */
    suspend fun recordRawRequest(
        method: String,
        url: String,
        headers: String?,
        requestBody: String?,
        requestEncoded: Boolean,
        statusCode: Int,
        success: Boolean,
        durationMs: Long,
        responseDecoded: Boolean,
        error: String?,
    ): String? = runCatching {
        val requestId = java.util.UUID.randomUUID().toString()
        val history = RequestHistory(
            requestId = requestId,
            timestamp = System.currentTimeMillis(),
            method = method.uppercase(),
            url = url,
            path = pathOf(url),
            headers = headers,
            requestBody = requestBody,
            requestEncoded = requestEncoded,
            statusCode = statusCode,
            success = success,
            durationMs = durationMs,
            responseDecoded = responseDecoded,
            error = error,
            bodyTruncated = false,
        )
        db.requestHistoryDao().insert(history)
        db.requestHistoryDao().trimTo(HISTORY_KEEP)
        requestId
    }.getOrNull()

    /** 最近 [limit] 条请求历史。 */
    fun observeRecentHistory(limit: Int = 100): Flow<List<RequestHistory>> =
        db.requestHistoryDao().observeRecent(limit)

    /** 从完整 URL 里截出路径部分（不含 host、不含查询串）。 */
    private fun pathOf(url: String): String {
        val afterScheme = url.substringAfter("://", url)
        val afterHost = afterScheme.substringAfter('/', "")
        return "/" + afterHost.substringBefore('?')
    }

    /** 删除样本。 */
    suspend fun delete(id: Long): Boolean =
        runCatching { db.sampleDao().deleteById(id) }.isSuccess

    private companion object {
        /** 历史流水保留条数上限。 */
        const val HISTORY_KEEP = 2000
    }
}
