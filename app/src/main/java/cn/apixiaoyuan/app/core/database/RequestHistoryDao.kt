package cn.apixiaoyuan.app.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 请求历史表读写入口。
 *
 * 历史是只增不改的流水 —— 落进去的记录不提供 UPDATE。需要修正时
 * 删掉重写，而不是原地改：流水一旦可改就失去了作为证据的价值，
 * 而这张表的用途正是「事后对照请求与响应」。
 *
 * 查询能力先只做页面会用的两条：按时间倒序的 Flow（列表）、
 * 按路径过滤（按接口聚合）。更复杂的统计等有真实需求再加。
 */
@Dao
interface RequestHistoryDao {

    /** 全部历史，按时间倒序。页面主列表用。 */
    @Query("SELECT * FROM request_history ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<RequestHistory>>

    /** 限定条数的历史，避免流水无限增长时一次读出全部。 */
    @Query("SELECT * FROM request_history ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<RequestHistory>>

    /** 按路径前缀过滤。按接口聚合查看时用。 */
    @Query("SELECT * FROM request_history WHERE path LIKE :pathPrefix || '%' ORDER BY timestamp DESC")
    fun observeByPath(pathPrefix: String): Flow<List<RequestHistory>>

    /** 按 requestId 取单条。 */
    @Query("SELECT * FROM request_history WHERE requestId = :requestId LIMIT 1")
    suspend fun findById(requestId: String): RequestHistory?

    /** 插入一条历史。requestId 冲突时替换 —— 重放同一请求时保留最新一次。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: RequestHistory)

    /** 总数。用于页面展示与清理阈值判断。 */
    @Query("SELECT COUNT(*) FROM request_history")
    suspend fun count(): Int

    /**
     * 按条数裁剪：保留最新的 [keep] 条，其余删除。
     *
     * 流水表必须有上限，否则长期使用后单次查询会越来越慢。
     * 裁剪在写入后调用，不在读取时做。
     */
    @Query(
        "DELETE FROM request_history WHERE requestId NOT IN " +
            "(SELECT requestId FROM request_history ORDER BY timestamp DESC LIMIT :keep)"
    )
    suspend fun trimTo(keep: Int): Int

    /** 清空全部历史。 */
    @Query("DELETE FROM request_history")
    suspend fun clearAll()
}