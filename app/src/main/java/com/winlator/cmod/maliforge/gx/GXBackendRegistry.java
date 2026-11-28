package com.winlator.cmod.maliforge.gx;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Registry of known Winlator compatibility routes and MaliForge experimental backend metadata. */
public final class GXBackendRegistry {
    public static final String CURRENT = "winlator-current";
    public static final String DXVK = "winlator-dxvk";
    public static final String VKD3D = "winlator-vkd3d";
    public static final String WINED3D = "winlator-wined3d";
    /** Canonical MaliForge GX identifier. It is not a Container.WRAPPER_DRIVERS value yet. */
    public static final String MALIFORGE_NATIVE = "maliforge_gx";
    /** Historical diagnostic identifier; retained for log/config migration code only. */
    public static final String LEGACY_MALIFORGE_NATIVE = "maliforge-gx-native";

    private final Map<String, GXBackendDescriptor> descriptors = new LinkedHashMap<>();
    private final Map<String, GXBackendInitializationResult> lastResults = new LinkedHashMap<>();

    public synchronized boolean register(GXBackendDescriptor descriptor) {
        if (descriptor == null || descriptors.containsKey(descriptor.getId())) return false;
        descriptors.put(descriptor.getId(), descriptor);
        return true;
    }

    public synchronized GXBackendDescriptor get(String id) {
        return id == null ? null : descriptors.get(id);
    }

    /** Records validation or initialization state for a registered backend only. */
    public synchronized boolean recordResult(GXBackendInitializationResult result) {
        if (result == null || !descriptors.containsKey(result.getBackendId())) return false;
        if (result.getStatus() == GXBackendStatus.READY && !result.isInitializationVerified()) return false;
        lastResults.put(result.getBackendId(), result);
        return true;
    }

    public synchronized GXBackendInitializationResult getLastResult(String backendId) {
        return backendId == null ? null : lastResults.get(backendId);
    }

    /** UNKNOWN means that this backend has not yet been checked in the current process. */
    public synchronized GXBackendStatus getStatus(String backendId) {
        GXBackendInitializationResult result = getLastResult(backendId);
        return result == null ? GXBackendStatus.UNKNOWN : result.getStatus();
    }

    public synchronized List<GXBackendDescriptor> snapshot() {
        return Collections.unmodifiableList(new ArrayList<>(descriptors.values()));
    }

    public synchronized int size() { return descriptors.size(); }

    public static GXBackendRegistry createDefault() {
        GXBackendRegistry registry = new GXBackendRegistry();
        Set<String> swapchain = Collections.singleton("VK_KHR_swapchain");
        registry.register(new GXBackendDescriptor(CURRENT, "Keep current Winlator configuration",
                "Winlator-managed", GXBackendDescriptor.Kind.PRESERVE_CURRENT,
                GXBackendDescriptor.RequiredApi.NONE, 0, 0, Collections.emptySet(),
                false, true, false, true, true,
                "Preserves the existing per-container driver and DX wrapper without writing configuration."));
        registry.register(new GXBackendDescriptor(DXVK, "Existing Winlator DXVK path",
                "Winlator-managed", GXBackendDescriptor.Kind.D3D_TRANSLATION,
                GXBackendDescriptor.RequiredApi.VULKAN, 1, 1, swapchain,
                false, true, false, true, true,
                "Describes the existing DXVK integration and packaged DXVK runtime; MaliForge does not initialize it."));
        registry.register(new GXBackendDescriptor(VKD3D, "Existing Winlator VKD3D path",
                "Winlator-managed", GXBackendDescriptor.Kind.D3D_TRANSLATION,
                GXBackendDescriptor.RequiredApi.VULKAN, 1, 2, swapchain,
                true, true, false, true, true,
                "Describes the existing VKD3D integration; requires Vulkan 1.3 or VK_KHR_synchronization2 in addition to its baseline."));
        registry.register(new GXBackendDescriptor(WINED3D, "Existing Winlator WineD3D path",
                "Wine-managed", GXBackendDescriptor.Kind.WINE_OPENGL_TRANSLATION,
                GXBackendDescriptor.RequiredApi.NONE, 0, 0, Collections.emptySet(),
                false, true, false, true, true,
                "Compatibility fallback supplied by the current Wine/Winlator build; no GX GLES context is probed."));
        registry.register(new GXBackendDescriptor(MALIFORGE_NATIVE, "MaliForge GX",
                "0.3.0-stage3-experimental", GXBackendDescriptor.Kind.NATIVE_RENDERER,
                GXBackendDescriptor.RequiredApi.VULKAN, 1, 1, swapchain,
                false, false, false, false, true,
                "Experimental Vulkan graphics backend for compatible Mali devices. Unavailable until a real guest renderer package and verified Wine/container initialization hook are installed."));
        return registry;
    }
}
