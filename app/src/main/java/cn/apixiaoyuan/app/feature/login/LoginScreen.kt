package cn.apixiaoyuan.app.feature.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController

/**
 * 登录页。
 *
 * 三条链路全部接到 [LoginViewModel]：
 *  - 密码登录：AuthRepository.loginByPassword
 *  - 短信登录：AuthRepository.loginBySms
 *  - 发验证码：AuthRepository.sendSmsCode
 *
 * 登录成功的副作用由 [LaunchedEffect] 监听 [LoginViewModel.loggedInUser]：
 * 一旦非空立即 popBackStack —— cookie 已由 PersistentCookieJar 落盘，
 * 返回上一页后所有请求自动带登录态，无需额外传递。
 *
 * 底部预留 96dp 给 LiquidGlassTabBar，避免表单被底栏遮住。
 * UI 只用 material3 组件，不引 miuix basic（本工程只依赖 miuix 的
 * blur / shader / nav 三个模块，basic 组件不在依赖里）。
 */
@Composable
fun LoginScreen(
    navController: NavHostController,
    viewModel: LoginViewModel = viewModel(),
) {
    // 登录成功即退出登录页；cookie 已落盘，无需回传任何东西。
    LaunchedEffect(viewModel.loggedInUser) {
        if (viewModel.loggedInUser != null) {
            navController.popBackStack()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 32.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "登录小猿口算",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "登录态由服务端 Cookie 承载，登录后自动持久化",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(8.dp))

        // ---- 模式切换：密码 / 短信 ----
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            LoginViewModel.Mode.entries.forEachIndexed { index, m ->
                SegmentedButton(
                    selected = viewModel.mode == m,
                    onClick = { viewModel.switchMode(m) },
                    shape = SegmentedButtonDefaults.itemShape(index, LoginViewModel.Mode.entries.size),
                ) {
                    Text(if (m == LoginViewModel.Mode.PASSWORD) "密码登录" else "验证码登录")
                }
            }
        }

        // ---- 手机号 ----
        OutlinedTextField(
            value = viewModel.phone,
            onValueChange = { viewModel.phone = it.filter { c -> c.isDigit() }.take(11) },
            label = { Text("手机号") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
        )

        // ---- 密码 / 验证码，按模式切换 ----
        when (viewModel.mode) {
            LoginViewModel.Mode.PASSWORD -> {
                OutlinedTextField(
                    value = viewModel.password,
                    onValueChange = { viewModel.password = it },
                    label = { Text("密码") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            LoginViewModel.Mode.SMS -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = viewModel.verification,
                        onValueChange = { viewModel.verification = it.filter { c -> c.isDigit() }.take(6) },
                        label = { Text("验证码") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = { viewModel.sendSmsCode() },
                        enabled = viewModel.canSendCode,
                    ) {
                        Text(
                            if (viewModel.countdown > 0) "${viewModel.countdown}s" else "获取验证码",
                        )
                    }
                }
            }
        }

        // ---- 错误提示 ----
        viewModel.errorMessage?.let { msg ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = msg,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // ---- 提交 ----
        androidx.compose.material3.Button(
            onClick = { viewModel.submit() },
            enabled = viewModel.canSubmit,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (viewModel.loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text(if (viewModel.mode == LoginViewModel.Mode.PASSWORD) "登录" else "验证码登录")
            }
        }
    }
}
