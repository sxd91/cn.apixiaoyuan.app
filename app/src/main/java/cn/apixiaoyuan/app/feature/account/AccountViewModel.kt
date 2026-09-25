package cn.apixiaoyuan.app.feature.account

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.apixiaoyuan.app.core.account.AccountRepository
import cn.apixiaoyuan.app.core.account.SubAccountItem
import cn.apixiaoyuan.app.core.auth.AuthRepository
import cn.apixiaoyuan.app.core.model.UserVO
import cn.apixiaoyuan.app.core.session.SessionStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 账号页状态机：宝贝学习账号切换 + 改密码。
 *
 * ## 数据来源的一个硬约束
 *
 * 子账号 ID 列表**只能从登录响应里取**（`UserAccount.subUserInfos`），服务端
 * 没有单独的列表接口。这意味着：**用旧版本登录过、或升级前就已登录的用户，
 * 本地没有这份缓存，列表会是空的**。本类对此的处理是如实提示「重新登录一次
 * 以载入宝贝列表」，而不是假装列表为空 = 没有子账号。
 *
 * ## 切换账号后的副作用
 *
 * 切换成功后服务端会下发新的 `userid` cookie（PersistentCookieJar 自动落盘），
 * 所以必须**刷新 [SessionStore.yfdU] 缓存并重拉列表**，否则界面还显示旧账号为当前。
 */
class AccountViewModel : ViewModel() {

    /** 当前账号资料。null = 未拉到。 */
    var currentUser by mutableStateOf<UserVO?>(null)
        private set

    /** 宝贝学习账号列表。 */
    var subAccounts by mutableStateOf<List<SubAccountItem>>(emptyList())
        private set

    /** 是否正在加载。 */
    var loading by mutableStateOf(false)
        private set

    /** 错误提示。null = 无。 */
    var message by mutableStateOf<String?>(null)
        private set

    /** 改密码：手机号（默认取当前登录账号，可改）。 */
    var phone by mutableStateOf("")
    var verification by mutableStateOf("")
    var newPassword by mutableStateOf("")

    /** 是否正在提交改密码。 */
    var submittingPassword by mutableStateOf(false)
        private set

    /** 发码倒计时。 */
    var countdown by mutableStateOf(0)
        private set

    private var countdownJob: Job? = null

    init {
        refresh()
    }

    /** 重新拉取当前账号 + 宝贝列表。 */
    fun refresh() {
        if (loading) return
        loading = true
        message = null
        viewModelScope.launch {
            currentUser = AccountRepository.fetchCurrentUser()
            // 手机号初值：从登录态里推不出来（cookie 里没有手机号），
            // 留空让用户自己填，避免瞎猜。
            subAccounts = AccountRepository.fetchSubAccounts()
            loading = false
            if (subAccounts.isEmpty() && SessionStore.subUserIds().isEmpty()) {
                message = "本地没有宝贝账号列表缓存（服务端无独立列表接口）。" +
                    "请重新登录一次以载入。"
            }
        }
    }

    /** 切换到指定宝贝学习账号。 */
    fun switchTo(item: SubAccountItem) {
        if (loading || item.isCurrent) return
        loading = true
        message = null
        viewModelScope.launch {
            val result = AccountRepository.switchAccount(item.userId)
            loading = false
            result.onSuccess { resp ->
                if (resp.isSuccess) {
                    // 服务端已换 cookie，本地缓存要跟上，否则界面仍标旧账号为当前。
                    SessionStore.saveYfdU(item.userId.toLong())
                    message = "已切换到「${item.nickname}」"
                    refresh()
                } else {
                    message = "切换失败（code=${resp.code}）"
                }
            }.onFailure {
                message = "切换失败：${it.message ?: it}"
            }
        }
    }

    /** 新建宝贝学习账号。 */
    fun createSubAccount() {
        if (loading) return
        loading = true
        message = null
        viewModelScope.launch {
            val result = AccountRepository.createSubAccount()
            loading = false
            result.onSuccess { account ->
                SessionStore.saveYfdU(account.id.toLong())
                runCatching {
                    val ids = account.subUserInfos?.project2SubUserInfo?.get("6")?.subUserIds
                    if (!ids.isNullOrEmpty()) SessionStore.saveSubUserIds(ids)
                }
                message = "已新建并切换到新宝贝账号"
                refresh()
            }.onFailure {
                message = "新建失败：${it.message ?: it}"
            }
        }
    }

    /** 删除宝贝学习账号（需短信验证码）。 */
    fun deleteSubAccount(item: SubAccountItem, verification: String) {
        if (loading) return
        val primary = currentUser?.primaryUserId ?: return
        loading = true
        message = null
        viewModelScope.launch {
            val result = AccountRepository.deleteSubAccount(
                subUserId = item.userId,
                primaryUserId = primary,
                verification = verification,
            )
            loading = false
            result.onSuccess {
                message = "已删除「${item.nickname}」"
                refresh()
            }.onFailure {
                message = "删除失败：${it.message ?: it}"
            }
        }
    }

    /** 改密码：发验证码。 */
    fun sendSmsCode() {
        if (countdown > 0 || phone.length != 11) return
        viewModelScope.launch {
            when (val outcome = AuthRepository.sendSmsCode(phone)) {
                AuthRepository.SmsOutcome.Sent -> startCountdown()
                is AuthRepository.SmsOutcome.Rejected ->
                    message = outcome.serverMessage?.let { "发码被拒（${outcome.httpStatus}）：$it" }
                        ?: "发码被拒（${outcome.httpStatus}）"
                is AuthRepository.SmsOutcome.Failed -> message = "发码失败：${outcome.message}"
            }
        }
    }

    /** 改密码：提交。 */
    fun resetPassword() {
        if (submittingPassword) return
        if (phone.length != 11 || verification.isBlank() || newPassword.length < 6) {
            message = "请填写 11 位手机号、验证码，以及至少 6 位新密码"
            return
        }
        submittingPassword = true
        message = null
        viewModelScope.launch {
            val result = AccountRepository.resetPassword(phone, verification, newPassword)
            submittingPassword = false
            result.onSuccess {
                message = "密码已修改"
                verification = ""
                newPassword = ""
            }.onFailure {
                message = "改密码失败：${it.message ?: it}"
            }
        }
    }

    fun clearMessage() {
        message = null
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