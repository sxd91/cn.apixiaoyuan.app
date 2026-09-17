# 逆向系老挂

`cn.apixiaoyuan.app` —— 一个以小猿口算逆向结果为业务基底、以 [miuix](https://github.com/miuix-kotlin/miuix) 为 UI 基座的 Android 应用。

## 这是什么

本项目的三个来源：

- **UI 基座**：[miuix](https://github.com/miuix-kotlin/miuix) `0.9.4-rc01`（blur / shader / nav 三个模块），负责液态玻璃质感与悬浮底栏。
- **视觉基准**：作者此前写的 `cn.nizou.sxd`（老挂戏老叟）。莫奈取色、液态玻璃 Tab 栏、图标库三项观感一比一对齐该应用。
- **业务基底**：`com.fenbi.android.leo` 3.141.1 的逆向结果。接口分层、编码层、PK 与练习协议均以该版本的逆向结论为准。

13 个 DEV-PLAN 模块已全部有实体代码落地，当前处于**业务链路联调阶段**：骨架、数据层、网络层、样本库、请求台均已成形，登录态已打通到「cookie 迁移 + 风控层定性」，正在啃 solar-encoder 签名这一关。

## 当前进度

**已完成：**

- Compose + miuix 的工程骨架，AGP 9.3.1 / Kotlin 2.4.10 / compileSdk 37 / minSdk 33
- 类型安全导航（`AppNavHost`），九个页面路由与底部 Tab 单源维护（`BottomTabs`）
- 莫奈取色主题（`Theme.kt`），走 material-kolor 5.0.0 的动态配色，结构对齐 `cn.nizou.sxd` 的 `MizuTheme`
- 液态玻璃 Tab 栏（`LiquidGlassTabBar.kt`），当前为 Compose 原生模拟，真模糊待接入 miuix-blur
- 图标统一出口（`AppIcons.kt`），底座为 Material Symbols（`com.composables:icons-material-symbols-outlined-cmp:2.2.1`）
- 网络底座（`core/network/`）：双 BaseUrl 拦截器、鉴权、编解码桥注解、Retrofit 双实例、`ServiceLocator`
- 数据层（Room 八表 + KSP）：请求链路四表 + 业务链路四表，schema 导出到 `app/schemas`
- 10 个 API Service（`core/network/api/`），对齐 `docs/API-INVENTORY.md`
- 请求台 → 样本库 → 请求历史链路（发送/重放均落库，`trimTo(2000)` 裁剪）
- 登录/发码错误提示结构化（`SmsOutcome` 三态 + 服务端 message 抽取）
- `PersistentCookieJar` 服务端删除指令识别（不再写入空值污染）
- 原版登录态迁移（12 条 cookie 明文 JSON → 本工程 `shared_prefs/leo_session.xml`）
- CI 全绿：`lint` + `testDebugUnitTest` + `assembleRelease`（未签名 APK 走 artifact）

**进行中：**

- solar-encoder 签名机制（`SignInterceptor` 已定性为 URL query 参数，`Li30/d` 接口已定位，实现类待锁定）
- `libRequestEncoder.so` 的 JNI 导出语义确证（`sdwioxccsd` / `zcvsd1wr2t` 是否为 sign 生成器）

**未开始：**

- 练习、PK、接口浏览器、设置页的完整业务 UI
- native 编码层复刻（`libContentEncoder.so` / `libRedressProcess.so`）
- baselineprofile

## 文档

| 文件 | 内容 |
|---|---|
| [`docs/DEV-PLAN.md`](docs/DEV-PLAN.md) | 13 模块清单、依赖顺序、M1-M6 里程碑、风险项、本地验证策略 |
| [`docs/API-INVENTORY.md`](docs/API-INVENTORY.md) | 接口全量清单（双 BaseUrl、登录、练习、PK、native 编码层） |
| [`docs/LOGIN-API.md`](docs/LOGIN-API.md) | 登录接口专项（`LeoGatewayService` 九方法 + 旧版 `YtkApiService`） |
| [`docs/skills/`](docs/skills/) | 可跨对话复用的技能文档（工具调用规范等） |

## 构建

本项目**不在本地构建 APK**，所有构建走 GitHub Actions。

- 推送 `main` 触发 `.github/workflows/ci.yml`
- Job `lint-and-test`：`./gradlew lint` + `testDebugUnitTest`
- Job `build`：`assembleRelease`，产物为未签名 APK 与混淆映射，从 Actions artifact 下载

本地若只做静态检查：

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-arm64
export ANDROID_HOME=/opt/android-sdk
export ANDROID_SDK_ROOT=/opt/android-sdk
cd /root/cn.apixiaoyuan.app
./gradlew :app:compileDebugKotlin --no-daemon --console=plain
```

exit 0 且零 `e:` 行才算过。

需要 JDK 21 与 Android SDK（compileSdk 37）。本机为 aarch64，aapt2 是 x86-64，跑不了 `processDebugResources`，故 APK 打包只能在 CI 完成。

## 版本

版本号由 `version.properties` 维护。CI 不改版本号，只有 release 工作流发布时 `+1`。

## 协议

MIT，见 [LICENSE](LICENSE)。
