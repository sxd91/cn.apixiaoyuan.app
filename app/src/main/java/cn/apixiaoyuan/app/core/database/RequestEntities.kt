package cn.apixiaoyuan.app.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 请求链路四表：RequestHistory / ResponseCache / Sample / DecodedPayload。
 *
 * 字段以「可确证的最小集」为准 —— 这个工程是逆向工具，落库的目的不是建模
 * 业务，而是让每一次请求可回放、每一份密文可对照。因此字段里全部带
 * `raw`（原始字节）与 `decoded`（解码后）两列，而不是只存解析后的对象 ——
 * 后者一旦解析器写错，原始数据就永久丢失了。
 *
 * ⚠️ 标注「推断」的字段是设计推断，不是从原版逆向出的结构。原版的数据库
 * schema 未逆向（它的 Room 版本、表名、列名都在 `smali` 的 `_Impl` 类里，
 * 未逐行读）。本工程的表是自建的，只要能支撑「可回放」这个验收标准即可。
 */

/**
 * 请求历史。每次发出请求落一行，无论成功失败。
 *
 * `requestId` 用 `UUID` 字符串而不是自增 Long —— 请求可能并发，自增主键
 * 在插入前拿不到值，而请求头里需要带上自己的 id 便于日志串联。
 */
@Entity(
    tableName = "request_history",
    indices = [Index("timestamp"), Index("path"), Index("success")],
)
data class RequestHistory(
    /** 请求唯一 id（UUID），同时写进请求头用于串联日志。 */
    @PrimaryKey val requestId: String,
    /** 发出时刻（epoch millis）。 */
    val timestamp: Long,
    /** HTTP 方法。 */
    val method: String,
    /** 完整 URL（含查询串）。 */
    val url: String,
    /** 路径部分（不含 host），便于按接口聚合统计。 */
    val path: String,
    /** 请求头，JSON 字符串。 */
    val headers: String?,
    /** 请求体原文（编码前），超长时截断并在 [bodyTruncated] 标记。 */
    val requestBody: String?,
    /** 是否对请求体做了 native 编码（`@NeedEncode`）。 */
    val requestEncoded: Boolean,
    /** HTTP 状态码；网络层异常时为 -1。 */
    val statusCode: Int,
    /** 是否成功（2xx）。 */
    val success: Boolean,
    /** 耗时毫秒。 */
    val durationMs: Long,
    /** 响应体是否被 native 解码（`@NeedDecode`）。 */
    val responseDecoded: Boolean,
    /** 失败时的异常摘要（类名 + message），成功为 null。 */
    val error: String?,
    /** body 是否被截断（超过阈值）。 */
    val bodyTruncated: Boolean = false,
)

/**
 * 响应缓存。按「请求指纹」去重，同一次请求重复发时可命中。
 *
 * 指纹不是 URL —— 同一个 URL 带不同 body 是不同请求。指纹 = method + url +
 * 规范化后的 body 的 SHA-256。
 */
@Entity(
    tableName = "response_cache",
    indices = [Index(value = ["fingerprint"], unique = true)],
)
data class ResponseCache(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** method + url + body 的 SHA-256，唯一。 */
    val fingerprint: String,
    /** 原始响应字节（Base64 编码存储）。 */
    val rawBody: String,
    /** 解码后的明文（Base64）；未解码时为 null。 */
    val decodedBody: String?,
    /** 响应 Content-Type。 */
    val contentType: String?,
    /** HTTP 状态码。 */
    val statusCode: Int,
    /** 写入时刻。 */
    val cachedAt: Long,
)

/**
 * 请求样本。可回放 —— 保存一次请求的完整输入，之后能按它重放。
 *
 * 与 [RequestHistory] 的区别：History 是「发生过什么」的流水，
 * Sample 是「我挑出来要复现的」清单。Sample 引用 History 的 requestId，
 * 但内容是自包含的（即使 History 被清理，Sample 仍能重放）。
 */
@Entity(
    tableName = "sample",
    indices = [Index("name", unique = true), Index("sourceRequestId")],
)
data class Sample(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 用户给样本起的名字，唯一。 */
    val name: String,
    /** 可选的描述。 */
    val description: String?,
    /** 来源请求 id；手动创建时为 null。 */
    val sourceRequestId: String?,
    /** 重放所需：method。 */
    val method: String,
    /** 重放所需：完整 URL。 */
    val url: String,
    /** 重放所需：请求头 JSON。 */
    val headers: String?,
    /** 重放所需：请求体（编码前）。 */
    val requestBody: String?,
    /** 该样本是否要求 native 编码。 */
    val needEncode: Boolean,
    /** 该样本是否期望 native 解码。 */
    val needDecode: Boolean,
    /** 创建时刻。 */
    val createdAt: Long,
    /** 最近一次重放时刻；未重放为 null。 */
    val lastReplayedAt: Long?,
    /** 最近一次重放是否成功。 */
    val lastReplaySuccess: Boolean?,
)

/**
 * 解码后的明文缓存。
 *
 * 单独一表而不是塞进 [ResponseCache]：native 解码是这套逆向里最贵的一步
 * （要加载 so、过 JNI），缓存它能省掉重复解码；而且解码结果与原始密文
 * 往往要分开比对，同表存会互相拖累查询。
 */
@Entity(
    tableName = "decoded_payload",
    indices = [Index(value = ["rawHash"], unique = true)],
)
data class DecodedPayload(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 密文原始字节的 SHA-256，唯一。 */
    val rawHash: String,
    /** 明文（Base64）。 */
    val decoded: String,
    /** 解码方式：`content-encoder` / `request-encoder` / `identity` / `unknown`。 */
    val decoder: String,
    /** 解码时刻。 */
    val decodedAt: Long,
)
