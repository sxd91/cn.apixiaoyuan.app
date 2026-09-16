package cn.apixiaoyuan.app.core.auth

import cn.apixiaoyuan.app.core.model.LoginResponse
import cn.apixiaoyuan.app.core.model.UserVO
import cn.apixiaoyuan.app.core.network.ApiException
import cn.apixiaoyuan.app.core.network.ServiceLocator
import cn.apixiaoyuan.app.core.session.SessionStore

/**
 * 登录 / 登出 / 短信验证码的唯一入口。
 *
 * 链路（R2 落地后的完整形态）：
 *  1. 调 [ServiceLocator.gateway] 的登录方法；
 *  2. OkHttp 的 [cn.apixiaoyuan.app.core.session.PersistentCookieJar] 在响应返回时
 *     自动把 `Set-Cookie` 落进 [SessionStore] —— 这一步不需要本类插手；
 *  3. 本类校验业务码（`code == 1` 才是成功，不是 [cn.apixiaoyuan.app.core.network.CODE_SUCCESS]）；
 *  4. 成功后拉一次 [UserVO] 确认登录态可用，失败则清空 [SessionStore]。
 *
 * 注意与 [cn.apixiaoyuan.app.core.network.ApiResult] 的关系：登录响应用的是
 * [LoginResponse] 这套「code==1 成功」的旧信封，不走 `Envelope<T>`。所以这里
 * 直接返回 [LoginOutcome]，让调用方拿到结构化的四态结果，而不是把 code 判断
 * 散到 UI 层。
 */
object AuthRepository {

    /**
     * 登录结果。四态与 [LoginResponse] 的 code 一一对应，外加一个网络失败态。
     */
    sealed interface LoginOutcome {
        /** `code == 1`，登录成功。[user] 是随登录响应带回的 `leoUserInfo`。 */
        data class Success(val user: UserVO?) : LoginOutcome

        /** `code == 2`，需要短信二次验证。 */
        data object NeedAuth : LoginOutcome

        /** `code == 3`，账号封禁。 */
        data object Banned : LoginOutcome

        /** `code == 10`，手机号不匹配。 */
        data object PhoneNotMatch : LoginOutcome

        /** 其他业务码或传输层失败。[message] 用于直接展示。 */
        data class Failed(val code: Int, val message: String) : LoginOutcome
    }

    /**
     * 密码登录。
     *
     * @param phone    手机号
     * @param password 密码（明文，原版就是明文 POST，未观察到客户端加密）
     * @param yfdU     猿辅导用户 ID。**首次登录传 0** —— 原版 [LeoGatewayService.passwordLogin]
     *                 的 `@Query("YFD_U")` 在未登录场景下就是 0。
     */
    suspend fun loginByPassword(
        phone: String,
        password: String,
        yfdU: Long = 0L,
    ): LoginOutcome = runLogin {
        ServiceLocator.gateway.passwordLogin(
            yfdU = yfdU,
            phone = phone,
            password = password,
        )
    }

    /**
     * 短信验证码登录。
     *
     * @param autoRegister 未注册手机号是否自动注册（原版默认行为取决于入口，这里由调用方指定）
     */
    suspend fun loginBySms(
        phone: String,
        verification: String,
        autoRegister: Boolean = false,
        yfdU: Long? = null,
    ): LoginOutcome = runLogin {
        ServiceLocator.gateway.smsLogin(
            yfdU = yfdU ?: SessionStore.yfdU,
            phone = phone,
            verification = verification,
            autoRegister = autoRegister,
        )
    }

    /**
     * 发送短信验证码。
     *
     * 走账号域 [ServiceLocator.ytkApi] 的 `/verifier/android/sms`。
     * 返回 true 表示请求已发出且无异常；服务端是否真的发了短信由后续登录验证。
     */
    suspend fun sendSmsCode(phone: String): Boolean = try {
        ServiceLocator.ytkApi.smsVerify(
            yfdU = SessionStore.yfdU,
            phone = phone,
        ).execute().isSuccessful
    } catch (e: Throwable) {
        false
    }

    /**
     * 登出。
     *
     * 先调服务端清会话（两条链路都试，失败不阻塞），再清本地 [SessionStore]。
     * 顺序不能反 —— 先清本地就拿不到 cookie 去调服务端了。
     */
    suspend fun logout() {
        runCatching { ServiceLocator.ytkApi.logout() }
        runCatching { ServiceLocator.profile.leoLogout() }
        SessionStore.clear()
    }

    /**
     * 拉当前用户资料。登录态可用时返回 [UserVO]，否则 null。
     *
     * 用于「冷启动时确认 cookie 还有效」—— 若返回 401，[ApiException.Unauthorized]
     * 由调用方捕获后清 [SessionStore]。
     */
    suspend fun fetchCurrentUser(): UserVO? = runCatching {
        ServiceLocator.profile.getUserInfo()
    }.getOrNull()

    /**
     * 公共登录收尾：校验业务码 → 失败清空会话 → 成功返回结构化结果。
     *
     * cookie 的落盘由 [cn.apixiaoyuan.app.core.session.PersistentCookieJar] 在
     * OkHttp 层完成，这里不再重复处理。
     */
    private suspend fun runLogin(block: suspend () -> LoginResponse): LoginOutcome = try {
        val response = block()
        when {
            response.isSuccess -> LoginOutcome.Success(response.body?.leoUserInfo)
            response.needAuth -> LoginOutcome.NeedAuth
            response.isBanned -> {
                SessionStore.clear()
                LoginOutcome.Banned
            }
            response.phoneNotMatch -> {
                SessionStore.clear()
                LoginOutcome.PhoneNotMatch
            }
            else -> LoginOutcome.Failed(
                code = response.code,
                message = "登录失败（code=${response.code}）",
            )
        }
    } catch (e: ApiException.Http) {
        LoginOutcome.Failed(e.status, "HTTP ${e.status}")
    } catch (e: ApiException.Network) {
        LoginOutcome.Failed(-1, "网络错误：${e.message}")
    } catch (e: ApiException.Parse) {
        LoginOutcome.Failed(-2, "响应解析失败")
    } catch (e: Throwable) {
        LoginOutcome.Failed(-3, e.message ?: "未知错误")
    }
}
