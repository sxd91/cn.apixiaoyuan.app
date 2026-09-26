package cn.apixiaoyuan.app.core.native

/**
 * 三个 native 库的 JNI 语义速查（**不再提供加载实现**）。
 *
 * ## 为什么这个文件里没有加载代码了
 *
 * 2026-09-26 确证：`libRequestEncoder.so` 与 `libContentEncoder.so`
 * **都不能用 `System.loadLibrary` + `external fun`** —— 它们的 `JNI_OnLoad`
 * 里走 `RegisterNatives`，把方法注册到原版 App 的类上，而那些类在本工程
 * 不存在，注册必然失败（库能 `dlopen`，但方法不可用）。
 *
 * 因此实际调用实现搬到了「dlopen + 按偏移直调」的两个桥：
 *
 * | 库 | 调用入口 | 偏移（以 JNI_OnLoad 为基） | 语义 |
 * |---|---|---|---|
 * | `libRequestEncoder.so` | [cn.apixiaoyuan.app.core.sign.SignComputer] | `+0x4078` | 主域 `sign` |
 * | `libContentEncoder.so` | [ContentBridge] | `+0x1ecf0` | 内容编解码 `([B)[B` |
 * | `libRedressProcess.so` | 未接线 | — | 拍照矫正（需 OpenCV，暂不需要） |
 *
 * ## 各库的静态事实（`readelf` / `nm` / 重定位表读出，非推测）
 *
 * ### libRequestEncoder.so（919,600 字节，md5 1d9d8e3b…）
 * - 唯一静态导出：`JNI_OnLoad`
 * - `JNI_OnLoad` 内 `RegisterNatives` 两个方法到
 *   `com/yuanfudao/android/leo/stub/SecureStub`
 * - 方法名在 so 内被加密（明文搜不到），运行时才注册
 *
 * ### libContentEncoder.so（298,144 字节）
 * - 唯一静态导出：`JNI_OnLoad` @ `0x1ee2c`
 * - 注册 1 个方法到 `com/fenbi/android/leo/imgsearch/sdk/utils/e`：
 *   - name `"c"`（`0x1466f`）
 *   - sig `"([B)[B"`（`0x13959`）
 *   - fnPtr `0x1ecf0`
 * - 静态链接 libc++（`NEEDED` 里没有 `libc++_shared.so`），无外部 C++ 依赖
 *
 * ### libRedressProcess.so（4,942,568 字节）
 * - 四个**静态导出**（`Java_*` 直接可见），无需 RegisterNatives：
 *   `Java_com_yuanfudao_android_leo_check_redress_CheckRedressJNI_resizeWithPad`
 *   `..._dewarp` / `..._processPoint` / `..._complement`
 * - 属「拍照搜题」的图像矫正链路，参数涉及 OpenCV `Mat`；
 *   本工程未引入 OpenCV，暂不接线。
 *
 * ## 命名说明
 *
 * `zcvsd1wr2t` / `sdwioxccsd` / `getEncodedP` 是原版混淆名，逐字保留 ——
 * 改成可读名会让 JNI 查表失败（JNI 按名字精确匹配）。
 */
internal object NativeEncodersDoc  // 仅作文档载体，无成员