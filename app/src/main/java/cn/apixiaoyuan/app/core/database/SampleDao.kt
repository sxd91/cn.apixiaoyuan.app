package cn.apixiaoyuan.app.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * 样本表读写入口。
 *
 * 只暴露样本库页面需要的操作 —— 不做通用 CRUD。少一个方法就少一处
 * 可写错的地方，而样本一旦被误删是不可恢复的（它是手工挑出来的复现清单）。
 */
@Dao
interface SampleDao {

    /** 全部样本，按创建时间倒序。页面主列表用。 */
    @Query("SELECT * FROM sample ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Sample>>

    /** 按 id 取单个样本。重放前再读一次，避免用列表里的过期快照。 */
    @Query("SELECT * FROM sample WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): Sample?

    /** 按名字取单个样本。创建前查重。 */
    @Query("SELECT * FROM sample WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): Sample?

    /** 插入。名字冲突时中止而不是覆盖 —— 覆盖会静默丢掉旧样本。 */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(sample: Sample): Long

    /** 更新。用于重放后回写 lastReplayedAt / lastReplaySuccess。 */
    @Update
    suspend fun update(sample: Sample)

    /** 删除。 */
    @Query("DELETE FROM sample WHERE id = :id")
    suspend fun deleteById(id: Long)
}
