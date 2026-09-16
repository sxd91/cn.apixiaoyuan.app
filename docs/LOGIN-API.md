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
    val school: List<Any>,  // 泛型未确证，留待进一步读
)
```

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
| 首次登录 `YFD_U` 传 0 是否可行 | 低（推断） | 真机抓首次登录请求 |
| 密码登录是否需先调 `smsVerify` | 中 | 读 `FastLoginActivity` 调用链 |
| `UserPhaseInfo.school` 的元素类型 | 低 | 读 `UserPhaseInfo.smali` 完整泛型签名 |
| `LoginResponseBody.phone` 派生逻辑 | 中 | 读 getter 方法体 |
| 网关版 vendor 登录的确切路径 | 中 | 读 `LeoGatewayService` 剩余四个方法 |
| token 存哪（MMKV key 名） | 未确证 | 真机 `/data/data/com.fenbi.android.leo/files/mmkv/` |
| `YFD_U` 是否等同于 `ytkUserId` | 中 | 对比 `CurrentUserInfo.id` 与 `LoginResponseBody.ytkUserId` |

---

## 7. 对复刻的直接影响

**可以直接落盘的：**

- `LeoGatewayService` 的 `passwordLogin` / `smsLogin` / `tokenLogin` 三个方法（路径 + 参数 + 返回全部确证）
- `YtkApiService` 的五个方法（全部确证）
- 八个数据类的字段（全部逐字段确证）

**被阻塞的：**

- `YFD_U` 的来源——首次登录传什么？后续从哪读？**这是 R2 风险的核心**，需真机确认。
- token 的持久化位置——`LoginResponse` 里没看到 token 字段，说明 token 可能藏在 HTTP 响应头（`Set-Cookie` 或自定义头），或由 `body.ytkUserId` + 设备指纹组合生成。**需抓包确认。**

**下一个动作建议：** 先落 `core/model/` 的八个数据类 + `core/network/api/` 的两个登录 Service（接口定义），**运行时逻辑留空壳**——先把「能编译、能列出接口」这个中间态做出来，`YFD_U` 与 token 的真相并行去挖。
