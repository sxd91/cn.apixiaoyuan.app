package cn.apixiaoyuan.app.feature.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * 配置导出 / 导入的 SAF（Storage Access Framework）胶水层。
 *
 * ## 为什么用 SAF 而不是直接写公共目录
 *
 * Android 11+ 起 `WRITE_EXTERNAL_STORAGE` 已被分区存储限制，
 * 直接往 `Download/` 写文件要么需要 MANAGE_EXTERNAL_STORAGE（应用商店敏感权限），
 * 要么会被拒。SAF 让用户自己在系统文件管理器里选位置，既合规又不需要任何权限。
 *
 * ## 三个函数
 *
 *  - [rememberLauncherForCreateJson]：导出，弹「保存到…」，回调给目标 Uri；
 *  - [rememberLauncherForOpenJson]：导入，弹「打开文件」，回调给选中 Uri；
 *  - [writeTextToUri] / [readTextFromUri]：Uri ↔ 文本的实际读写。
 *
 * 全部走 `runCatching` 兜底：SAF 的 Uri 可能因用户选了云盘文件、文件被删等原因
 * 打不开，异常必须收敛成 null / false，不能冒到 UI 层崩页面。
 */

/**
 * 导出用：`CreateDocument` 让用户选保存位置。
 *
 * @param mimeType 固定 `application/json`，系统文件管理器据此给默认扩展名。
 * @param onResult 回调；uri 为 null 表示用户取消。
 * @return 一个 `(fileName) -> Unit` 的启动器。
 */
@Composable
internal fun rememberLauncherForCreateJson(
    mimeType: String = "application/json",
    onResult: (Uri?) -> Unit,
): (String) -> Unit {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(mimeType),
        onResult = onResult,
    )
    return { fileName -> launcher.launch(fileName) }
}

/**
 * 导入用：`OpenDocument` 让用户选一个已存在的 JSON。
 *
 * 只过滤 json 与纯文本 —— 不限 mime 的话用户会选到图片或安装包，
 * 解析失败还得再提示一次，不如一开始就收窄。
 */
@Composable
internal fun rememberLauncherForOpenJson(
    onResult: (Uri?) -> Unit,
): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = onResult,
    )
    return {
        runCatching { launcher.launch(arrayOf("application/json", "text/plain")) }
    }
}

/**
 * 把文本写进 [uri]。
 *
 * 用 ` ContentResolver.openOutputStream` + `use` 确保流被关闭 ——
 * SAF 给的 Uri 可能指向云盘，不关闭流会导致云端文件始终处于「上传中」。
 *
 * @return 是否写成功。
 */
internal fun writeTextToUri(uri: Uri, text: String): Boolean = runCatching {
    val resolver = cn.apixiaoyuan.app.App.instance.contentResolver
    resolver.openOutputStream(uri)?.use { out ->
        out.write(text.toByteArray(Charsets.UTF_8))
        out.flush()
    } ?: return false
    true
}.getOrDefault(false)

/**
 * 从 [uri] 读文本。
 *
 * @return 文本内容；读不到（Uri 失效 / 无权限）返回 null。
 */
internal fun readTextFromUri(uri: Uri): String? = runCatching {
    val resolver = cn.apixiaoyuan.app.App.instance.contentResolver
    resolver.openInputStream(uri)?.use { input ->
        input.readBytes().toString(Charsets.UTF_8)
    }
}.getOrNull()

/** 供 Composable 内取 Context 用（SAF 读写需要 ContentResolver）。 */
@Composable
internal fun localContext(): android.content.Context = LocalContext.current