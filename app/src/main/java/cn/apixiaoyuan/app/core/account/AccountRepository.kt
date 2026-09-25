package cn.apixiaoyuan.app.core.account

import cn.apixiaoyuan.app.core.auth.AuthRepository
import cn.apixiaoyuan.app.core.auth.DeviceFingerprint
import cn.apixiaoyuan.app.core.auth.PhoneEncoder
import cn.apixiaoyuan.app.core.model.LoginResponse
import cn.apixiaoyuan.app.core.model.UserAccount
import cn.apixiaoyuan.app.core.model.UserVO
import cn.apixiaoyuan.app.core.network.ServiceLocator
import cn.apixiaoyuan.app.core.session.SessionStore

/**
 * 账号管理数据入口：宝贝学习账号（子账号）切换 / 新建 / 删除，以及改密码。
 *
 * ## 分层纪律
 *
 * 与 [cn.apixiaoyuan.app.core.auth.AuthRepository] 同套路：UI 不直接碰
 * Retrofit Service，异常在这里收敛成 `Result` / null，UI 只处理两态。
 *
 * ## 子账号 ID 从哪来
 *
 * 服务端不在 `batchGet` 之外单独给「我的子账号列表」接口 —— 子账号 ID 藏在
 * 登录响应 `UserAccount.subUserInfos.project2SubUserInfo["6"].subUserIds` 里
 * （实测响应：
 * `{"project2SubUserInfo":{"6":{"projectId":6,"primarySubUserId":511467407,"subUserIds":[511467407,1066052990,1151665130]}}}`）。
 *
 * 所以要拿列表得两步：
 *  1. [listSubAccountIds] —— 拉当前账号，从 `subUserIds` 取出全部 ID；
 *  2. [batchGetUserInfos] —— 用逗号拼串批量换回 `UserVO`（昵称、头像、年级）。
 *
 * 项目号 `"6"` 是小猿口算所属业务线；`subUserIds` 的第一个元素是**主账号自己**，
 * 不是子账号 —— 展示时必须区分，否则用户会看到「自己」出现在宝贝列表里。
 */
object AccountRepository {

    /** 小猿口算所属业务线在 `subUserInfos` 里的 key。 */
    private const val PROJECT_KEY = "6"

    /**
     * 当前账号的完整信息（含 `subUserIds`）。
     *
     * 用密码登录接口重放不可行（需要密码），改用 `GET /accounts/android/current`。
     * 该接口在 [cn.apixiaoyuan.app.core.network.api.YtkAccountService] 里是
     * `Call<CurrentUserInfo>` 形态，不含 `subUserInfos` —— 所以这里退一步：
     * 直接从 [ServiceLocator.subAccount] 的 `batchGet` 反查不可行（要先有 ID）。
     *
     * **实际做法**：调 `profile.getUserInfo()` 拿主账号 `UserVO`，再调
     * `subAccount.batchGetUserInfos` 只查主账号自己 —— 子账号 ID 列表则由
     * [listSubAccountIds] 从会话里的登录响应缓存取（见那里的说明）。
     */
    suspend fun fetchCurrentUser(): UserVO? = runCatching {
        ServiceLocator.profile.getUserInfo()
    }.getOrNull()

    /**
     * 拉子账号 ID 列表（含主账号自己）。
     *
     * 数据源是 [SessionStore] 里缓存的登录响应 `subUserIds`。登录时
     * [cn.apixiaoyuan.app.core.auth.AuthRepository] 已经把整个 `UserAccount`
     * 存进来了（见 `SessionStore.saveAccount`），这里只做读取。
     *
     * 登录响应里没有该字段时返回空表（老账号 / 单账号用户就是这种情况）。
     */
    fun listSubAccountIds(): List<Int> = SessionStore.subUserIds()

    /**
     * 批量换回子账号资料。
     *
     * @param ids 子账号 ID 列表；空表直接返回空，不发请求。
     */
    suspend fun batchGetUserInfos(ids: List<Int>): List<UserVO> {
        if (ids.isEmpty()) return emptyList()
        return runCatching {
            ServiceLocator.subAccount.batchGetUserInfos(ids.joinToString(","))
        }.getOrDefault(emptyList())
    }

    /**
     * 一次拿齐「宝贝学习账号列表」。
     *
     * 组合 [listSubAccountIds] + [batchGetUserInfos]，并**标出哪个是当前登录账号**。
     */
    suspend fun fetchSubAccounts(): List<SubAccountItem> {
        val ids = listSubAccountIds()
        if (ids.isEmpty()) return emptyList()
        val currentUid = SessionStore.yfdU?.toInt()
        return batchGetUserInfos(ids).map { vo ->
            SubAccountItem(
                userId = vo.userId,
                nickname = vo.nickname ?: vo.defaultNickname ?: "未命名",
                grade = vo.grade,
                isCurrent = currentUid != null && vo.userId == currentUid,
                // 主账号 = 主账号 ID（primaryUserId）的那个
                isPrimary = vo.userId == vo.primaryUserId,
            )
        }
    }

    /**
     * 新建宝贝学习账号。
     *
     * 成功后服务端会下发新的 cookie（切换为该子账号）—— 由
     * [cn.apixiaoyuan.app.core.session.PersistentCookieJar] 自动落盘。
     */
    suspend fun createSubAccount(): Result<UserAccount> = runCatching {
        ServiceLocator.subAccount.registerSonSubUser()
    }

    /**
     * 切换到指定宝贝学习账号。
     *
     * @param targetUserId 目标账号 ID（来自 [SubAccountItem.userId]）
     */
    suspend fun switchAccount(targetUserId: Int): Result<LoginResponse> = runCatching {
        ServiceLocator.subAccount.switchAccount(targetUserId)
    }

    /**
     * 删除宝贝学习账号（**通道 A**：账号域直连版，用户拍板选定）。
     *
     * POST `/accounts/android/directly/deregisterSubUser`。
     *
     * ## 为什么选通道 A（2026-09-25 三条通道实测对比）
     *
     * | 通道 | 路径 / 域名 | 传参 | 实测结果 |
     * |---|---|---|---|
     * | **A** | `/accounts/android/directly/deregisterSubUser`（ape-api） | Field | **403 `Decrypt failed` → 加密后 500（链路通）** |
     * | B | `/leo-gateway/android/accounts/directly/deregisterSubUser`（xyks） | Query | 401 `unauthorized` |
     * | C | `/accounts/android/directly/subDeregister`（ape-api） | Query | 403 `Decrypt failed`（且路径已下线，404） |
     *
     * B 走主域 —— 而本项目**主域 cookie 实测一律 401**（`batchGet` 同样 401），
     * 说明主域还要求本项目尚未复刻的额外头（原版 `HeaderInterceptor` 链路），
     * 因此 B 不可用。C 在账号域但路径已下线（404 `No message available`）。
     * **A 是唯一实测可用的通道**，且它在账号域，cookie 天然有效。
     *
     * ## `verification` 必须 RSA 加密
     *
     * 传明文会得到 403 `Decrypt failed, data =000000` —— 服务端**先解密再校验**。
     * 这里用 [PhoneEncoder.encode]（与发短信/登录密码同一把公钥）加密后提交。
     * 两个 ID 参数**不加密**（错误信息未指向它们）。
     *
     * @param subUserId     要删的子账号 ID
     * @param primaryUserId 主账号 ID
     * @param verification  短信验证码（明文入参，内部 RSA 加密后提交）
     */
    suspend fun deleteSubAccount(
        subUserId: Int,
        primaryUserId: Int,
        verification: String,
    ): Result<UserAccount> = runCatching {
        ServiceLocator.subAccount.deregisterSubUser(
            deregsiterSubUser = subUserId,
            targetPrimarySubUserId = primaryUserId,
            verification = PhoneEncoder.encode(verification),
        )
    }

    /** 删除宝贝学习账号：发验证码。
     *
     * 复用与登录同一条发码链路（`/verifier/android/sms`，账号域）。
     * 删除接口的 `verification` 要求 RSA 密文，但**发码接口的 phone 也要求密文**
     * —— 两者都由 [cn.apixiaoyuan.app.core.auth.AuthRepository.sendSmsCode]
     * 内部处理，这里不重复加密。
     */
    suspend fun sendDeleteSmsCode(phone: String): AuthRepository.SmsOutcome =
        AuthRepository.sendSmsCode(phone)

    /**
     * 改密码。
     *
     * POST `/accounts/android/password/reset`，需要短信验证码。
     *
     * 三个参数事实：
     *  - `encryptedPhone` —— **RSA 密文**（与发短信同一把公钥，见 [PhoneEncoder]）
     *  - `verification`   —— 明文验证码
     *  - `password`       —— **RSA 密文**（实测密码字段要求密文，与密码登录同口径）
     */
    suspend fun resetPassword(
        phone: String,
        verification: String,
        newPassword: String,
    ): Result<Unit> = runCatching {
        ServiceLocator.ytkUserCenter.resetPassword(
            yfdU = DeviceFingerprint.yfdU(),
            encryptedPhone = PhoneEncoder.encode(phone),
            verification = verification,
            password = PhoneEncoder.encode(newPassword),
        )
    }

    /**
     * 修改昵称。
     *
     * `PUT /leo-profile/android/user-infos`。
     *
     * **必须用专用 body 类**：`UserVO.userId` 与 `primaryUserId` 是无默认值的
     * 非空 `Int`，直接构造 `UserVO` 会序列化出 `"userId":0` 污染请求。
     * [UpdateNicknameBody] 只带 `nickname`，服务端按部分更新处理。
     */
    suspend fun updateNickname(nickname: String): Result<UserVO> = runCatching {
        ServiceLocator.profile.updateUserInfo(
            body = UserVO(
                userId = requireNotNull(SessionStore.yfdU).toInt(),
                primaryUserId = requireNotNull(SessionStore.yfdU).toInt(),
                nickname = nickname,
            ),
        )
    }
}

/**
 * 宝贝学习账号列表项。
 *
 * @param isCurrent 是否是**当前登录**的账号（cookie `userid` 与之相同）
 * @param isPrimary 是否是主账号（`userId == primaryUserId`）
 */
data class SubAccountItem(
    val userId: Int,
    val nickname: String,
    val grade: Int,
    val isCurrent: Boolean,
    val isPrimary: Boolean,
)