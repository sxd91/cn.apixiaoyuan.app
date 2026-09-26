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
 * ## 子账号列表怎么拿（2026-09-26 修正）
 *
 * 服务端有一个**无参**接口直接给列表：
 * `GET /leo-profile/android/user-infos/batchGet`（主域，原版方法名
 * `LeoProfileApiService.getSubAccounts()`），按当前 cookie 返回
 * `List<UserVO>` —— 登录态是谁，就返回谁名下的全部账号。
 *
 * 此前本类绕了一大圈：先要从登录响应的
 * `UserAccount.subUserInfos.project2SubUserInfo["6"].subUserIds` 拿 ID 列表，
 * 再拼逗号串调 `batchGet?userIds=...`。这条链路**整体不成立**：
 *  - `subUserInfos` / `subUserIds` / `project2SubUserInfo` / `primarySubUserId`
 *    在 3.141.1 的 dex 里**完全不存在**（`dex_names` 与 `dex_strings` 双查 0 命中），
 *    所以 ID 列表永远是空 → `batchGet` 从不被调用 → **小号列表恒空**；
 *  - 原版接口本身也不吃 `userIds`，多传参数与协议不符。
 */
object AccountRepository {

    /**
     * 当前账号的完整信息。
     *
     * 调 `profile.getUserInfo()` 拿主账号 `UserVO`。
     */
    suspend fun fetchCurrentUser(): UserVO? = runCatching {
        ServiceLocator.profile.getUserInfo()
    }.getOrNull().also { vo ->
        // 年级落会话：PK 入口数据按年级取（PkViewModel 读它），
        // 登录响应 UserAccount 不带 grade，只有 UserVO 有。
        vo?.grade?.takeIf { it > 0 }?.let { SessionStore.saveGrade(it) }
    }

    /**
     * 一次拿齐「宝贝学习账号列表」。
     *
     * 直接调无参的 [ServiceLocator.subAccount] `getSubAccounts()`，
     * 并把**主账号**与**当前登录账号**标出来：
     *  - `isPrimary` —— `userId == primaryUserId`（响应里每个 `UserVO` 都带）；
     *  - `isCurrent` —— `userId` 与服务端下发的 `userid` cookie（= [SessionStore.yfdU]）一致。
     *
     * ## 为什么返回 `Result` 而不是「失败给空表」
     *
     * 此前这里把异常吞成 `emptyList()`，界面就只剩「没有可显示的宝贝账号」——
     * 用户完全分不清是**真的没有小号**还是**请求失败了**（401 / 417 / 网络）。
     * 这正是本轮「小号没写出来」排查困难的直接原因之一，因此改为把失败
     * 连同原因一起交给上层展示。
     */
    suspend fun fetchSubAccounts(): Result<List<SubAccountItem>> = runCatching {
        val currentUid = SessionStore.yfdU?.toInt()

        // 第一步：拿「有哪些子账号」—— 用 context（**不需要设备链**）。
        //
        // 此前直接用 batchGet（需要设备链 sid/ks_*），本项目自身登录的 cookie
        // 没有设备链 → 401 leo-auth → 列表恒空（用户看到「一直闪 + 401」）。
        // 实测（2026-09-26）：context?_productId=241 用本项目登录 cookie 即 200，
        // 返回 allSubUserIds。这是无设备链时唯一能拿到子账号 ID 的通道。
        val ctx = runCatching { ServiceLocator.subAccount.getUserInfosContext() }.getOrNull()
        val ids = ctx?.allSubUserIds?.map { it.toInt() }?.distinct()

        // 第二步：拿名字头像 —— batchGet（需设备链）。
        // 失败不致命：降级为只显示 uid，至少让用户看到有几个账号、能切换。
        val voById = runCatching { ServiceLocator.subAccount.getSubAccounts() }
            .getOrNull()
            ?.associateBy { it.userId }
            ?: emptyMap()

        val resolved = if (!ids.isNullOrEmpty()) {
            ids
        } else {
            // context 也拿不到时，退回 batchGet 的结果（可能为空）。
            voById.keys.toList()
        }

        val primary = ctx?.primarySubUserId?.toInt() ?: voById.values.firstOrNull()?.primaryUserId ?: 0

        resolved.map { uid ->
            val vo = voById[uid]
            val isCurrent = currentUid != null && uid == currentUid
            if (isCurrent && vo != null) {
                // 回填当前用户信息：PK H5 的 getUserInfo 桥能力从这里读。
                SessionStore.saveCurrentUserInfo(
                    nickname = vo.nickname ?: vo.defaultNickname,
                    avatarUrl = vo.avatarUrl,
                )
            }
            SubAccountItem(
                userId = uid,
                // 拿不到名字头像（batchGet 401）时降级显示 uid，
                // 好过显示「未命名」或整块空白。
                nickname = vo?.nickname ?: vo?.defaultNickname ?: "账号 $uid",
                grade = vo?.grade ?: 0,
                primaryUserId = vo?.primaryUserId ?: primary,
                avatarUrl = vo?.avatarUrl,
                isCurrent = isCurrent,
                isPrimary = uid == primary,
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
     * 切换到指定宝贝学习账号，并写回 `userid` cookie（**主页与账号页共用的完整实现**）。
     *
     * ## 为什么切换逻辑要下沉到这里
     *
     * 用户新需求（2026-09-26）：子账号卡片直接显示在**主页**「已登录卡片」下方，
     * 点哪个切哪个 —— 切换入口不再只有账号页。所以「调 switch 接口 + 写回
     * `userid` cookie + 刷新 YFD_U 缓存」这段必须收敛成可复用方法，否则主页和
     * 账号页会各写一份、行为漂移。
     *
     * 切换成功后的两件必做之事（缺一不可）：
     *  1) 本地缓存 YFD_U 跟上；
     *  2) 把新身份写回 `userid` cookie —— 切换响应未必带 Set-Cookie（或带了但
     *     jar 未落盘），不显式写一次的话，后续主域请求仍带旧 userid，表现为
     *     「切换了但没生效」。写的时候沿用原条目的 domain/path，只换 value。
     *
     * 新身份取值：优先用切换响应里的 `ytkUserId`（服务端给的真实新身份），
     * 拿不到时退回点击的那一项。
     *
     * @return 切换成功返回新的 userid；失败抛异常（含非 1 业务码）。
     */
    suspend fun switchTo(item: SubAccountItem): Long {
        val resp = ServiceLocator.subAccount.switchAccount(item.userId)
        check(resp.isSuccess) { "切换失败（code=${resp.code}）" }
        val newId = resp.body?.ytkUserId?.takeIf { it > 0 } ?: item.userId
        SessionStore.saveYfdU(newId.toLong())
        val domain = SessionStore.loadCookies()
            .firstOrNull { it.name == "userid" }?.domain
            ?: ".yuanfudao.com"
        SessionStore.upsertCookie("userid", newId.toString(), domain)
        return newId.toLong()
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
 * @param primaryUserId 主账号 ID（`UserVO.primaryUserId`）—— 用于界面展示
 *        「主账号 xxx」，也用于删除接口的 `targetPrimarySubUserId`。
 *        此前 UI 拿 `SubAccountItem` 里并不存在的 `primaryUserId` 去显示，
 *        实际取到的是默认值 `0`（显示成「主账号 0」），属于编译期看不出的错。
 * @param isCurrent 是否是**当前登录**的账号（cookie `userid` 与之相同）
 * @param isPrimary 是否是主账号（`userId == primaryUserId`）
 */
data class SubAccountItem(
    val userId: Int,
    val nickname: String,
    val grade: Int,
    val primaryUserId: Int,
    val avatarUrl: String? = null,
    val isCurrent: Boolean,
    val isPrimary: Boolean,
)