package cn.apixiaoyuan.app.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 业务链路四表：User / Session / ExerciseRecord / PkRecord。
 *
 * 与 [RequestHistory] 那组不同，这四表的字段是「业务事实」而非「原始字节」，
 * 因此不存 Base64 原始体，只存结构化结果。
 *
 * ⚠️ **与 SessionStore 的关系**：登录态的唯一真身在 [cn.apixiaoyuan.app.core.session.SessionStore]
 * （cookie 持久化，R2 已闭环）。这里的 [User] 表只存「用户资料的快照」，
 * 用于离线展示与历史对照，**不是鉴权来源**。鉴权一律走 cookie，不走这张表。
 *
 * ⚠️ **与 `@NeedEncode` / `@NeedDecode` 的关系**：[ExerciseRecord] 与 [PkRecord]
 * 存的是解码后的业务结果；原始密文归 [DecodedPayload] 与 [ResponseCache] 管。
 * 同一次练习的密文与明文分散在两处，是为了让「解码器写错」时可对照定位。
 */

/**
 * 用户资料快照。
 *
 * **不是鉴权来源** —— 登录态在 SessionStore 的 cookie 里（R2 确证）。
 * 这张表只用来在离线时展示「上次登录的是谁」，以及对照资料变更历史。
 *
 * 主键用 `userId`（等于 `UserVO.userId`，也等于 cookie 里的 `userid`），
 * 同一用户重复拉取资料时是 UPDATE 而不是 INSERT 新行。
 */
@Entity(
    tableName = "user_profile",
    indices = [Index("phone"), Index("grade")],
)
data class User(
    /** 用户 id，等于 `UserVO.userId`，等于 cookie `userid`。 */
    @PrimaryKey val userId: Long,
    /** 昵称。 */
    val nickname: String?,
    /** 头像 URL。 */
    val avatarUrl: String?,
    /** 手机号（原版接口返回，可能脱敏）。 */
    val phone: String?,
    /** 年级。真机 `UserVO.grade` 读到过，英语章节请求的 `grade=2` 来源。 */
    val grade: Int?,
    /** 学校节点，JSON 字符串（`UserPhaseInfo.school` 是 List<SchoolNode>）。 */
    val school: String?,
    /** 资料快照时刻。 */
    val capturedAt: Long,
)

/**
 * 会话记录。
 *
 * ⚠️ **只存审计信息，不存 cookie 值**。
 * cookie 的真身在 [cn.apixiaoyuan.app.core.session.SessionStore] 的
 * `cookieJsonListKey` 里（真机确证的 MMKV 键名）。这里存的是「什么时候登录过、
 * 从哪来、什么时候登出的」，用于排查登录态问题，不参与鉴权。
 *
 * 之所以不在这里冗余一份 cookie：两处存登录态，必然出现不一致，
 * 而鉴权必须只有一个真相来源。
 */
@Entity(
    tableName = "session_log",
    indices = [Index("startedAt"), Index("active")],
)
data class Session(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 关联的用户 id；未拿到资料时为 null。 */
    val userId: Long?,
    /** 登录方式：`password` / `sms` / `cookie-restore` / `unknown`。 */
    val loginMethod: String,
    /** 登录时刻。 */
    val startedAt: Long,
    /** 登出时刻；仍在登录态为 null。 */
    val endedAt: Long?,
    /** 当前是否活跃。登出或失效时置 false。 */
    val active: Boolean,
    /** 登录结果摘要，失败时记原因。 */
    val note: String?,
)

/**
 * 练习记录。
 *
 * 一次「任务卡」完成落一行。字段取自 `ExerciseModels` 里已确证的模型
 * （`LeoCurrentTaskInfo` / `LeoUserCurrentExpData`），推断字段显式标注。
 *
 * ⚠️ `taskId` / `taskName` / `finishedCount` / `totalCount` 是**推断字段** ——
 * 任务卡接口的真实返回结构未完全读出（`LeoCurrentTaskInfo` 当前是占位模型）。
 * 真机验证后按实际字段调整。
 */
@Entity(
    tableName = "exercise_record",
    indices = [Index("recordedAt"), Index("taskId"), Index("subject")],
)
data class ExerciseRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 任务 id（推断字段，待真机确证）。 */
    val taskId: String?,
    /** 任务名（推断字段）。 */
    val taskName: String?,
    /** 学科：`math` / `chinese` / `english`。 */
    val subject: String,
    /** 已完成数（推断字段）。 */
    val finishedCount: Int,
    /** 总数（推断字段）。 */
    val totalCount: Int,
    /** 本次获得的经验值；无则为 0。 */
    val expGained: Int,
    /** 上报时的明文字节是否经 native 编码（对应 `postSavedExp` 的 `@NeedEncode`）。 */
    val encoded: Boolean,
    /** 记录时刻。 */
    val recordedAt: Long,
    /** 来源请求 id，可回溯到 [RequestHistory]。 */
    val requestId: String?,
)

/**
 * PK 记录。
 *
 * ⚠️ **口算 PK 是 H5**（模块 8 已确证）：原生只做入口与埋点，
 * 答题协议在 H5 内部，原生拿不到逐题结果。因此这张表**只记原生侧
 * 能观测到的东西**：什么时候进了 PK 页、H5 URL 是什么、
 * WebView 何时加载完成/失败、是否触发过 `leo://close`。
 *
 * 不存「胜负」「得分」这类字段 —— 原生看不见它们，编造列名比不建表更坏。
 */
@Entity(
    tableName = "pk_record",
    indices = [Index("enteredAt"), Index("loadedOk")],
)
data class PkRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 进入 PK 页时刻。 */
    val enteredAt: Long,
    /** 实际加载的 H5 URL（含参数）。 */
    val h5Url: String,
    /** WebView 是否加载成功。 */
    val loadedOk: Boolean,
    /** 页面标题（H5 回传）。 */
    val pageTitle: String?,
    /** 加载耗时毫秒；失败为 -1。 */
    val loadDurationMs: Long,
    /** 是否触发过 `leo://close`（用户主动退出）。 */
    val closedByScheme: Boolean,
    /** 失败原因摘要。 */
    val error: String?,
)
