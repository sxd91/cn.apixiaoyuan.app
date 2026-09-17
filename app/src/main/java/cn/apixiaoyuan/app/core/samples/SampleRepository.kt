package cn.apixiaoyuan.app.core.samples

import cn.apixiaoyuan.app.core.database.AppDatabase
import cn.apixiaoyuan.app.core.database.Sample
import kotlinx.coroutines.flow.Flow

/**
 * 样本库数据入口。
 *
 * 分层纪律与 [cn.apixiaoyuan.app.core.exercise.ExerciseRepository] 一致：
 * 这是唯一碰 [AppDatabase] 的层，上层（ViewModel / Screen）只见 [Sample] 与
 * 本类的函数，不直接接触 DAO 或 Room。
 *
 * 当前只做样本表的读写 + 重放记录回写。重放本身的网络逻辑放在
 * [cn.apixiaoyuan.app.feature.samples.SamplesViewModel] 里 —— Repository
 * 负责数据，ViewModel 负责动作。
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

    /** 删除样本。 */
    suspend fun delete(id: Long): Boolean =
        runCatching { db.sampleDao().deleteById(id) }.isSuccess
}
