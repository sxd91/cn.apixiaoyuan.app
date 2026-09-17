package cn.apixiaoyuan.app.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * 工程数据库。八表实体全部注册，版本 1。
 *
 * 表清单（模块 12-13，DEV-PLAN）：
 *  - [RequestHistory]  请求历史
 *  - [ResponseCache]   响应缓存
 *  - [Sample]          请求样本（可回放）
 *  - [DecodedPayload]  解码后的明文缓存
 *  - [User]            账号信息快照
 *  - [Session]         会话审计日志
 *  - [ExerciseRecord]  练习记录
 *  - [PkRecord]        PK 记录
 *
 * ## 迁移策略
 *
 * 版本 1，`exportSchema = true` 已开，schema 落到 `app/schemas/`（构建时由
 * KSP 生成）。之所以开 schema 导出：后续改表时 `fallbackToDestructiveMigration`
 * 会静默清库，而请求样本是不可恢复的手工产物 —— 必须有 schema 才能写
 * 真实迁移，不能靠清库蒙混。
 *
 * 当前没有旧版本要迁，`fallbackToDestructiveMigration` **不启用**。
 * 开发期若真改了表结构，走 `ALTER TABLE` 的 Migration，或显式清库。
 *
 * ## 单例
 *
 * [init] 由 `App.onCreate` 调用，与 `RetrofitFactory.init` / `SessionStore.init`
 * 并列。数据库不涉及网络与 cookie，初始化顺序在它们之后即可。
 */
@Database(
    entities = [
        RequestHistory::class,
        ResponseCache::class,
        Sample::class,
        DecodedPayload::class,
        User::class,
        Session::class,
        ExerciseRecord::class,
        PkRecord::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    /** 样本表入口。样本库页面用。 */
    abstract fun sampleDao(): SampleDao

    /** 请求历史表入口。请求流水用。 */
    abstract fun requestHistoryDao(): RequestHistoryDao

    companion object {

        private const val DB_NAME = "apixiaoyuan.db"

        @Volatile
        private var instance: AppDatabase? = null

        /** 幂等初始化。重复调用返回同一实例。 */
        fun init(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME,
                ).build().also { instance = it }
            }
        }

        /** 取已初始化的实例。未初始化时抛异常，不静默返回 null。 */
        fun get(): AppDatabase {
            return instance ?: error("AppDatabase.init() 未调用")
        }
    }
}