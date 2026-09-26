package cn.apixiaoyuan.app.feature.account

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.apixiaoyuan.app.core.navigation.AppNavController
import cn.apixiaoyuan.app.core.account.SubAccountItem
import cn.apixiaoyuan.app.core.oldsimian.OldSimianPrefs
import cn.apixiaoyuan.app.core.design.component.AppScrollScaffold
import cn.apixiaoyuan.app.core.design.icon.AppIcons
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 账号页：宝贝学习账号（子账号）切换 + 改密码。
 *
 * 从首页的用户卡片进入（见 `HomeScreen.SessionCard`）—— 用户明确要求
 * 「账号切换等功能的下级页面点击主页的用户卡片即可进入」。
 *
 * ## 为什么不是二级页列表
 *
 * 这一页承担两件事：**切换宝贝账号** 与 **改密码**。两者都是低频操作、
 * 都需要先看到「当前是哪个账号」，放同一页比拆成两个入口更符合实际使用路径
 * （切完账号顺手看看密码）。
 *
 * ## 子账号列表的空态是如实提示
 *
 * 服务端没有「我的子账号列表」接口，ID 只能从登录响应
 * `UserAccount.subUserInfos` 截取。所以**升级前就已登录**的用户本地没有这份
 * 缓存，列表会空。这里明确写「请重新登录一次以载入」，而不是伪装成
 * 「你没有宝贝账号」—— 后者会让人以为功能坏了。
 */
@Composable
fun AccountScreen(
    navController: AppNavController,
    viewModel: AccountViewModel = viewModel(),
) {
    var deleteTarget by remember { mutableStateOf<SubAccountItem?>(null) }
    var deleteCode by remember { mutableStateOf("") }

    AppScrollScaffold(title = "账号", onBack = { navController.popBackStack() }) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ==================== 当前账号 ====================
            SectionCard(title = "当前账号") {
                val u = viewModel.currentUser
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = u?.nickname ?: u?.defaultNickname ?: "未登录",
                        color = MiuixTheme.colorScheme.onSurfaceContainer,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = { viewModel.refresh() }) { Text("刷新") }
                }
                u?.let {
                    Text(
                        text = "ID ${it.userId} · 主账号 ${it.primaryUserId} · 年级 ${it.grade}",
                        color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                    )
                }
            }

            // ==================== 改名 ====================
            // 「无视名字限制」开关合并进此处：打开后客户端不做长度/字符校验，
            // 昵称原样提交，由服务端裁决 —— 即「原生无限制改名」。
            SectionCard(title = "修改昵称") {
                TextField(
                    value = viewModel.nicknameInput,
                    onValueChange = { viewModel.nicknameInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = "昵称",
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                )
                val unlimited = OldSimianPrefs.ignoreNicknameRestriction
                Text(
                    text = if (unlimited) {
                        "已开启「无视名字限制」：客户端不校验长度与字符，原样提交。"
                    } else {
                        "关闭「无视名字限制」时，本地限制 16 个字符。"
                    },
                    color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                )
                Button(
                    onClick = { viewModel.rename() },
                    enabled = !viewModel.submittingNickname,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (viewModel.submittingNickname) "提交中…" else "确认修改昵称")
                }
            }
            // ==================== 宝贝学习账号 ====================
            SectionCard(title = "宝贝学习账号") {
                if (viewModel.subAccounts.isEmpty()) {
                    Text(
                        text = if (viewModel.loading) {
                            "正在拉取宝贝账号…"
                        } else {
                            "没有可显示的宝贝账号。若你确认有小号，点「刷新」重试；" +
                                "仍为空说明当前登录账号名下确实没有其它账号。"
                        },
                        color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                    )
                } else {
                    viewModel.subAccounts.forEach { item ->
                        SubAccountRow(
                            item = item,
                            onClick = { viewModel.switchTo(item) },
                            onDelete = {
                                deleteTarget = item
                                deleteCode = ""
                            },
                        )
                    }
                }
                Button(
                    onClick = { viewModel.createSubAccount() },
                    enabled = !viewModel.loading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("新建宝贝账号")
                }
                Text(
                    text = "切换会立即生效：服务端下发新的登录 cookie，之后所有请求都按新账号走。",
                    color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                )
            }

            // ==================== 删除确认 ====================
            deleteTarget?.let { target ->
                SectionCard(title = "删除「${target.nickname}」") {
                    Text(
                        text = "删除子账号需要短信验证码（服务端要求，且验证码须 RSA 加密后提交）。" +
                            "此操作不可撤销。",
                        color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                    )
                    // 手机号：删除接口需要它来发码，与改密码共用同一个字段。
                    TextField(
                        value = viewModel.phone,
                        onValueChange = { viewModel.phone = it.filter(Char::isDigit).take(11) },
                        modifier = Modifier.fillMaxWidth(),
                        label = "手机号（用于接收验证码）",
                        useLabelAsPlaceholder = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TextField(
                            value = deleteCode,
                            onValueChange = { deleteCode = it.filter(Char::isDigit).take(6) },
                            modifier = Modifier.weight(1f),
                            label = "短信验证码",
                            useLabelAsPlaceholder = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                        )
                        Button(
                            onClick = { viewModel.sendDeleteSmsCode() },
                            enabled = viewModel.phone.length == 11 && viewModel.countdown == 0,
                        ) {
                            Text(if (viewModel.countdown > 0) "${viewModel.countdown}s" else "获取验证码")
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Button(
                            onClick = {
                                viewModel.deleteSubAccount(target, deleteCode)
                                deleteTarget = null
                            },
                            enabled = deleteCode.length >= 4 && !viewModel.loading,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("确认删除")
                        }
                        Button(
                            onClick = { deleteTarget = null },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("取消")
                        }
                    }
                }
            }

            // ==================== 修改密码 ====================
            SectionCard(title = "修改密码") {
                Text(
                    text = "密码提交前会用与手机号同一把公钥做 RSA 加密（服务端要求密文）。",
                    color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                )
                TextField(
                    value = viewModel.phone,
                    onValueChange = { viewModel.phone = it.filter(Char::isDigit).take(11) },
                    modifier = Modifier.fillMaxWidth(),
                    label = "手机号",
                    useLabelAsPlaceholder = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextField(
                        value = viewModel.verification,
                        onValueChange = { viewModel.verification = it.filter(Char::isDigit).take(6) },
                        modifier = Modifier.weight(1f),
                        label = "验证码",
                        useLabelAsPlaceholder = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                    )
                    Button(
                        onClick = { viewModel.sendSmsCode() },
                        enabled = viewModel.phone.length == 11 && viewModel.countdown == 0,
                    ) {
                        Text(if (viewModel.countdown > 0) "${viewModel.countdown}s" else "获取验证码")
                    }
                }
                TextField(
                    value = viewModel.newPassword,
                    onValueChange = { viewModel.newPassword = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = "新密码（至少 6 位）",
                    useLabelAsPlaceholder = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                )
                Button(
                    onClick = { viewModel.resetPassword() },
                    enabled = !viewModel.submittingPassword,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (viewModel.submittingPassword) "提交中…" else "确认修改密码")
                }
            }

            // ==================== 登录态导入 ====================
            //
            // ⚠️ 2026-09-26 恢复：此前一度删除，依据是「主域不需要设备链」的实测结论 ——
            // 那个结论是**错的**：它只测了 `rank/pre-fetch`，而该端点是服务端的
            // **路径白名单特例**（不走认证也不走编码，任何 cookie 都 200），
            // 不能用来推断整个主域。
            //
            // 用真实业务端点（batchGet / task/home / leo-math）重测：
            //   完整 cookie（登录 + sid + ks_*） → 417 solar-encoder（认证已过，只卡编码）
            //   去掉 sid + 全部 ks_*             → 401 leo-auth
            //   不带                              → 401 fenbi-auth
            // 即设备链**确实是必需的**，且认证是两层链（fenbi-auth → leo-auth）。
            SectionCard(title = "导入登录态（主域权限）") {
                Text(
                    text = "主域业务接口（练习 / 出题 / 资料）需要两层凭据：\n" +
                        "① 设备链 sid + ks_sess + ks_deviceid（+ ks_persistent / ks_r / ks_u），" +
                        "由原版 App 下发，本项目拿不到；\n" +
                        "② 用户凭据 sess / userid / g_sess，本项目登录已有。\n" +
                        "只带 ② 会得到 401 leo-auth；两层齐备才会进入编码层。",
                    color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                )
                TextField(
                    value = viewModel.cookieInput,
                    onValueChange = { viewModel.cookieInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = "sid=...; ks_sess=...; ks_deviceid=...（可只粘这三个）",
                    useLabelAsPlaceholder = true,
                    maxLines = 4,
                )
                Button(
                    onClick = { viewModel.importCookies() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("导入")
                }
                Text(
                    text = "导入只覆盖同名项，不会清掉本项目登录已拿到的 cookie —— " +
                        "两层必须共存。设备链也不会被服务端的清除指令抹掉。\n" +
                        "注：练习类接口在两层齐备后仍可能返回 417（solar-encoder），" +
                        "那是编码层校验，与登录态无关。",
                    color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                )
            }

            viewModel.message?.let {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.defaultColors(
                        color = MiuixTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MiuixTheme.colorScheme.onSurfaceContainerHigh,
                    ),
                ) {
                    Text(text = it, modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
}

/** 宝贝账号行：昵称 + 状态，右侧箭头表示可切换。 */
@Composable
private fun SubAccountRow(
    item: SubAccountItem,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !item.isCurrent, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = item.nickname + when {
                    item.isCurrent -> "（当前）"
                    item.isPrimary -> "（主账号）"
                    else -> ""
                },
                color = MiuixTheme.colorScheme.onSurfaceContainer,
            )
            Text(
                text = "ID ${item.userId} · 年级 ${item.grade}",
                color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
            )
        }
        if (!item.isPrimary && !item.isCurrent) {
            Button(onClick = onDelete) { Text("删除") }
        } else if (!item.isCurrent) {
            Icon(
                imageVector = AppIcons.ChevronForward,
                contentDescription = "切换",
                tint = MiuixTheme.colorScheme.primary,
            )
        }
    }
}

/** 分组卡片。与「老挂戏老叟」页同款写法。 */
@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.defaultColors(
                color = MiuixTheme.colorScheme.surfaceContainer,
                contentColor = MiuixTheme.colorScheme.onSurfaceContainer,
            ),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                content()
            }
        }
    }
}