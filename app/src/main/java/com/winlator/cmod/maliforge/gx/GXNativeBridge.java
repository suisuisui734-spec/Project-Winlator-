package com.winlator.cmod.maliforge.gx;

final class GXNativeBridge {
    private static final boolean LOADED;
    private static final String LOAD_ERROR;
    static {
        boolean loaded = false;
        String error = "";
        try { System.loadLibrary("winlator"); loaded = true; }
        catch (UnsatisfiedLinkError | SecurityException e) {
            error = e.getClass().getSimpleName() + ": " + (e.getMessage() == null ? "No details" : e.getMessage());
        }
        LOADED = loaded;
        LOAD_ERROR = error;
    }
    private GXNativeBridge() {}
    static boolean isLoaded() { return LOADED; }
    static String loadError() { return LOAD_ERROR; }
    static int initialize() { return LOADED ? nativeInitialize() : GXNativeRuntime.ERROR_LIBRARY_UNAVAILABLE; }
    static int state() { return LOADED ? nativeGetState() : GXNativeRuntime.STATE_FAILED; }
    static int error() { return LOADED ? nativeGetErrorCode() : GXNativeRuntime.ERROR_LIBRARY_UNAVAILABLE; }
    static String abi() { return LOADED ? safe(nativeGetAbi()) : "Unavailable"; }
    static int api() { return LOADED ? nativeGetAndroidApiLevel() : -1; }
    static String diagnostics() { return LOADED ? safe(nativeGetDiagnostics()) : LOAD_ERROR; }
    static void shutdown() { if (LOADED) nativeShutdown(); }
    private static String safe(String value) {
        return value == null || value.trim().isEmpty() ? "Unavailable" : value;
    }
    private static native int nativeInitialize();
    private static native int nativeGetState();
    private static native int nativeGetErrorCode();
    private static native String nativeGetAbi();
    private static native int nativeGetAndroidApiLevel();
    private static native String nativeGetDiagnostics();
    private static native void nativeShutdown();
}
