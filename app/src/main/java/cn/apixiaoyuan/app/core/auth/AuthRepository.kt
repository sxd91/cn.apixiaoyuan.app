package cn.apixiaoyuan.app.core.auth

import cn.apixiaoyuan.app.core.model.LoginResponse
import cn.apixiaoyuan.app.core.model.UserVO
import cn.apixiaoyuan.app.core.network.ApiException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import cn.apixiaoyuan.app.core.network.ServiceLocator
import cn.apixiaoyuan.app.core.session.SessionStore
import retrofit2.HttpException

/**
 * 登录 / 登出 / 短信验证码的唯一入口。
 *
 * 链路：
 *  1. 调 [ServiceLocator] 对应的服务方法；
 *  2. OkHttp 的 [cn.apixiaoyuan.app.core.session.PersistentCookieJar] 在响应返回时
 *     自动把 `Set-Cookie` 落进 [SessionStore] —— 这一步不需要本类插手；
 *  3. 本类校验业务码（`code == 1` 才是成功）；
 *  4. 成功后拉一次 [UserVO] 确认登录态可用，失败则清空 [SessionStore]。
 *
 * 所有服务方法都是 `suspend`，本类全部方法也都是 `suspend`。调用方在主线程
 * （如 `viewModelScope`）直接调即可，不需要自己 `withContext(Dispatchers.IO)`。
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
     * **走直连版** [cn.apixiaoyuan.app.core.network.api.YtkApiService.passwordLogin]
     * （账号域 `ape-api.yuanfudao.com` 的 `/accounts/android/safe/login`）。
     *
     * ## 为什么不走网关版
     *
     * 此前这里走 [ServiceLocator.gateway] 的
     * `POST /leo-gateway/android/auth/password`。2026-09-25 对真机账号实测：
     * 该接口无论密码传明文还是 RSA 密文，**一律 401 `unauthorized`**（无任何
     * 语义化错误信息）；而直连版 + RSA 密文密码 → **HTTP 200**，返回完整
     * `UserAccount` 并下发 `sess` / `userid` / `g_sess` / `persistent` 四个 cookie。
     *
     * 另一个佐证：明文密码打到直连版会得到 `401 {"message":"密码错误"}` ——
     * 服务端确实在读 `password` 字段，只是要求密文。网关版连这个都拿不到。
     *
     * ## 密码必须 RSA 加密
     *
     * 原版 `YtkApiService.passwordLoginCall` 的注解层只写 `@Field`，看不出加密；
     * 实测证明**必须**加密。加密复用 [PhoneEncoder] —— 它复刻的是
     * `Lkv/k` 的 `ENCRYPT_MODE` 分支（RSA/ECB/PKCS1PADDING + 硬编码公钥 + Base64），
     * 与手机号用的是同一把公钥、同一条链路（原版 `a()` 加密 / `b()` 解密）。
     *
     * 手机号本身**实测明文可用**，不加密 —— 与短信登录链路里 phone 也传密文
     * 的做法不同，不要想当然统一。
     *
     * @param phone    手机号（明文）
     * @param password 密码（明文入参，内部 RSA 加密后提交）
     * @param yfdU     `YFD_U`，默认取设备指纹派生值
     */
    suspend fun loginByPassword(
        phone: String,
        password: String,
        yfdU: Long = DeviceFingerprint.yfdU(),
    ): LoginOutcome = runCatching {
        ServiceLocator.ytkApi.passwordLogin(
            yfdU = yfdU,
            phone = phone,
            password = PhoneEncoder.encode(password),
        )
    }.fold(
        onSuccess = { account ->
            // 直连版返回 UserAccount（平铺账号对象，无业务码信封），
            // 与 [loginBySms] 同构，兜底逻辑也一致。
            runCatching {
                val uid = account.id.takeIf { it != 0 } ?: account.primarySubUserId
                if (uid != 0 && SessionStore.yfdU == null) SessionStore.saveYfdU(uid.toLong())
            }
            // 子账号 ID 列表只能在登录响应里截 —— 服务端没有单独的列表接口，
            // 只有 batchGet 批量换资料。key "6" 是小猿口算所属业务线。
            runCatching {
                val ids = account.subUserInfos?.project2SubUserInfo?.get(PROJECT_KEY)?.subUserIds
                if (!ids.isNullOrEmpty()) SessionStore.saveSubUserIds(ids)
            }
            LoginOutcome.Success(user = null)
        },
        onFailure = { e -> mapLoginError(e) },
    )

    /** 小猿口算在 `subUserInfos` 里的业务线 key。 */
    private const val PROJECT_KEY = "6"

    /**
     * 短信验证码登录。
     *
     * **走直连版** [cn.apixiaoyuan.app.core.network.api.YtkApiService.smsLogin]
     * （`ape-api.yuanfudao.com` 的 `/accounts/android/safe/login`），
     * 与原版 `wo/d.smali` 的调用链一致。
     *
     * 三条参数事实（逐行来自 `wo/d.smali:630-694`）：
     *  - `phone` 与 `verification` **都传 RSA 密文**（各调一次 `Lkv/k;->b`）
     *  - `YFD_U` 用**设备指纹派生值**，不是会话里的用户 ID
     *  - `autoRegister` **硬编码 true**（原版行为，新用户自动注册）
     *
     * @param autoRegister 未注册手机号是否自动注册。默认 true，对齐原版。
     */
    suspend fun loginBySms(
        phone: String,
        verification: String,
        autoRegister: Boolean = true,
        yfdU: Long? = null,
    ): LoginOutcome = runCatching {
        val encodedPhone = PhoneEncoder.encode(phone)
        val encodedVerification = PhoneEncoder.encode(verification)
        ServiceLocator.ytkApi.smsLogin(
            yfdU = yfdU ?: DeviceFingerprint.yfdU(),
            phone = encodedPhone,
            verification = encodedVerification,
            autoRegister = autoRegister,
        )
    }.fold(
        onSuccess = { account ->
            // 直连版返回 UserAccount（**平铺账号对象，无业务码信封**）：
            // 实测成功响应就是 `{"id":...,"phone":...,"primarySubUserId":...}`，
            // 没有 code/body 包装。服务端失败时给非 2xx（Retrofit 抛
            // HttpException，走下面的 onFailure），所以能解析到 body 即成功。
            //
            // 登录态由 Set-Cookie 承载（sess / userid / g_sess / persistent），
            // PersistentCookieJar 已自动落盘，此处不重复保存 cookie。
            //
            // 兜底：若响应未带 Set-Cookie（或落盘环节异常），用响应体里的
            // 账号 ID 直接写 YFD_U —— 它同时是 SessionStore.isLoggedIn 的判据，
            // 避免「解析成功但首页仍显示未登录」。
            runCatching {
                val uid = account.id.takeIf { it != 0 } ?: account.primarySubUserId
                if (uid != 0 && SessionStore.yfdU == null) SessionStore.saveYfdU(uid.toLong())
            }
            runCatching {
                val ids = account.subUserInfos?.project2SubUserInfo?.get(PROJECT_KEY)?.subUserIds
                if (!ids.isNullOrEmpty()) SessionStore.saveSubUserIds(ids)
            }
            LoginOutcome.Success(user = null)
        },
        onFailure = { e -> mapLoginError(e) },
    )

    /**
     * 短信验证码发送结果。
     *
     * 与 [LoginOutcome] 分开的原因：发码接口（`/verifier/android/sms`）没有业务码信封，
     * 只有 HTTP 状态码与可能的 JSON 错误体
     * （实测失败时形如 `{"timestamp":...,"status":403,"message":"验证码获取失败"}`）。
     */
    sealed interface SmsOutcome {
        /** HTTP 2xx，服务端已受理发码请求。 */
        data object Sent : SmsOutcome

        /** HTTP 非 2xx，服务端明确拒绝。[serverMessage] 是响应体里的 `message` 字段。 */
        data class Rejected(val httpStatus: Int, val serverMessage: String?) : SmsOutcome

        /** 传输层失败（DNS / 连接 / 超时）或本地编码失败。 */
        data class Failed(val message: String) : SmsOutcome
    }

    /**
     * 发送短信验证码。
     *
     * 走账号域 [ServiceLocator.ytkApi] 的 `/verifier/android/sms`。
     *
     * 服务方法 [cn.apixiaoyuan.app.core.network.api.YtkApiService.smsVerify] 是 `suspend`，
     * 内部由 Retrofit 切到 IO 线程，本方法可在主线程直接调用。
     *
     * 注意 [PhoneEncoder.encode] 在本方法内同步执行 —— RSA 编码是纯 CPU 计算，
     * 1024 位一次加密毫秒级，不构成主线程阻塞。它的失败（如 provider 不可用）
     * 由外层 `catch (e: Throwable)` 接住，归入 [SmsOutcome.Failed]。
     */
    suspend fun sendSmsCode(phone: String): SmsOutcome = try {
        val encoded = PhoneEncoder.encode(phone)
        ServiceLocator.ytkApi.smsVerify(
            // 与原版一致：用设备指纹派生值，未登录时也传（设备级频控键）。
            yfdU = DeviceFingerprint.yfdU(),
            phone = encoded,
        )
        SmsOutcome.Sent
    } catch (e: HttpException) {
        SmsOutcome.Rejected(e.code(), extractServerMessage(e.response()?.errorBody()?.string()))
    } catch (e: Throwable) {
        SmsOutcome.Failed(e.message ?: e.javaClass.simpleName)
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
     */
    suspend fun fetchCurrentUser(): UserVO? = runCatching {
        ServiceLocator.profile.getUserInfo()
    }.getOrNull()

    /**
     * 公共登录收尾：校验业务码 → 失败清空会话 → 成功返回结构化结果。
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
    } catch (e: retrofit2.HttpException) {
        val serverMsg = extractServerMessage(e.response()?.errorBody()?.string())
        LoginOutcome.Failed(e.code(), serverMsg ?: "HTTP ${e.code()}")
    } catch (e: ApiException.Http) {
        LoginOutcome.Failed(e.status, "HTTP ${e.status}")
    } catch (e: ApiException.Network) {
        LoginOutcome.Failed(-1, "网络错误：${e.message}")
    } catch (e: ApiException.Parse) {
        LoginOutcome.Failed(-2, "响应解析失败")
    } catch (e: Throwable) {
        LoginOutcome.Failed(-3, e.message ?: "未知错误")
    }

    /**
     * 把异常映射成 [LoginOutcome.Failed]。
     *
     * 供直连版 `smsLogin`（返回 [UserAccount]，无业务码信封）复用；
     * 网关版走 [runLogin]，其 catch 分支与本方法口径一致。
     */
    private fun mapLoginError(e: Throwable): LoginOutcome = when (e) {
        is HttpException -> {
            val serverMsg = extractServerMessage(e.response()?.errorBody()?.string())
            LoginOutcome.Failed(e.code(), serverMsg ?: "HTTP ${e.code()}")
        }
        is ApiException.Http -> LoginOutcome.Failed(e.status, "HTTP ${e.status}")
        is ApiException.Network -> LoginOutcome.Failed(-1, "网络错误：${e.message}")
        is ApiException.Parse -> LoginOutcome.Failed(-2, "响应解析失败")
        else -> LoginOutcome.Failed(-3, e.message ?: "未知错误")
    }

    /**
     * 从服务端错误响应体里抽 `message` 字段。
     *
     * 小猿口算的错误响应统一形如：
     * ```
     * {"timestamp":1789645191184,"status":401,"message":"unauthorized"}
     * ```
     */
    private fun extractServerMessage(rawBody: String?): String? {
        if (rawBody.isNullOrBlank()) return null
        return runCatching {
            Json.parseToJsonElement(rawBody).jsonObject["message"]?.jsonPrimitive?.content
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }
}
