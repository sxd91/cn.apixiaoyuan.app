# 逆向系老挂 · 登录接口清单（补逆向）

> 来源：`com.fenbi.android.leo` 3.141.1 apktool 解包产物
> 关键文件：
> - `smali_classes6/com/yuanfudao/android/leo/login/api/YtkApiService.smali`
> - `smali_classes6/com/yuanfudao/android/leo/login/api/LeoGatewayService.smali`
> - `smali_classes6/com/yuanfudao/android/leo/login/datas/*`
> - `smali_classes7/com/yuanfudao/android/leo/user/data/*`
> 用途：`core/network/api/` 的登录 Service 定义依据

---

## 0. 关键结论（先看这里）

**登录有两条链路，复刻走网关版：**

| 链路 | Service | 路径前缀 | 返回类型 | 状态 |
|---|---|---|---|---|
| 旧版 | `YtkApiService` | `/accounts/android/safe/login` | `Call<UserAccount>` | 保留，`@NotNullAndValid` |
| **新版** | `LeoGatewayService` | `/leo-gateway/android/auth/*` | `LoginResponse`（suspend） | **推荐复刻** |

**两条链路的域名不同：**

- `YtkApiService` 挂 `ytk_base_url` = `https://ape-api.yuanfudao.com`
- `LeoGatewayService` 挂 `leo_base_url` = `https://xyks.yuanfudao.com`

`LeoGatewayService` 虽是「网关」，但走的是主域，不是账号域。这点容易搞错。

---

## 1. LeoGatewayService（主域，推荐复刻）

挂 `leo_base_url`。九个方法，全部 `suspend`，全部 `@FormUrlEncoded` + `@GsonConverter` + `@CheckNothing`，返回 `LoginResponse`。

| 方法 | HTTP | 路径 | Query | Field | 可行性 |
|---|---|---|---|---|---|
| `passwordLogin` | POST | `/leo-gateway/android/auth/password` | `YFD_U: Long` | `phone`、`password` | **高** |
| `smsLogin` | POST | `/leo-gateway/android/auth/sms` | `YFD_U: Long?` | `phone`、`verification`、`autoRegister: Boolean` | 中 |
| `tokenLogin` | POST | `/leo-gateway/android/auth/token-login` | `YFD_U: Long?` | `token` | 低 |
| `huaweiLogin` | POST | 待读 | `YFD_U: Long?` | `token`、`autoRegister: Boolean` | 极低 |
| `honorLogin` | POST | 待读 | `YFD_U: Long?` | `token`、`autoRegister: Boolean` | 极低 |
| `vivoLogin` | POST | 待读 | `YFD_U: Long?` | `token`、`autoRegister: Boolean` | 极低 |
| `xiaomiLogin` | POST | 待读 | `YFD_U: Long?` | `token`、`autoRegister: Boolean` | 极低 |
| `cmccLogin` | POST | 待读 | — | `token`、`phone`、`autoRegister: Boolean` | 极低 |
| `aliLoginWithOperator` | POST | 待读 | — | `operatorId: Int`、`token`、`autoRegister`、`extra` | 极低 |

**签名原样（smali 逐行）：**

```
passwordLogin(JLjava/lang/String;Ljava/lang/String;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;
  p1: J -> @Query("YFD_U")
  p3: String -> @Field("phone")
  p4: String -> @Field("password")
  return: LoginResponse (suspend)

smsLogin(Ljava/lang/Long;Ljava/lang/String;Ljava/lang/String;ZLkotlin/coroutines/Continuation;)Ljava/lang/Object;
  p1: Long? -> @Query("YFD_U")
  p2: String? -> @Field("phone")
  p3: String? -> @Field("verification")
  p4: Z -> @Field("autoRegister")
  return: LoginResponse (suspend)

tokenLogin(Ljava/lang/Long;Ljava/lang/String;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;
  p1: Long? -> @Query("YFD_U")
  p2: String? -> @Field("token")
  return: LoginResponse (suspend)
```

---

## 2. YtkApiService（账号域，旧版）

挂 `ytk_base_url`。五个方法，`Call` 版本，`@FormUrlEncoded` + `@GsonConverter`。

| 方法 | HTTP | 路径 | Query | Field | 返回 | 注解 |
|---|---|---|---|---|---|---|
| `logout` | POST | `/accounts/android/logout` | — | — | `Unit`（suspend） | `@CheckNothing` |
| `passwordLoginCall` | POST | `/accounts/android/safe/login` | `YFD_U: Long` | `phone`、`password` | `Call<UserAccount>` | `@NotNullAndValid` |
| `smsLogin` | POST | `/accounts/android/safe/login` | `YFD_U: Long?` | `phone`、`verification`、`autoRegister: Boolean` | `Call<UserAccount>` | `@CheckNothing` |
| `smsVerify` | POST | `/verifier/android/sms` | `YFD_U: Long?` | `phone` | `Call<Void>` | `@CheckNothing` |
| `validateVerifyCode` | POST | `/verifier/android/validate` | — | `phone`、`verification` | `Call<Void>` | `@CheckNothing` |

**注意：`smsVerify`（发短信）与 `validateVerifyCode`（校验验证码）挂在账号域，但路径前缀是 `/verifier/`，不是 `/accounts/`。**

---

## 3. 短信验证码流程

```
1. smsVerify(phone)                POST /verifier/android/sms          （发码）
2. validateVerifyCode(phone, code) POST /verifier/android/validate     （校验，可选）
3. smsLogin(phone, verification)   POST /leo-gateway/android/auth/sms  （登录，拿 LoginResponse）
```

`smsLogin` 带 `autoRegister: Boolean`——**手机号未注册时自动注册**，无需单独的注册接口。

---

## 4. 数据类

### 4.1 LoginResponse（`login/datas/`）

```kotlin
data class LoginResponse(
    val code: Int,
    val body: LoginResponseBody? = null,
) {
    val needAuth: Boolean get() = code == 2
    val isBanned: Boolean get() = code == 3
    val phoneNotMatch: Boolean get() = code == 10
}
```

**`code` 语义（smali 逐行确证）：**

| code | 含义 | 判定方法 |
|---|---|---|
| `1` | 成功 | `LOGIN_RESULT_CODE_SUCCESS` |
| `2` | 需要认证 | `getNeedAuth()` |
| `3` | 封禁 | `isBanned()` |
| `10` | 手机号不匹配 | `getPhoneNotMatch()` |

**`code != 1` 全部视为失败**——注意这与 `Envelope` 的 `CODE_SUCCESS = 0` **不同**。登录接口用自己的 code 体系。

### 4.2 LoginResponseBody（`login/datas/`）

```kotlin
data class LoginResponseBody(
    val encryptedPhone: String,
    val ytkUserId: Int,
    val primaryUserId: Int,
    val rewardPoints: Int,
    val rewardPetFoods: Int?,
    val ytkUserInfo: YtkUserSchoolInfo,
    val leoUserInfo: UserVO,
)
```

构造函数签名（smali 逐行）：`(Ljava/lang/String;IIILjava/lang/Integer;Lcom/.../YtkUserSchoolInfo;Lcom/.../UserVO;)V`

### 4.3 UserVO（`user/data/`，smali_classes7）

```kotlin
data class UserVO(
    val userId: Int,
    val primaryUserId: Int,
    val nickname: String?,
    val defaultNickname: String?,
    val avatarId: String?,
    val avatarUrl: String?,
    val avatarPendantId: Int,
    val avatarPendantUrl: String?,
    val role: Int,
    val grade: Int,
    val gradeTrusted: Boolean,
    val gradeUpdatedTime: Long,
    val nicknameUpdatedTime: Long,
    val hasBindWxSrv: Boolean,
) {
    companion object {
        const val ROLE_STUDENT = 0
        const val ROLE_PARENT = 1
        const val ROLE_TEACHER = 2
    }
}
```

### 4.4 YtkUserSchoolInfo（`user/data/`）

```kotlin
data class YtkUserSchoolInfo(
    val xiaoxueInfo: UserPhaseInfo?,
    val chuzhongInfo: UserPhaseInfo?,
    val gaozhongInfo: UserPhaseInfo?,
    val daxueInfo: UserPhaseInfo?,
)
```

### 4.5 UserPhaseInfo（`user/data/`）

```kotlin
data class UserPhaseInfo(
    val school: List<SchoolNode>,
)

// SchoolNode 的六个字段由真机 leo_user_info 的 currentUserXiaoxueInfoStrKey 解出：
data class SchoolNode(
    val id: Long,
    val name: String,
    val height: Int,       // 层级：4=省级 / 2=市级 / 1=区级
    val initial: String,
    val parentId: Long,
    val regionPath: String,
)
```

**已修正**：原报告写 `List<Any>`（泛型未确证），真机 `currentUserXiaoxueInfoStrKey` 的实际值是
`{"school":[{"height":4,"id":836863,"initial":"","name":"","parentId":0,"regionPath":""}]}`，
元素类型确证为 `SchoolNode`。`height` 层级由 `parentId` 链反推：
836863(4).parentId=0 → 840051(2).parentId=836863 → 983521(1).parentId=840051。

另有 `UserVipInfo`（来自 `currentUserVipInfoKey`），含 `vipRightInfoVO` / `studyGroupRightInfo` / `svipRightInfoVO` 三个子结构，字段见 `core/model/SchoolModels.kt`。

### 4.6 UserAccount（`login/datas/`，旧版返回）

```kotlin
data class UserAccount(
    val id: Int,
    val primaryUserId: Int,
    val phone: String?,
    val email: String?,
    val identity: String?,
    val passwordExist: Boolean,
    val createdTime: Long,
)
```

### 4.7 UserSetting（`user/data/`）

```kotlin
data class UserSetting(
    val autoCheckEBook: Int,
)
```

只一个字段，整个类就这一个。

### 4.8 CurrentUserInfo（`user/data/`）

```kotlin
data class CurrentUserInfo(
    val id: Int,
    val phone: String?,
    val passwordExist: Boolean,
    val createdTime: Long,
)
```

---

## 5. 登录流程（对齐原版 Activity 链）

```
FastLoginActivity（一键登录主入口，运营商 SDK）
  | 运营商/厂商 token
  v
LeoGatewayService.<vendor>Login(token, autoRegister)
  |
  v
LoginResponse（code=1 -> 成功，body 带 leoUserInfo + ytkUserInfo）
  |
  v
RoleAndGradeSettingActivity（选角色 + 年级）
  | updateUserInfo
  v
HomeActivity
```

密码登录路径：

```
输入手机号 + 密码
  |
  v
LeoGatewayService.passwordLogin(YFD_U=0, phone, password)
  |
  v
LoginResponse -> code=1 -> body.leoUserInfo / body.ytkUserInfo
```

短信登录路径：

```
输入手机号
  | YtkApiService.smsVerify(phone)
收到验证码
  |
  v
LeoGatewayService.smsLogin(YFD_U=0, phone, verification, autoRegister=true)
  |
  v
LoginResponse
```

**`YFD_U` 在首次登录时可传 `0` 或空**——它是「猿辅导用户 ID」，新用户还没有。这是从 smali 里 `passwordLoginCall(J...)` 的首参是**原始 `J`（非 `Long?`）**、而 `smsLogin` 是 `Long?` 推断的——密码登录要求非空 `YFD_U`，短信登录允许空。**首次密码登录传 `0` 需实测确认**，这是本清单里置信度最低的一条。

---

## 6. 待确证项

| 项 | 置信度 | 确证方法 |
|---|---|---|
| 密码登录是否需先调 `smsVerify` | 中 | 读 `FastLoginActivity` 调用链 |
| `LoginResponseBody.phone` 派生逻辑 | 中 | 读 getter 方法体 |
| 网关版 vendor 登录的确切路径 | 中 | 读 `LeoGatewayService` 剩余四个方法 |
| `ks_*` 系列 cookie 的签名算法 | 未确证 | 需看 native `libRequestEncoder.so` |
| `__sub_user_infos__` 的解密方式 | 未确证 | 同上 |

**已解决（真机确证，2026-09-16）：**

| 项 | 结论 |
|---|---|
| 首次登录 `YFD_U` 传什么 | 传 `0`。`userid` cookie 登录后才下发，`SessionStore.yfdU` 未登录时返回 null |
| `UserPhaseInfo.school` 元素类型 | `List<SchoolNode>`，六个字段见 4.5 |
| token 存哪 | **不在响应体，在 `Set-Cookie`**。落 MMKV `cookie_store`，键 `cookieJsonListKey`，域 `yuanfudao.com` |
| `YFD_U` 是否等同 `ytkUserId` | **不等同**。`YFD_U` = 主域 `userid` cookie = `UserVO.userId`；`ytkUserId` 是账号域 ID |

---

## 7. 对复刻的直接影响

**可以直接落盘的：**

- `LeoGatewayService` 的 `passwordLogin` / `smsLogin` / `tokenLogin` 三个方法（路径 + 参数 + 返回全部确证）
- `YtkApiService` 的五个方法（全部确证）
- 八个数据类的字段（全部逐字段确证）
- 登录动作已可用：`core/auth/AuthRepository.kt` 的 `loginByPassword` / `loginBySms` / `sendSmsCode` / `logout`，返回 `LoginOutcome` 五态

**R2 已解（真机确证）：**

登录凭据不是响应体里的字段，是服务端 `Set-Cookie` 下发的 cookie 集合。原版把整份
cookie 列表以 JSON 存进 MMKV 的 `cookie_store`，键 `cookieJsonListKey`。

真机（Redmi onyx / Android 16 / KernelSU）实测的关键 cookie：

| Cookie | 值（截断） | 性质 |
|---|---|---|
| `sid` | `7012770506069852030` | 持久 sid，`expiresAt=253402300799999`（永不过期），登录主键 |
| `userid` | `1066052990` | **等于 `UserVO.userId`，就是 `YFD_U` 要注入的值** |
| `sess` / `g_sess` / `ks_sess` | base64 密文 | 会话 token 三层 |
| `ks_persistent` / `ks_r` / `ks_u` / `ks_deviceid` | — | 设备指纹与风控链 |
| `__sub_user_infos__` | base64 密文 | 子账号信息 |

**落地形状：**

- `core/session/SessionStore.kt` —— cookie 持久化（`CookieEntry` 九字段对齐原版 JSON）
- `core/session/SessionStore.kt` 里的 `PersistentCookieJar` —— OkHttp `CookieJar` 实现，`Set-Cookie` 自动落盘
- `core/network/AuthInterceptor.kt` —— 只注入 `YFD_U` 查询参数；cookie 由 CookieJar 处理
- `core/auth/AuthRepository.kt` —— 登录/登出/短信验证码唯一入口，`LoginOutcome` 五态对应四个业务码

---

## 附录 A. 真机取证记录（2026-09-16）

**设备**：Redmi onyx（25053RT47C）/ Android 16 / KernelSU root（`context=u:r:ksu:s0`）

**取证路径**：`/data/data/com.fenbi.android.leo/`

### A.1 cookie_store（登录态真身）

`files/mmkv/cookie_store`，键 `cookieJsonListKey`，值是 JSON 数组。
字段：`domain` / `expiresAt` / `hostOnly` / `httpOnly` / `name` / `path` / `persistent` / `secure` / `value`。

### A.2 leo_user_info（用户资料真身）

`files/mmkv/leo_user_info` 里的键：

| 键 | 内容 |
|---|---|
| `currentUserInfoStrKey@V3.21.0` | `UserVO` 十四字段 JSON |
| `currentUserXiaoxueInfoStrKey@V3.21.0` | 小学习段 `SchoolNode` 列表 |
| `currentUserChuZhongInfoStrKey@V3.21.0` | `{"school":[]}` |
| `currentUserGaoZhongInfoStrKey@V3.21.0` | `{"school":[]}` |
| `currentUserDaXueInfoStrKey@V3.102.0` | `{"school":[]}` |
| `currentUserVipInfoKey@V3.23.0` | `UserVipInfo` JSON |
| `currentUserPhoneKey@V3.21.0` | AES 密文（`0/v1$...==` 形态） |
| `isCurrentUserParentCertificatedKey@V3.50.0` | Boolean |

### A.3 leo_shepherd_id（设备指纹）

`didKey@v3.68.0` = `$DUtA-DmaWBaa-xgaLMMFCl5fjJG__ajuzNf3`，24 字符，Base64 变体。
系统层：`ro.serialno` = `5ee243c8`，`settings get secure android_id` = `75291af23fea578d`。

### A.4 leo_user_info_un_remove（登出后保留）

`lastUsedPhoneNumberKey@V3.104.0`（AES 密文）+ `hasEnteredDeregisterAccountKey@V3.127.0`。

### A.5 设备环境

| 项 | 值 |
|---|---|
| 型号 | 25053RT47C（Redmi onyx） |
| Android | 16 |
| 指纹 | `Redmi/onyx/onyx:16/BP2A.250605.031.A3/OS3.0.303.0.WOLCNXM:user/release-keys` |
| root | KernelSU（`u:r:ksu:s0`） |
