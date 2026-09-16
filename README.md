# 逆向系老挂

`cn.apixiaoyuan.app` —— 一个以小猿口算逆向结果为业务基底、以 [miuix](https://github.com/miuix-kotlin/miuix) 为 UI 基座的 Android 应用。

## 这是什么

本项目的三个来源：

- **UI 基座**：[miuix](https://github.com/miuix-kotlin/miuix) `0.9.4-rc01`（blur / shader / nav 三个模块），负责液态玻璃质感与悬浮底栏。
- **视觉基准**：作者此前写的 `cn.nizou.sxd`（老挂戏老叟）。莫奈取色、液态玻璃 Tab 栏、图标库三项观感一比一对齐该应用。
- **业务基底**：`com.fenbi.android.leo` 3.141.1 的逆向结果。接口分层、编码层、PK 与练习协议均以该版本的逆向结论为准。

当前处于**工程骨架阶段**：导航壳、主题取色、Tab 栏、图标出口已落盘，业务层尚未开写。

## 当前进度

已完成：

- Compose + miuix 的工程骨架，AGP 9.3.1 / Kotlin 2.4.10 / compileSdk 37 / minSdk 33
- 类型安全导航（`AppNavHost`），九个页面路由与底部 Tab 单源维护（`BottomTabs`）
- 莫奈取色主题（`Theme.kt`），走 material-kolor 5.0.0 的动态配色，结构对齐 `cn.nizou.sxd` 的 `MizuTheme`
- 液态玻璃 Tab 栏（`LiquidGlassTabBar.kt`），当前为 Compose 原生模拟，真模糊待接入 miuix-blur
- 图标统一出口（`AppIcons.kt`），底座为 Material Symbols（`com.composables:icons-material-symbols-outlined-cmp:2.2.1`）

未开始：

- 数据层（Room 八表）
- 网络层（OkHttp + Retrofit + 鉴权 Interceptor + `@NeedDecode` 解码桥）
- 登录注册、接口浏览器、协议请求台、练习、PK、样本库、设置页
- native 编码层复刻（`libRequestEncoder.so` / `libContentEncoder.so` / `libRedressProcess.so`）
- baselineprofile

## 构建

本项目**不在本地构建 APK**，所有构建走 GitHub Actions。

- 推送 `main` 触发 `.github/workflows/ci.yml`
- Job `lint-and-test`：`./gradlew lint` + `testDebugUnitTest`
- Job `build`：`assembleRelease`，产物为未签名 APK 与混淆映射，从 Actions artifact 下载

本地若只做静态检查：

```bash
./gradlew :app:compileDebugKotlin --no-daemon --console=plain
```

需要 JDK 21 与 Android SDK（compileSdk 37）。

## 版本

版本号由 `version.properties` 维护。CI 不改版本号，只有 release 工作流发布时 `+1`。

## 协议

MIT，见 [LICENSE](LICENSE)。
