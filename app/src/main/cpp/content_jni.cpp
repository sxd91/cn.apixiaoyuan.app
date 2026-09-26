// content_jni.cpp —— 调用 libContentEncoder.so 的 getEncodedP 做内容编解码
//
// ## 为什么不能用 System.loadLibrary + external fun
//
// 与 libRequestEncoder.so 同构：libContentEncoder.so 的 JNI_OnLoad 内
// `RegisterNatives` 把它唯一的方法注册到类
// `com/fenbi/android/leo/imgsearch/sdk/utils/e`（已由 so 内字符串 +
// `.data.rel.ro` 重定位双重确证，见下）。该类在本工程不存在，
// System.loadLibrary 会因 RegisterNatives 失败（ClassNotFoundException /
// NoSuchMethodError），库虽加载但方法不可用。
//
// ## 静态确证的注册信息（libContentEncoder.so，298144 字节）
//
// JNI_OnLoad @ 0x1ee2c，`FindClass("com/fenbi/android/leo/imgsearch/sdk/utils/e")`
// 后 `RegisterNatives(clazz, methods, 1)`，methods 数组在 vaddr 0x45bf8：
//
//   off 0x45bf8  R_AARCH64_RELATIVE addend 0x1466f  → 方法名（"c"）
//   off 0x45c00  R_AARCH64_RELATIVE addend 0x13959  → 签名 "([B)[B"
//   off 0x45c08  R_AARCH64_RELATIVE addend 0x1ecf0  → 函数入口 0x1ecf0
//
// 0x1ecf0 的函数体是标准 JNI 形态：`GetArrayLength`(JNIEnv 表 +1472)
// → `NewByteArray`(+1368) → 内部转换 → `SetByteArrayRegion`(+1408)。
//
// ## 与 sign 的差别
//
// sign 的 chain 只吃 std::string、不碰 JNIEnv；而本函数的入参/返回都是
// `jbyteArray`，**必须带 JNIEnv 调用**。因此这里不能用裸寄存器 shim，
// 而是直接按 `jbyteArray (*)(JNIEnv*, jclass, jbyteArray)` 的 C 签名调用
// —— 这正是 JNI 原生函数的标准 ABI，dlopen 后按偏移取到即可。
//
// 返回的 jbyteArray 由本函数负责取字节并释放。

#include <jni.h>
#include <dlfcn.h>
#include <cstdint>
#include <cstring>
#include <android/log.h>

#define LOG_TAG "ContentBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

// getEncodedP 偏移（以上述 libContentEncoder.so 的 JNI_OnLoad 为基）
static const uintptr_t OFF_GET_ENCODED_P = 0x1ecf0;

typedef jbyteArray (*GetEncodedPFn)(JNIEnv*, jclass, jbyteArray);

static GetEncodedPFn g_fn = nullptr;

extern "C" JNIEXPORT jboolean JNICALL
Java_cn_apixiaoyuan_app_core_native_ContentBridge_nativeInit(JNIEnv* env, jclass, jstring jPath) {
    if (g_fn != nullptr) return JNI_TRUE;
    const char* path = env->GetStringUTFChars(jPath, nullptr);
    void* h = dlopen(path, RTLD_NOW | RTLD_GLOBAL);
    if (h == nullptr) {
        LOGI("dlopen fail: %s", dlerror());
        env->ReleaseStringUTFChars(jPath, path);
        return JNI_FALSE;
    }
    void* jni = dlsym(h, "JNI_OnLoad");
    if (jni == nullptr) {
        LOGI("dlsym JNI_OnLoad fail: %s", dlerror());
        env->ReleaseStringUTFChars(jPath, path);
        return JNI_FALSE;
    }
    g_fn = (GetEncodedPFn)((uintptr_t)jni + OFF_GET_ENCODED_P);
    LOGI("loaded: JNI_OnLoad=%p getEncodedP=%p", jni, (void*)g_fn);
    env->ReleaseStringUTFChars(jPath, path);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_cn_apixiaoyuan_app_core_native_ContentBridge_nativeReady(JNIEnv*, jclass) {
    return g_fn != nullptr ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_cn_apixiaoyuan_app_core_native_ContentBridge_nativeEncode(JNIEnv* env, jclass, jbyteArray in) {
    if (g_fn == nullptr || in == nullptr) return nullptr;
    // 直接把 jbyteArray 交给原生函数：入参只读，返回新数组由我们释放。
    return g_fn(env, nullptr, in);
}