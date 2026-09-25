package cn.apixiaoyuan.app.feature.login

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.apixiaoyuan.app.core.auth.AuthRepository
import cn.apixiaoyuan.app.core.auth.AuthRepository.LoginOutcome
import cn.apixiaoyuan.app.core.model.UserVO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 登录页状态机。
 *
 * 把 [AuthRepository] 的四个方法包成 Compose 可观察状态。全部网络动作走
 * [viewModelScope]，页面销毁自动取消；发码倒计时用独立 [Job] 持有，
 * 重复点击先取消旧 Job，避免多个倒计时并发。
 *
 * 与仓库层的分工：本类只管「界面状态 + 输入校验 + 倒计时」，不碰 HTTP、
 * 不碰 cookie。业务码四态（成功/需认证/封禁/手机号不匹配）由
 * [AuthRepository] 判定，这里只做文案映射。
 */
class LoginViewModel : ViewModel() {

    /** 登录模式。密码与短信共用一套表单，切模式时清空验证码字段。 */
    enum class Mode { PASSWORD, SMS }

    var mode by mutableStateOf(Mode.PASSWORD)
        private set

    var phone by mutableStateOf("")
    var password by mutableStateOf("")
    var verification by mutableStateOf("")

    /** 是否正在请求（登录或发码），用于禁用按钮与显示进度。 */
    var loading by mutableStateOf(false)
        private set

    /** 错误提示文案。null 表示无错误。 */
    var errorMessage by mutableStateOf<String?>(null)
        private set

    /**
     * 成功后的用户资料。**可能为 null。**
     *
     * 直连版短信登录（`/accounts/android/safe/login`）的响应体是平铺账号
     * 对象、不含 `leoUserInfo`，因此它成功时这里仍是 null。
     * 判断「是否登录完成」请用 [loggedIn]，不要用本字段。
     */
    var loggedInUser by mutableStateOf<UserVO?>(null)
        private set

    /**
     * 是否已完成登录。UI 据此触发返回上一页。
     *
     * 与 [loggedInUser] 分开的原因：**登录完成 ≠ 拿到了用户资料**。
     * 网关版（`/leo-gateway/android/auth/{sms,password}`）成功时带回 `leoUserInfo`，
     * 直连版成功时没有。早先直接拿 `loggedInUser != null` 当跳转判据，
     * 会让直连版短信登录「请求成功、无报错、但页面不跳转」，卡在登录页。
     */
    var loggedIn by mutableStateOf(false)
        private set

    /** 发码倒计时剩余秒数。0 表示可以发码。 */
    var countdown by mutableStateOf(0)
        private set

    private var countdownJob: Job? = null

    /** 手机号基本校验：11 位且首位为 1。原版用 1[3-9]\d{9}，这里放宽到 11 位数字。 */
    val phoneValid: Boolean
        get() = phone.length == 11 && phone.all { it.isDigit() } && phone.startsWith("1")

    /** 当前模式下的提交按钮是否可用。 */
    val canSubmit: Boolean
        get() = phoneValid && when (mode) {
            Mode.PASSWORD -> password.isNotEmpty()
            Mode.SMS -> verification.isNotEmpty()
        } && !loading

    /** 是否可以发验证码。 */
    val canSendCode: Boolean
        get() = phoneValid && countdown == 0 && !loading

    fun switchMode(next: Mode) {
        if (mode == next) return
        mode = next
        errorMessage = null
    }

    fun clearError() {
        errorMessage = null
    }

    /**
     * 发短信验证码。成功后启动 60 秒倒计时。
     *
     * 失败提示不再笼统写「请检查手机号或网络」—— 服务端拒绝时把它的 message
     * 原样带出（实测风控层拒绝形如 `403 验证码获取失败`），否则用户与排查者
     * 都会被误导到错误的排查方向。
     */
    fun sendSmsCode() {
        if (!canSendCode) return
        loading = true
        errorMessage = null
        viewModelScope.launch {
            val outcome = AuthRepository.sendSmsCode(phone)
            loading = false
            when (outcome) {
                AuthRepository.SmsOutcome.Sent -> startCountdown()
                is AuthRepository.SmsOutcome.Rejected -> errorMessage =
                    outcome.serverMessage?.let { "验证码发送被拒（HTTP ${outcome.httpStatus}）：$it" }
                        ?: "验证码发送被拒（HTTP ${outcome.httpStatus}）"
                is AuthRepository.SmsOutcome.Failed -> errorMessage =
                    "验证码发送失败：${outcome.message}"
            }
        }
    }

    /** 提交登录。按当前模式走密码或短信链路。 */
    fun submit() {
        if (!canSubmit) return
        loading = true
        errorMessage = null
        viewModelScope.launch {
            val outcome = when (mode) {
                Mode.PASSWORD -> AuthRepository.loginByPassword(phone, password)
                Mode.SMS -> AuthRepository.loginBySms(phone, verification)
            }
            loading = false
            handleOutcome(outcome)
        }
    }

    private fun handleOutcome(outcome: LoginOutcome) {
        when (outcome) {
            // 两个字段必须同时置位：loggedIn 是「登录完成」的唯一判据（UI 跳转用），
            // loggedInUser 只是「顺带拿到的用户资料」，直连版短信登录时为 null。
            // 只置 loggedInUser 会导致直连版成功却永不跳转。
            is LoginOutcome.Success -> {
                loggedInUser = outcome.user
                loggedIn = true
            }
            LoginOutcome.NeedAuth -> errorMessage = "该账号需短信二次验证，请改用验证码登录"
            LoginOutcome.Banned -> errorMessage = "账号已被封禁"
            LoginOutcome.PhoneNotMatch -> errorMessage = "手机号与账号不匹配"
            is LoginOutcome.Failed -> errorMessage = outcome.message
        }
    }

    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            countdown = 60
            while (countdown > 0) {
                delay(1000L)
                countdown -= 1
            }
        }
    }

    override fun onCleared() {
        countdownJob?.cancel()
        super.onCleared()
    }
}
