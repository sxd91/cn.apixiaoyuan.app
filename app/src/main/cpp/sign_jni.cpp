// sign_jni.cpp —— 调用 libRequestEncoder.so 的 chain 函数生成主域 SIGN
//
// 为什么不走 System.loadLibrary + external fun：
//   libRequestEncoder.so 的方法走 JNI_OnLoad 内 RegisterNatives 动态注册，
//   注册目标类在 so 内是 `com/yuanfudao/android/leo/stub/SecureStub`（本工程不存在），
//   System.loadLibrary 会因 RegisterNatives 找不到类而失败。
//   因此改为 dlopen + 直接按偏移调用 chain 函数（与逆向验证时的 harness 完全同路径）。
//
// 关键偏移（设备 so，以 JNI_OnLoad 为基）：
//   chain   = JNI_OnLoad + 0x4078
//   T 生成  = JNI_OnLoad + 0x2dc8（chain 内部自行调用，无需外部触发）
//
// ⚠️ 偏移与 so 版本强绑定。两份已见版本的差异：
//   设备版 919,600B  md5 1d9d8e3be5f9f1511d2862b0b1b0addb  chain=+0x4078  md5fn=0x64990
//   旧版   919,568B  md5 e6a9e427a9a8612e83fbf9174541cf5f  chain=+0x406c  md5fn=0x64980
//   （旧版整体小 0x10/0x1c，两版算法输出不同，不可混用）
//
// 将来换 so 时重新定位 chain 的方法（三步，全部静态）：
//   1) 找 getEncodedP：JNI_OnLoad 内 RegisterNatives 的 methods 数组第三项 fnPtr
//      （so 偏移约 0x61bf4），其函数体内有 bl 到 chain 的调用；
//   2) 找 chain：getEncodedP 尾部（约 +0x62794）那条 `bl` 的目标即 chain 入口，
//      特征为函数开头 `sub sp, sp, #0x100` + `stp x29,x30,[sp,#192]`；
//   3) 校验：chain 入口偏移处应能反汇编到 `bl <JNI_OnLoad+0x2dc8>`（T 生成），
//      且函数体内有 4 次 md5 调用（bl md5fn）与 4 次 digest→hex 调用。
//      再到真机跑一次比对抓包 sign，确认无误。
//
// chain 语义（已由反汇编 + 运行时双证）：
//   out = md5hex( A + B + md5hex(A+B) + A + md5hex(...) + T + md5hex(...) + B )
//   其中 A = path，B = salt("wdi4n2t8edr")，T 由 time()/60 派生。

#include <jni.h>
#include <dlfcn.h>
#include <cstdint>
#include <cstring>
#include <cstdlib>
#include <android/log.h>

#define LOG_TAG "SignBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

// 精确寄存器调用垫片（call_shim.S）
//   call_shim(fn=x0, out=x1, a=x2, b=x3, c=w4)
extern "C" void call_shim(void* fn, void* out, void* a, void* b, int c);

// chain 偏移（JNI_OnLoad 相对）
static const uintptr_t OFF_CHAIN = 0x4078;

static void* g_base = nullptr;              // JNI_OnLoad 地址
static void (*g_free)(void*) = nullptr;     // libc++ _ZdlPv（用于回收返回串的堆缓冲）

// ---- libc++ std::string 布局辅助 ----
// 短串(<=22)：[0]=(len<<1)，正文在 [1..]
// 长串      ：[0]=cap|1（低位标记），[8]=len，[16]=ptr
static void lcxx_write(char* buf, const char* s, size_t n) {
    if (n <= 22) {
        buf[0] = (char)(n << 1);
        memcpy(buf + 1, s, n);
        buf[1 + n] = 0;
    } else {
        char* p = (char*)malloc(n + 1);
        memcpy(p, s, n + 1);
        *(unsigned long*)buf = (unsigned long)((n + 16) | 1);
        *(unsigned long*)(buf + 8) = (unsigned long)n;
        *(char**)(buf + 16) = p;
    }
}
static const char* lcxx_ptr(const char* buf) {
    return ((unsigned char)buf[0] & 1) ? *(const char**)(buf + 16) : buf + 1;
}
// 回收 libc++ 长串堆缓冲（短串无堆分配）
static void lcxx_release(char* buf) {
    if (((unsigned char)buf[0] & 1) && g_free) {
        g_free(*(void**)(buf + 16));
    }
    memset(buf, 0, 32);
}

// 线程本地缓冲：避免每次调用都 malloc 容器（链函数只读 A/B，返回串需回收）
static thread_local char t_a[64];
static thread_local char t_b[64];
static thread_local char t_out[64];

extern "C" JNIEXPORT jboolean JNICALL
Java_cn_apixiaoyuan_app_core_sign_SignComputer_nativeInit(JNIEnv* env, jclass, jstring jPath) {
    if (g_base != nullptr) return JNI_TRUE;
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
    g_free = (void (*)(void*))dlsym(h, "_ZdlPv");
    g_base = jni;
    LOGI("loaded: JNI_OnLoad=%p chain=%p free=%p", jni, (void*)((uintptr_t)jni + OFF_CHAIN), (void*)g_free);
    env->ReleaseStringUTFChars(jPath, path);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_cn_apixiaoyuan_app_core_sign_SignComputer_nativeReady(JNIEnv*, jclass) {
    return g_base != nullptr ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_cn_apixiaoyuan_app_core_sign_SignComputer_nativeSign(JNIEnv* env, jclass, jstring jA, jstring jB, jint c) {
    if (g_base == nullptr) return nullptr;
    const char* a = env->GetStringUTFChars(jA, nullptr);
    const char* b = env->GetStringUTFChars(jB, nullptr);
    lcxx_write(t_a, a, strlen(a));
    lcxx_write(t_b, b, strlen(b));
    env->ReleaseStringUTFChars(jA, a);
    env->ReleaseStringUTFChars(jB, b);

    lcxx_release(t_out);
    call_shim((void*)((uintptr_t)g_base + OFF_CHAIN), t_out, t_a, t_b, (int)c);

    const char* sig = lcxx_ptr(t_out);
    jstring ret = env->NewStringUTF(sig);
    // 回收 A/B 的堆缓冲（chain 只读它们）；t_out 留到下次调用前回收
    lcxx_release(t_a);
    lcxx_release(t_b);
    return ret;
}