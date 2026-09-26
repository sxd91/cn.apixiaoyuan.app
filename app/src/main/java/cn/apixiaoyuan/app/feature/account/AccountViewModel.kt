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
 * ## 子账号列表的来源（2026-09-26 修正）
 *
 * 直接调主域无参接口 `GET /leo-profile/android/user-infos/batchGet`
 * （原版 `LeoProfileApiService.getSubAccounts()`），按当前 cookie 返回
 * 账号名下的全部 `UserVO`。
 *
 * 此前依赖登录响应里的 `UserAccount.subUserInfos.project2SubUserInfo["6"].subUserIds`
 * 拿 ID 列表 —— 该字段在 3.141.1 的 dex 里**根本不存在**，导致 ID 列表恒空、
 * 列表恒空，还多出一条「请重新登录以载入」的假提示。现在这条链路已整体删除，
 * 不再有「必须先登录过才看得到」的限制。
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
            //
            // 宝贝列表：把失败原因**说出来**，不再静默吞成空表 —— 否则用户看到
            // 的永远是「没有可显示的宝贝账号」，分不清是没有小号还是请求挂了。
            AccountRepository.fetchSubAccounts()
                .onSuccess { subAccounts = it }
                .onFailure {
                    subAccounts = emptyList()
                    message = "宝贝账号列表拉取失败：${it.message ?: it}"
                }
            loading = false
        }
    }

    /** 切换到指定宝贝学习账号。 */
    fun switchTo(item: SubAccountItem) {
        if (loading || item.isCurrent) return
        loading = true
        message = null
        viewModelScope.launch {
            val result = runCatching { AccountRepository.switchTo(item) }
            loading = false
            result.onSuccess { newId ->
                message = "已切换到「${item.nickname}」（userid=$newId）"
                refresh()
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
        // 主账号 ID 优先取列表项自带的（同一份 `UserVO` 数据，一定准确），
        // 退而取当前账号资料的 —— 两者都不存在才放弃。
        val primary = item.primaryUserId.takeIf { it > 0 }
            ?: currentUser?.primaryUserId
            ?: return
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
                // 不再手工维护本地 ID 缓存：列表现在每次 refresh 都直接向服务端要，
                // 服务端删掉后自然不再返回该账号。
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
     * **这是本项目拿到主域业务权限的必要途径** —— 主域业务端点（练习 / 出题 /
     * 资料）的认证是两层链，缺第一层直接 401：
     *
     * | 携带的 cookie | 结果 |
     * |---|---|
     * | 完整（登录 cookie + `sid` + `ks_*`） | 进入编码层（417 说明认证已过） |
     * | 去掉 `sid` + 全部 `ks_*` | 401 `x-block-by: leo-auth` |
     * | 不带 | 401 `x-block-by: fenbi-auth` |
     *
     * ⚠️ 曾经误删过这个入口：当时只测了 `rank/pre-fetch`（服务端**路径白名单特例**，
     * 任何 cookie 都 200），据此错误地得出「主域不需要设备链」。用真实业务端点
     * 复测后已纠正并恢复。
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
        message = "已导入 $n 条 cookie。设备链与登录 cookie 两层齐备后，主域业务接口才可用。"
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