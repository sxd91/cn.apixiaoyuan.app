package cn.apixiaoyuan.app.feature.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.Button
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
import cn.apixiaoyuan.app.core.navigation.AppNavController
import cn.apixiaoyuan.app.core.design.component.AppScrollScaffold

/**
 * 登录页。
 *
 * 三条链路全部接到 [LoginViewModel]：
 *  - 密码登录：AuthRepository.loginByPassword
 *  - 短信登录：AuthRepository.loginBySms
 *  - 发验证码：AuthRepository.sendSmsCode
 *
 * 登录成功的副作用由 [LaunchedEffect] 监听 [LoginViewModel.loggedIn]：
 * 一旦为 true 立即 popBackStack —— cookie 已由 PersistentCookieJar 落盘，
 * 返回上一页后所有请求自动带登录态，无需额外传递。
 *
 * 判据必须是 [LoginViewModel.loggedIn] 而非 [LoginViewModel.loggedInUser]：
 * 直连版短信登录（`/accounts/android/safe/login`）成功时响应体不含
 * `leoUserInfo`，`loggedInUser` 仍为 null，用它当判据会导致「登录成功但
 * 页面不跳转」。
 *
 * 顶栏与返回键由 [AppScaffold] 统一提供，statusBars 留白由它负责；
 * 悬浮玻璃底栏是浮层，内容不再为它预留 96dp。
 */
@Composable
fun LoginScreen(
    navController: AppNavController,
    viewModel: LoginViewModel = viewModel(),
) {
    LaunchedEffect(viewModel.loggedIn) {
        if (viewModel.loggedIn) {
            navController.popBackStack()
        }
    }

    AppScrollScaffold(title = "登录", onBack = { navController.popBackStack() }) {
        Column(
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

            OutlinedTextField(
                value = viewModel.phone,
                onValueChange = { viewModel.phone = it.filter { c -> c.isDigit() }.take(11) },
                label = { Text("手机号") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
            )

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

            Button(
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
}
