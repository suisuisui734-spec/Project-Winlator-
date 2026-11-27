#include <jni.h>
#include <android/api-level.h>
#include <mutex>
#include <cstdio>

namespace {
enum RuntimeState : jint {
    UNINITIALIZED = 0, INITIALIZING = 1, READY = 2, DEGRADED = 3,
    FAILED = 4, RECOVERING = 5, DISABLED = 6
};
enum RuntimeError : jint {
    NO_ERROR = 0, UNSUPPORTED_ABI = 1, UNSUPPORTED_POINTER_SIZE = 2,
    API_LEVEL_UNAVAILABLE = 3
};
std::mutex stateMutex;
RuntimeState state = UNINITIALIZED;
RuntimeError errorCode = NO_ERROR;
const char* abiName() {
#if defined(__aarch64__)
    return "arm64-v8a";
#elif defined(__arm__)
    return "armeabi-v7a";
#elif defined(__x86_64__)
    return "x86_64";
#elif defined(__i386__)
    return "x86";
#else
    return "unknown";
#endif
}
const char* stateName(RuntimeState value) {
    switch (value) {
        case UNINITIALIZED: return "UNINITIALIZED";
        case INITIALIZING: return "INITIALIZING";
        case READY: return "READY";
        case DEGRADED: return "DEGRADED";
        case FAILED: return "FAILED";
        case RECOVERING: return "RECOVERING";
        case DISABLED: return "DISABLED";
        default: return "UNKNOWN";
    }
}
}
extern "C" JNIEXPORT jint JNICALL
Java_com_winlator_cmod_maliforge_gx_GXNativeBridge_nativeInitialize(JNIEnv*, jclass) {
    std::lock_guard<std::mutex> guard(stateMutex);
    if (state == READY) return NO_ERROR;
    state = INITIALIZING;
    errorCode = NO_ERROR;
#if !defined(__aarch64__)
    errorCode = UNSUPPORTED_ABI; state = FAILED; return errorCode;
#else
    if (sizeof(void*) != 8U) {
        errorCode = UNSUPPORTED_POINTER_SIZE; state = FAILED; return errorCode;
    }
#endif
    if (android_get_device_api_level() < 0) {
        state = DEGRADED; errorCode = API_LEVEL_UNAVAILABLE; return errorCode;
    }
    // Support bridge only: no Vulkan instance, ICD, EGL context or render hook is created.
    state = READY;
    return NO_ERROR;
}
extern "C" JNIEXPORT jint JNICALL
Java_com_winlator_cmod_maliforge_gx_GXNativeBridge_nativeGetState(JNIEnv*, jclass) {
    std::lock_guard<std::mutex> guard(stateMutex); return state;
}
extern "C" JNIEXPORT jint JNICALL
Java_com_winlator_cmod_maliforge_gx_GXNativeBridge_nativeGetErrorCode(JNIEnv*, jclass) {
    std::lock_guard<std::mutex> guard(stateMutex); return errorCode;
}
extern "C" JNIEXPORT jstring JNICALL
Java_com_winlator_cmod_maliforge_gx_GXNativeBridge_nativeGetAbi(JNIEnv* env, jclass) {
    return env->NewStringUTF(abiName());
}
extern "C" JNIEXPORT jint JNICALL
Java_com_winlator_cmod_maliforge_gx_GXNativeBridge_nativeGetAndroidApiLevel(JNIEnv*, jclass) {
    return android_get_device_api_level();
}
extern "C" JNIEXPORT jstring JNICALL
Java_com_winlator_cmod_maliforge_gx_GXNativeBridge_nativeGetDiagnostics(JNIEnv* env, jclass) {
    std::lock_guard<std::mutex> guard(stateMutex);
    char output[256];
    std::snprintf(output, sizeof(output),
        "MaliForge GX native bridge 0.2.0-stage2; ABI=%s; state=%s; error=%d; graphics backend not bundled",
        abiName(), stateName(state), static_cast<int>(errorCode));
    return env->NewStringUTF(output);
}
extern "C" JNIEXPORT void JNICALL
Java_com_winlator_cmod_maliforge_gx_GXNativeBridge_nativeShutdown(JNIEnv*, jclass) {
    std::lock_guard<std::mutex> guard(stateMutex);
    state = UNINITIALIZED; errorCode = NO_ERROR;
}
