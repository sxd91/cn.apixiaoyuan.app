# 逆向系老挂 · 业务层开发计划

> 前置文档：[API-INVENTORY.md](API-INVENTORY.md)
> 当前 HEAD：`a6ef4cd`（玻璃折射链路已推送）
> 目标：把 `cn.apixiaoyuan.app` 从骨架推进到「可登录、可拉练习、可跑 PK、可当协议请求台」

---

## 一、总体架构

```
┌─ UI 层（feature/*）──────────────────────────────┐
│  login / home / api / exercise / pk / repl       │
│  samples / settings / apk                        │
├─ Domain 层（core/domain）─────────────────────────┤
│  UseCase：LoginUseCase / FetchExerciseUseCase …  │
│  Repository 接口（不依赖 Retrofit）              │
├─ Data 层（core/network + core/database）──────────┤
│  Retrofit Service（对齐 API-INVENTORY）          │
│  OkHttp：双 BaseUrl Interceptor / 鉴权 / 解码桥  │
│  Room：请求历史、缓存、样本库                    │
└─ Native 层（core/native）─────────────────────────┘
   @NeedDecode 解码桥（JNI 复刻或 so 直调）
```

**分层纪律：**

- `feature/*` 只依赖 Domain 的 UseCase，不直接碰 Retrofit
- `core/network` 只做 HTTP 与序列化，不含业务判断
- Room 只在 Data 层用，不外泄 Entity 到 UI

---

## 二、模块清单与依赖顺序

| 序 | 模块 | 目录 | 前置 | 说明 |
|---|---|---|---|---|
| 1 | 网络底座 | `core/network/` | — | 双 BaseUrl、鉴权、日志、序列化 |
| 2 | 数据模型 | `core/model/` | — | `@Serializable` DTO，对齐接口清单 |
| 3 | 会话与存储 | `core/session/` | 1 | token、YFD_U、用户资料的持久化 |
| 4 | 账号域 Service | `core/network/api/` | 1,2 | `YtkAccountService` 等 |
| 5 | 登录流程 | `feature/login/` | 3,4 | 手机号 / 扫码 / 一键登录 |
| 6 | 主域 Service | `core/network/api/` | 1,2 | 数学 / 语文 / 英语 / 试卷 |
| 7 | 练习模块 | `feature/exercise/` | 6 | 知识点列表 → 答题 → 结果 |
| 8 | PK 模块 | `feature/pk/` | 6 | 原生入口 + H5 WebView 容器 |
| 9 | 接口浏览器 | `feature/api/` | 6 | 列出全部接口，可发起请求 |
| 10 | 协议请求台 | `feature/repl/` | 1,9 | 自由构造请求、看原始响应 |
| 11 | 解码桥 | `core/native/` | 10 | `@NeedDecode` 真实解码 |
| 12 | 数据库 | `core/database/` | 2 | Room 八表 + KSP |
| 13 | 样本库 | `feature/samples/` | 12 | 保存/回放请求样本 |

**从 1 到 13 严格串行**——下层不通，上层全是空中楼阁。

---

## 三、各模块详细设计

### 模块 1 · 网络底座 `core/network/`

**文件清单：**

```
core/network/
├── BaseUrl.kt              # @BaseUrl 注解 + BaseUrlInterceptor
├── NeedDecode.kt           # @NeedDecode 注解 + NeedDecodeInterceptor
├── AuthInterceptor.kt      # 注入 token / YFD_U / 设备指纹
├── HeaderInterceptor.kt    # 统一 UA / 版本号 / 渠道
├── LoggingInterceptor.kt   # 可选，debug 打完整请求响应
├── RetrofitFactory.kt      # 按 BaseUrl 建两套 Retrofit
├── ServiceLocator.kt       # 替代原版 sp/n
└── Result.kt               # 统一响应包装（ApiResult<T>）
```

**关键实现点：**

- `@BaseUrl` 注解挂在 Retrofit 方法上，`BaseUrlInterceptor` 在 `chain.request()` 时读注解重写 host。Retrofit 不原生支持按方法切 host，这是标准解法。
- `RetrofitFactory` 建**两个 Retrofit 实例**（leo / ytk），各自带不同的 `baseUrl`，共享同一 OkHttpClient。
- 所有 Service 的创建集中在 `ServiceLocator`，用 `by lazy` 保证单例。

**验收标准：** 能对任一接口发出请求，拿到原始响应字符串（不解码）。

---

### 模块 2 · 数据模型 `core/model/`

按接口清单逐个落 `@Serializable data class`。命名对齐原版：`UserVO`、`UserSetting`、`GamePkEntryData`、`OralPKHomaData` 等。

**关键决策：** 字段名必须与原版 JSON key 一致（用 `@SerialName` 显式标注），因为服务端可能返回驼峰/下划线混合。**不要靠猜**——每个模型落盘前先在原版 smali 里找到 `@SerializedName` 或字段声明核实。

**验收标准：** 每个接口的响应能反序列化成对象，字段值非空。

---

### 模块 3 · 会话与存储 `core/session/`

```
core/session/
├── SessionManager.kt       # 单例，持 token / YFD_U / UserVO
├── SessionStore.kt         # 持久化（DataStore 或 MMKV）
└── DeviceFingerprint.kt    # 设备指纹（对齐 MSA OAID）
```

**关键问题：token 从哪来。**

原版 token 疑似在 MMKV，但**具体 key 未确证**。两条路：

1. **真机验证**（推荐）：装原版 APK，登录，`adb shell` 进 `/data/data/com.fenbi.android.leo/files/mmkv/` 看 key 名。
2. **静态反推**：在原版 smali 里搜 `mmkv`、`YFD_U`、`Authorization` 的读写点。

**在确证之前，模块 3 只做骨架 + 接口定义，不写死 key。**

**验收标准：** 能手工注入一个 token 并落盘、重启后读回。

---

### 模块 4-5 · 账号域 Service + 登录流程

**登录方式优先级：**

| 方式 | 可行性 | 说明 |
|---|---|---|
| 手机号 + 密码 | 高 | 需先确证登录接口（原版未在报告中列出，需补逆向） |
| 手机号 + 验证码 | 中 | 需确证短信接口 |
| 扫码登录 | 低 | 需 WebView + 轮询 |
| 运营商一键登录 | 极低 | 依赖三大运营商 SDK |
| 第三方 OAuth | 低 | 华为/小米/VIVO 各家 SDK |

**第一步必须先补逆向登录接口**——现有报告只覆盖了 `getUserInfo` / `updateUserInfo`，没覆盖「怎么拿到登录态」。这是模块 5 的前置阻塞项。

**验收标准：** 用真实账号登录成功，拿到非空 `UserVO`。

---

### 模块 6 · 主域 Service

按接口清单落 5 个 Service：

```
core/network/api/
├── LeoProfileApiService.kt
├── LeoMathApiService.kt
├── LeoChineseApiService.kt
├── LeoEnglishApiService.kt
├── LeoPaperExerciseApiService.kt
└── LeoPoemsParadiseApiService.kt
```

**每个方法落盘前，先在原版 smali 里核实三件事：**

1. 路径是否与清单一致
2. Query/Body 参数的确切名字
3. 返回类型（`Call<T>` 还是 `suspend`）

**验收标准：** 每个方法能编译，且能用接口浏览器发出请求。

---

### 模块 7 · 练习模块

```
feature/exercise/
├── ExerciseScreen.kt       # 入口，科目 Tab
├── MathExerciseScreen.kt   # 数学知识点列表
├── ChineseExerciseScreen.kt
├── EnglishExerciseScreen.kt
├── PaperExerciseScreen.kt  # 试卷搜索
└── ExerciseResultScreen.kt # 结果页
```

**流程：** `getExercisesKeyPoints` → 列表 → 点进 `getMathExercisesCall` → 答题 → `uploadExamResult` → `getExamResult`。

**验收标准：** 能拉到真实知识点列表并展示。

---

### 模块 8 · PK 模块

```
feature/pk/
├── PkScreen.kt             # 入口，拉 getGamePkEntryData
├── PkMathScreen.kt         # 数学 PK，拉 getGamePkHomeData
├── PkH5Screen.kt           # WebView 容器，加载 pk.html
└── PkPoemsScreen.kt        # 诗 PK
```

**WebView 容器的关键点：**

- `settings.javaScriptEnabled = true`
- `settings.domStorageEnabled = true`
- `addJavascriptInterface` 暴露原生能力（抓包、解码、日志）
- 拦截 `leo://` scheme

**验收标准：** 能打开 H5 PK 页面，且 JS bridge 能双向通信。

---

### 模块 9 · 接口浏览器 `feature/api/`

把 `API-INVENTORY.md` 转成可交互列表：

```
feature/api/
├── ApiScreen.kt            # 按模块分组的接口列表
├── ApiDetailScreen.kt      # 单个接口：填参数 → 发请求 → 看响应
└── ApiRegistry.kt          # 接口元数据注册表（路径/方法/参数 schema）
```

**`ApiRegistry` 是核心**——把接口清单编码成结构化数据，UI 从它渲染。

**验收标准：** 不用写代码就能调通任一接口。

---

### 模块 10 · 协议请求台 `feature/repl/`

比接口浏览器更自由：

- 任意 URL / Method / Headers / Body
- 原始响应展示（含 hex 视图）
- 请求历史
- 导出为 curl / Python

**验收标准：** 能手工构造一个带 `@NeedDecode` 的请求，看到密文响应。

---

### 模块 11 · 解码桥 `core/native/`

**这是整个工程最难的一块。**

三个 so 的复刻路径：

1. **先用原版 so**（最快）：把 `libRequestEncoder.so` 等直接放进 `app/src/main/jniLibs/arm64-v8a/`，JNI 调用。
2. **IDA 分析**：定位 JNI 导出，找字符串常量（S 盒、密钥）。
3. **纯 Kotlin 复刻**：如果算法是标准 AES/RC4 + 已知密钥，用 Kotlin 重写。

**第一步是「用原版 so 跑通」**——先证明能解码，再谈复刻。

**验收标准：** `getChineseKnowledgeUsageExamInfo` 的响应能解码成可读 JSON。

---

### 模块 12-13 · 数据库 + 样本库

Room 八表（按需细化）：

| 表 | 用途 |
|---|---|
| `RequestHistory` | 请求历史 |
| `ResponseCache` | 响应缓存 |
| `Sample` | 请求样本（可回放） |
| `User` | 账号信息 |
| `Session` | 会话记录 |
| `ExerciseRecord` | 练习记录 |
| `PkRecord` | PK 记录 |
| `DecodedPayload` | 解码后的明文缓存 |

**KSP 插件已回**（`alias(libs.plugins.ksp)` + `ksp(libs.androidx.room.compiler)`）。

**KSP 版本踩坑记录：** `libs.versions.toml` 原写 `ksp = "2.4.10-2.0.4"`，阿里云三个代理仓库（`google` / `public` / `gradle-plugin`）全部解析失败。查 `maven-metadata.xml` 后确认仓库里 KSP 插件最高只到 `2.3.12`（`<latest>2.3.12</latest>`），2.4.x 一个都没有 —— 该复合版本号尚不存在。改 `2.3.12` 后通过。`room-compiler:2.8.0` 在 google 代理里确认存在（HTTP 200）。

**schema 导出：** `exportSchema = true` + `ksp { arg("room.schemaLocation", "$projectDir/schemas") }`，schema 落 `app/schemas/cn.apixiaoyuan.app.core.database.AppDatabase/1.json`。**不启用** `fallbackToDestructiveMigration` —— 请求样本是不可恢复的手工产物，改表必须写真实 Migration，不能靠清库蒙混。

**实际落地：**

| 文件 | 内容 |
|---|---|
| `core/database/RequestEntities.kt` | 请求链路四表：`RequestHistory` / `ResponseCache` / `Sample` / `DecodedPayload` |
| `core/database/BusinessEntities.kt` | 业务链路四表：`User` / `Session` / `ExerciseRecord` / `PkRecord` |
| `core/database/SampleDao.kt` | 样本表 DAO |
| `core/database/RequestHistoryDao.kt` | 历史表 DAO（含 `trimTo` 裁剪） |
| `core/database/AppDatabase.kt` | 八表注册 + 两个 DAO 入口 + 单例 |
| `core/samples/SampleRepository.kt` | 样本读写 + 重放记录回写 + `recordReplay` 落历史 |
| `feature/samples/SamplesViewModel.kt` | 列表状态 + 重放动作（直构 OkHttp，过编解码桥） |
| `feature/samples/SamplesScreen.kt` | 样本列表 + 重放结果面板 + 空态 |

**两处边界已写进 KDoc：**
- `User` 表**不是鉴权来源** —— 登录态唯一真身在 `SessionStore`（cookie，R2 已闭环）。`Session` 表只存审计信息，不冗余 cookie 值。
- `PkRecord` **不存胜负与得分** —— 口算 PK 是 H5（模块 8 已确证），原生只做入口与埋点，拿不到逐题结果。

**待真机确认：** `ExerciseRecord` 的 `taskId` / `taskName` / `finishedCount` / `totalCount` 四字段是**推断**（`LeoCurrentTaskInfo` 当前为占位模型），真机验证后按实际调整。

**验收标准：** 请求历史能落库、能查询、能重放。
- 落库：`SampleRepository.recordReplay()` 每次重放落一行 `request_history`，写后 `trimTo(2000)` 裁剪。
- 查询：`RequestHistoryDao.observeAll()` / `observeRecent(limit)` / `observeByPath(prefix)`。
- 重放：`SamplesScreen` 每条样本可重放；样本来源为协议请求台（`ReplScreen`）的「存为样本」按钮。

**已知缺口：**
- 请求台「发送」本身**不落** `request_history`，只有「存为样本后的重放」落库。要让所有请求都进流水，需在 `ReplViewModel.send()` 里也插一行 —— 未做。
- `ResponseCache` / `DecodedPayload` 两表已建但暂无写入方，等待响应缓存与解码缓存策略确定。
- `User` / `Session` / `ExerciseRecord` / `PkRecord` 四表已建但暂无写入方，等待对应业务链路接入。

---

## 四、执行顺序与里程碑

### M1 · 网络底座（模块 1-2）

- 落 `core/network/` 全部文件
- 落 `core/model/` 核心 DTO
- **验证：** 用接口浏览器（临时硬编码 UI）发出第一个真实请求，拿到原始响应

### M2 · 会话与账号（模块 3-5）

- **前置：补逆向登录接口**
- 落 `core/session/`
- 落账号域 Service
- 落登录页
- **验证：** 真实账号登录成功

### M3 · 主域接口（模块 6）

- 落 5 个主域 Service
- 落 `ApiRegistry`
- **验证：** 接口浏览器能列出并调用全部接口

### M4 · 练习与 PK（模块 7-8）

- 落练习模块
- 落 PK 模块 + WebView 容器
- **验证：** 能拉真实练习、能开 H5 PK

### M5 · 解码桥（模块 11）

- 先集成原版 so
- 再 IDA 分析
- **验证：** `@NeedDecode` 接口返回可读 JSON

### M6 · 数据与样本（模块 12-13）

- 加回 KSP
- 落 Room 八表
- 落样本库
- **验证：** 请求历史可回放

---

## 五、风险与阻塞项

| # | 风险 | 影响 | 应对 |
|---|---|---|---|
| R1 | **登录接口未逆向** | 阻塞 M2 全部 | 优先补逆向：smali 搜 `login`、`password`、`sms`、`verify` |
| R2 | **token 存储位置未确证** | 阻塞鉴权 | 真机 `adb shell` 看 MMKV；或 smali 搜 `mmkv` |
| R3 | **native 解码算法未知** | 阻塞 M5 | 先用原版 so 跑通，再谈复刻 |
| R4 | **AAPT2 是 x86-64** | 本地不能打包 | 已确认：本地只跑 `compileDebugKotlin`，打包走 CI |
| R5 | **H5 PK 协议未抓** | 阻塞 PK 深度功能 | mitmproxy + 系统证书注入 |
| R6 | **设备指纹（OAID）** | 部分接口可能校验 | 先不伪造，用真实设备值 |

---

## 六、本地验证策略

**AAPT2 是 x86-64，本机 aarch64 跑不了 `processDebugResources`。** 所以本地验证分两层：

| 层 | 命令 | 能验证什么 |
|---|---|---|
| Kotlin 编译 | `./gradlew :app:compileDebugKotlin` | 语法、符号、类型 |
| 完整打包 | 交给 CI | 资源、DEX、APK |

**每次动代码后本地必跑：**

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-arm64
export ANDROID_HOME=/opt/android-sdk
export ANDROID_SDK_ROOT=/opt/android-sdk
cd /root/cn.apixiaoyuan.app
./gradlew :app:compileDebugKotlin --no-daemon --console=plain
```

exit 0 且零 `e:` 行才算过。

---

## 七、下一步（立刻可做）

1. **落 `core/network/` 骨架**（模块 1）——不依赖任何逆向结论，纯粹是 Retrofit + OkHttp 装配
2. **补逆向登录接口**（R1）——并行，纯读 smali
3. **落 `core/model/` 核心 DTO**（模块 2）——依赖接口清单，已具备

模块 1 是唯一无阻塞的起点。从它开始。