package com.winlator.cmod.maliforge.gx;

public final class GXNativeRuntime {
    public static final int STATE_UNINITIALIZED = 0, STATE_INITIALIZING = 1, STATE_READY = 2,
            STATE_DEGRADED = 3, STATE_FAILED = 4, STATE_RECOVERING = 5, STATE_DISABLED = 6;
    public static final int ERROR_NONE = 0, ERROR_UNSUPPORTED_ABI = 1,
            ERROR_UNSUPPORTED_POINTER_SIZE = 2, ERROR_ANDROID_API_UNAVAILABLE = 3,
            ERROR_LIBRARY_UNAVAILABLE = 100, ERROR_NATIVE_INITIALIZATION = 101;

    private static final GXNativeRuntime INSTANCE = new GXNativeRuntime();
    private volatile Snapshot snapshot = Snapshot.empty();
    private GXNativeRuntime() {}
    public static GXNativeRuntime getInstance() { return INSTANCE; }

    public synchronized Snapshot initialize() {
        if (!GXNativeBridge.isLoaded()) {
            snapshot = Snapshot.failure(STATE_FAILED, ERROR_LIBRARY_UNAVAILABLE,
                    "libwinlator.so unavailable: " + GXNativeBridge.loadError(), "Unavailable", -1,
                    GXHostVulkanProbe.unavailable("Native library unavailable"),
                    GXVulkanDeviceBootstrap.unavailable("Native library unavailable"));
            return snapshot;
        }
        try {
            int result = GXNativeBridge.initialize();
            int state = GXNativeBridge.state();
            int error = GXNativeBridge.error();
            String abi = GXNativeBridge.abi();
            int api = GXNativeBridge.api();
            String detail = GXNativeBridge.diagnostics();
            GXHostVulkanProbe.Result hostProbe = GXHostVulkanProbe.unavailable("Native Vulkan probe was not run");
            GXVulkanDeviceBootstrap.Result deviceBootstrap =
                    GXVulkanDeviceBootstrap.unavailable("Logical-device bootstrap was not run");
            if (result == ERROR_NONE && state == STATE_READY) {
                try {
                    hostProbe = GXHostVulkanProbe.parse(GXNativeBridge.probeHostVulkan());
                    if (hostProbe.available) {
                        try {
                            deviceBootstrap = GXVulkanDeviceBootstrap.parse(
                                    GXNativeBridge.initializeHostVulkanDevice());
                        } catch (UnsatisfiedLinkError | RuntimeException deviceError) {
                            deviceBootstrap = GXVulkanDeviceBootstrap.unavailable(
                                    "Native Vulkan device bootstrap failed: "
                                            + deviceError.getClass().getSimpleName());
                        }
                    } else {
                        deviceBootstrap = GXVulkanDeviceBootstrap.unavailable(
                                "Host Vulkan physical-device probe did not pass.");
                    }
                } catch (UnsatisfiedLinkError | RuntimeException probeError) {
                    hostProbe = GXHostVulkanProbe.unavailable("Native Vulkan probe failed: "
                            + probeError.getClass().getSimpleName());
                }
            }
            if (result != ERROR_NONE || state != STATE_READY) {
                if (error == ERROR_NONE) error = result == ERROR_NONE
                        ? ERROR_NATIVE_INITIALIZATION : result;
                snapshot = Snapshot.failure(state, error, detail, abi, api, hostProbe, deviceBootstrap);
            } else snapshot = Snapshot.ready(detail, abi, api, hostProbe, deviceBootstrap);
        } catch (UnsatisfiedLinkError | RuntimeException e) {
            snapshot = Snapshot.failure(STATE_FAILED, ERROR_NATIVE_INITIALIZATION,
                    e.getClass().getSimpleName() + ": "
                    + (e.getMessage() == null ? "No details" : e.getMessage()),
                    "Unavailable", -1, GXHostVulkanProbe.unavailable("Native initialization failed"),
                    GXVulkanDeviceBootstrap.unavailable("Native initialization failed"));
        }
        return snapshot;
    }

    public Snapshot getSnapshot() { return snapshot; }
    public synchronized void shutdown() {
        try { GXNativeBridge.shutdownHostVulkanDevice(); }
        catch (UnsatisfiedLinkError | RuntimeException ignored) { }
        try { GXNativeBridge.shutdown(); }
        catch (UnsatisfiedLinkError | RuntimeException ignored) { }
        snapshot = Snapshot.empty();
    }

    public static String stateName(int state) {
        switch (state) {
            case STATE_UNINITIALIZED: return "UNINITIALIZED";
            case STATE_INITIALIZING: return "INITIALIZING";
            case STATE_READY: return "READY";
            case STATE_DEGRADED: return "DEGRADED";
            case STATE_FAILED: return "FAILED";
            case STATE_RECOVERING: return "RECOVERING";
            case STATE_DISABLED: return "DISABLED";
            default: return "UNKNOWN";
        }
    }

    public static final class Snapshot {
        public final int state, errorCode, androidApiLevel;
        public final String diagnostics, abi;
        public final GXHostVulkanProbe.Result hostVulkanProbe;
        public final GXVulkanDeviceBootstrap.Result vulkanDeviceBootstrap;
        public final boolean nativeBridgeReady;
        public final boolean graphicsBackendAvailable;
        private Snapshot(int state, int error, String detail, String abi, int api, boolean ready,
                         GXHostVulkanProbe.Result hostVulkanProbe,
                         GXVulkanDeviceBootstrap.Result vulkanDeviceBootstrap) {
            this.state = state;
            errorCode = error;
            diagnostics = detail == null ? "" : detail;
            this.abi = abi == null ? "Unavailable" : abi;
            androidApiLevel = api;
            nativeBridgeReady = ready;
            graphicsBackendAvailable = false;
            this.hostVulkanProbe = hostVulkanProbe == null
                    ? GXHostVulkanProbe.unavailable("No probe result") : hostVulkanProbe;
            this.vulkanDeviceBootstrap = vulkanDeviceBootstrap == null
                    ? GXVulkanDeviceBootstrap.unavailable("No device bootstrap result")
                    : vulkanDeviceBootstrap;
        }
        private static Snapshot empty() {
            return new Snapshot(STATE_UNINITIALIZED, ERROR_NONE,
                    "Native bridge has not been initialized", "Unknown", -1, false,
                    GXHostVulkanProbe.unavailable("Native bridge has not been initialized"),
                    GXVulkanDeviceBootstrap.unavailable("Native bridge has not been initialized"));
        }
        private static Snapshot ready(String detail, String abi, int api,
                                      GXHostVulkanProbe.Result hostVulkanProbe,
                                      GXVulkanDeviceBootstrap.Result vulkanDeviceBootstrap) {
            return new Snapshot(STATE_READY, ERROR_NONE, detail, abi, api, true,
                    hostVulkanProbe, vulkanDeviceBootstrap);
        }
        private static Snapshot failure(int state, int error, String detail, String abi, int api,
                                        GXHostVulkanProbe.Result hostVulkanProbe,
                                        GXVulkanDeviceBootstrap.Result vulkanDeviceBootstrap) {
            return new Snapshot(state, error, detail, abi, api, false,
                    hostVulkanProbe, vulkanDeviceBootstrap);
        }
        public String getStateName() { return stateName(state); }
    }
}
