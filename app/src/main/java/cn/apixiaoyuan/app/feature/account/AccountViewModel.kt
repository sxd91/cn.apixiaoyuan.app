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
import cn.apixiaoyuan.app.core.oldsimian.OldSimianPrefs
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

    /** 导入登录态用的文本（标准 Cookie 头形态）。 */
    var cookieInput by mutableStateOf("")

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

    /** 改名：待提交的新昵称（初值取当前昵称）。 */
    var nicknameInput by mutableStateOf("")
    /** 是否正在提交改名。 */
    var submittingNickname by mutableStateOf(false)
        private set

    init {
        refresh()
    }

    /** 重新拉取当前账号 + 宝贝列表。 */
    fun refresh() {
        if (loading) return
        loading = true
        message = null
        viewModelScope.launch {
            val user = AccountRepository.fetchCurrentUser()
            currentUser = user
            // 改名输入框初值：首次拉到昵称时回填，之后不覆盖用户正在编辑的内容
            // （避免 refresh 把用户敲了一半的昵称冲掉）。
            if (nicknameInput.isBlank()) nicknameInput = user?.nickname.orEmpty()
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
                // 服务端已从 subUserIds 摘掉该账号，本地缓存要同步 ——
                // 否则列表里还会残留一条（refresh 时 batchGet 已查不到它）。
                SessionStore.saveSubUserIds(
                    SessionStore.subUserIds().filterNot { it == item.userId },
                )
                refresh()
            }.onFailure {
                message = "删除失败：${it.message ?: it}"
            }
        }
    }

    /**
     * 删除流程的发码。
     *
     * 与改密码共用同一套发码链路（`/verifier/android/sms`），
     * 但**独立的倒计时**：两处同时发码会被服务端频控，这里用同一个
     * [countdown] 状态即可（同一时刻只可能有一个删除弹窗在开）。
     */
    fun sendDeleteSmsCode() {
        if (countdown > 0 || phone.length != 11) return
        viewModelScope.launch {
            when (val outcome = AccountRepository.sendDeleteSmsCode(phone)) {
                AuthRepository.SmsOutcome.Sent -> startCountdown()
                is AuthRepository.SmsOutcome.Rejected ->
                    message = outcome.serverMessage?.let { "发码被拒（${outcome.httpStatus}）：$it" }
                        ?: "发码被拒（${outcome.httpStatus}）"
                is AuthRepository.SmsOutcome.Failed -> message = "发码失败：${outcome.message}"
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

    /**
     * 提交改名。
     *
     * ## 与「无视名字限制」的关系（用户明确要求合并）
     *
     * `OldSimianPrefs.ignoreNicknameRestriction` 打开时，**跳过客户端侧的全部昵称
     * 校验** —— 长度、字符、敏感词都不拦，直接把原样昵称发给服务端。这就是
     * 「原生无限制改名」：限制与否只由服务端判定，客户端不做二次裁剪。
     *
     * 关闭时走一套保守校验（长度 ≤ 16、非空），与参考项目 `cn.nizou.sxd` 的
     * 昵称口径一致 —— 不自行发明字符集规则，避免误拦合法昵称。
     *
     * 无论开关如何，空昵称一律拦下：`PUT /leo-profile/android/user-infos` 收到空串
     * 会把昵称清空，那是不可逆的破坏性操作，不该由一次误触触发。
     */
    fun rename() {
        if (submittingNickname) return
        val name = nicknameInput.trim()
        if (name.isEmpty()) {
            message = "昵称不能为空"
            return
        }
        val unlimited = OldSimianPrefs.ignoreNicknameRestriction
        if (!unlimited && name.length > NICKNAME_MAX_LENGTH) {
            message = "昵称最长 $NICKNAME_MAX_LENGTH 个字符（当前 ${name.length}）。" +
                "需要更长请打开「无视名字限制」。"
            return
        }
        submittingNickname = true
        message = null
        viewModelScope.launch {
            val result = AccountRepository.updateNickname(name)
            submittingNickname = false
            result.onSuccess { user ->
                currentUser = user
                nicknameInput = user.nickname ?: name
                message = "昵称已修改为「${user.nickname ?: name}」"
                refresh()
            }.onFailure {
                message = "改名失败：${it.message ?: it}"
            }
        }
    }

    /**
     * 客户端侧昵称长度上限（仅在「无视名字限制」关闭时生效）。
     *
     * 取 16 而不是别的值：参考项目 `cn.nizou.sxd` 的昵称限制是 GBK 字节 ≤ 16
     * （2026-08-29 合并的双倍昵称长度），本项目按字符数对齐到同一量级。
     * 打开开关后此上限不再生效，由服务端裁决。
     */
    companion object {
        private const val NICKNAME_MAX_LENGTH = 16
    }

    fun clearMessage() {
        message = null
    }

    /**
     * 导入登录态（标准 `Cookie` 头形态）。
     *
     * **这是本项目拿到主域权限的唯一途径** —— 主域认证需要
     * `sid` + `ks_sess` + `ks_deviceid` 三者齐备，而它们只由原版 App 的
     * 登录通道下发，本项目自己拿不到（详见
     * [cn.apixiaoyuan.app.core.session.SessionStore.importCookieHeader] 的 KDoc）。
     *
     * 导入后立刻刷新，让用户马上看到效果。
     */
    fun importCookies() {
        val text = cookieInput.trim()
        if (text.isEmpty()) {
            message = "请先粘贴 Cookie 字符串"
            return
        }
        val n = SessionStore.importCookieHeader(text)
        if (n == 0) {
            message = "没能解析出任何 cookie（应为 name=value; name2=value2 形态）"
            return
        }
        cookieInput = ""
        message = "已导入 $n 条 cookie。若主域接口仍报 401，说明缺少 sid / ks_sess / ks_deviceid。"
        refresh()
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