package cn.apixiaoyuan.app.feature.pk

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.apixiaoyuan.app.core.pk.PkRepository
import cn.apixiaoyuan.app.core.session.SessionStore
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement

/**
 * PK 页状态机。
 *
 * 两条数据：
 *  - [h5Url]：H5 入口地址，由 [PkRepository.pkH5Url] 拼好，原生侧唯一构造物
 *  - [entryData]：PK 入口数据（`/leo-game-pk/android/game/homepage` 的原始 JSON）
 *
 * 关键设计：**H5 加载不依赖 entryData**。即使入口数据拉失败（未登录、
 * 接口变更），H5 页面本身仍应能加载 —— 它会用自己的方式处理登录态
 * （cookie 由 WebView 的 CookieManager 与原生共享，见 `PkH5Screen`）。
 * 所以两个状态互不阻塞：URL 立即就绪，entryData 异步补充。
 *
 * 答题协议不在原生侧 —— PK 交互全在 H5 内部，原生只做容器与埋点。
 */
class PkViewModel : ViewModel() {

    /** H5 入口 URL。构造即就绪，不为空。 */
    var h5Url by mutableStateOf(PkRepository.pkH5Url())
        private set

    /** PK 入口数据。null 表示未拉到或失败 —— 不阻塞 H5 加载。 */
    var entryData by mutableStateOf<JsonElement?>(null)
        private set

    /** 入口数据的原始 JSON 文本，供调试展示。 */
    var entryRaw by mutableStateOf<String?>(null)
        private set

    var loading by mutableStateOf(false)
        private set

    /** H5 加载进度（0..100）。用于顶部进度条。 */
    var webProgress by mutableStateOf(0)
        private set

    /** H5 当前页面标题。 */
    var webTitle by mutableStateOf("口算 PK")

    /** H5 加载失败时的错误（主文档级）。 */
    var webError by mutableStateOf<String?>(null)

    /**
     * 主域登录态自检结果。null = 还没探过。
     *
     * false 时 UI 应提示「请先导入登录态」而不是让用户对着白屏 ——
     * PK 的 401 是**认证**问题（`SolarAuthFilter`），设备链只能用户从原版导入，
     * 不提示的话用户无从得知该做什么。
     */
    var authOk by mutableStateOf<Boolean?>(null)
        private set

    /**
     * 加载 PK：先自检主域登录态，再拉入口数据。
     *
     * 顺序有意为之：自检失败时 H5 大概率也是未登录态，
     * 先跑探针能给出**明确的原因**（缺设备链 / 未登录），
     * 而不是让用户看一个加载失败的 H5 猜。
     */
    fun loadEntry() {
        if (loading) return
        loading = true
        viewModelScope.launch {
            authOk = PkRepository.probeAuth()
            val grade = SessionStore.grade() ?: DEFAULT_GRADE
            val data = PkRepository.fetchPkEntry(grade)
            entryData = data
            entryRaw = data?.toString()?.take(20_000)
            loading = false
        }
    }

    fun setProgress(p: Int) {
        webProgress = p.coerceIn(0, 100)
    }

    /** 重新加载 H5（错误页的「重试」按钮）。 */
    fun reload() {
        webError = null
        webProgress = 0
        h5Url = PkRepository.pkH5Url()
    }

    companion object {
        /** 年级：2。真机 UserVO.grade 实测值，待会话存储落盘后从 UserVO 读。 */
        private const val DEFAULT_GRADE = 2
    }
}