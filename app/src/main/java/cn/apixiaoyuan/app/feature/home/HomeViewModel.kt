package cn.apixiaoyuan.app.feature.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.apixiaoyuan.app.core.account.AccountRepository
import cn.apixiaoyuan.app.core.account.SubAccountItem
import cn.apixiaoyuan.app.core.session.SessionStore
import kotlinx.coroutines.launch

/**
 * 主页状态机：子账号卡片列表的加载与切换。
 *
 * ## 为什么主页需要一个 ViewModel
 *
 * 用户新需求（2026-09-26）：子账号卡片直接显示在主页「已登录卡片」下方，
 * 点哪个切哪个。这需要：
 *  1. 登录后 / 进入主页时拉一次子账号列表（`batchGet`，无参）；
 *  2. 切换账号（调 switch + 写回 userid cookie）后刷新列表。
 *
 * 这些是异步网络操作，不能塞进 Composable。与账号页 [AccountViewModel] 的关系：
 * 两个页面各自维护一份列表状态，但**切换逻辑共用** [AccountRepository.switchTo]，
 * 不会行为漂移。
 *
 * ## 刷新时机（关键）
 *
 * 监听 [SessionStore.stateRevision]（会话版本戳）—— 登录 / 登出 / 切号 /
 * 导入 cookie 都会自增。主页在 `init` 里用 `viewModelScope` 观察它，
 * 每次变化都重拉列表。这样「在账号页切换后返回主页」卡片能立即更新，
 * 不需要手动通知。
 */
class HomeViewModel : ViewModel() {

    /** 子账号列表。空 = 未登录或没有子账号。 */
    var subAccounts by mutableStateOf<List<SubAccountItem>>(emptyList())
        private set

    /** 是否正在拉取子账号列表。 */
    var loadingAccounts by mutableStateOf(false)
        private set

    /** 切换/拉取的提示。null = 无。 */
    var message by mutableStateOf<String?>(null)
        private set

    /** 是否正在切换账号。 */
    private var switching by mutableStateOf(false)

    init {
        refreshAccounts()
        // 会话版本戳变化（登录 / 登出 / 切号 / 导入 cookie）→ 重拉列表。
        // viewModelScope 里观察 mutableStateOf，每次 revision 变化都会触发。
        viewModelScope.launch {
            var last = SessionStore.stateRevision
            while (true) {
                val cur = SessionStore.stateRevision
                if (cur != last) {
                    last = cur
                    refreshAccounts()
                }
                kotlinx.coroutines.delay(200)
            }
        }
    }

    /** 拉取子账号列表（未登录时直接清空，不发请求）。 */
    fun refreshAccounts() {
        if (loadingAccounts) return
        if (!SessionStore.isLoggedIn) {
            subAccounts = emptyList()
            return
        }
        loadingAccounts = true
        viewModelScope.launch {
            AccountRepository.fetchSubAccounts()
                .onSuccess { subAccounts = it }
                .onFailure { subAccounts = emptyList() }
            loadingAccounts = false
        }
    }

    /** 切换到指定子账号。 */
    fun switchTo(item: SubAccountItem) {
        if (switching || item.isCurrent) return
        switching = true
        message = null
        viewModelScope.launch {
            val result = runCatching { AccountRepository.switchTo(item) }
            switching = false
            result.onSuccess { newId ->
                message = "已切换到「${item.nickname}」"
                // SessionStore.stateRevision 已被 switchTo 里的 saveYfdU/upsertCookie
                // 自增，init 的观察协程会自动重拉列表；这里不重复刷新。
            }.onFailure {
                message = "切换失败：${it.message ?: it}"
            }
        }
    }

    fun clearMessage() {
        message = null
    }
}