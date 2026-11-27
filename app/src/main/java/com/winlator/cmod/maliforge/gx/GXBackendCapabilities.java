package com.winlator.cmod.maliforge.gx;

import com.winlator.cmod.graphics.GraphicsCapabilities;

/** Read-only capabilities. Game-context GLES data remains UNKNOWN until a safe probe exists. */
public final class GXBackendCapabilities {
    private final String gpuRenderer;
    private final String vulkanVersion;
    private final int vulkanMajor;
    private final int vulkanMinor;
    private final int vulkanExtensionCount;
    private final GXCapabilityState gpuIdentity;
    private final GXCapabilityState vulkanApi;
    private final GXCapabilityState glesGameContext;

    private GXBackendCapabilities(GraphicsCapabilities source) {
        gpuRenderer = source == null ? "Unavailable" : source.getRenderer();
        vulkanVersion = source == null ? "Unavailable" : source.getVulkanVersion();
        vulkanMajor = source == null ? 0 : source.getVulkanMajor();
        vulkanMinor = source == null ? 0 : source.getVulkanMinor();
        vulkanExtensionCount = source == null ? 0 : source.getExtensionCount();
        gpuIdentity = isAvailable(gpuRenderer) ? GXCapabilityState.SUPPORTED : GXCapabilityState.UNKNOWN;
        // The current Winlator probe returns no separate "probe failed" bit. Missing version is UNKNOWN,
        // not UNSUPPORTED, because lack of evidence cannot safely prove lack of support.
        vulkanApi = source != null && source.hasVulkanVersion()
                ? GXCapabilityState.SUPPORTED : GXCapabilityState.UNKNOWN;
        // Do not borrow the X-server compositor GLES context and call it the game's renderer context.
        glesGameContext = GXCapabilityState.UNKNOWN;
    }

    public static GXBackendCapabilities from(GraphicsCapabilities source) {
        return new GXBackendCapabilities(source);
    }

    public String getGpuRenderer() { return gpuRenderer; }
    public String getVulkanVersion() { return vulkanVersion; }
    public int getVulkanMajor() { return vulkanMajor; }
    public int getVulkanMinor() { return vulkanMinor; }
    public int getVulkanExtensionCount() { return vulkanExtensionCount; }
    public GXCapabilityState getGpuIdentityState() { return gpuIdentity; }
    public GXCapabilityState getVulkanApiState() { return vulkanApi; }
    public GXCapabilityState getGlesGameContextState() { return glesGameContext; }

    public GXCapabilityState supportsVulkanVersion(int major, int minor) {
        if (vulkanApi == GXCapabilityState.UNKNOWN) return GXCapabilityState.UNKNOWN;
        return vulkanMajor > major || (vulkanMajor == major && vulkanMinor >= minor)
                ? GXCapabilityState.SUPPORTED : GXCapabilityState.UNSUPPORTED;
    }

    public GXCapabilityState supportsVulkanExtension(String name, GraphicsCapabilities source) {
        if (name == null || name.trim().isEmpty() || source == null || vulkanExtensionCount == 0) {
            return GXCapabilityState.UNKNOWN;
        }
        return source.hasExtension(name) ? GXCapabilityState.SUPPORTED : GXCapabilityState.UNSUPPORTED;
    }

    private static boolean isAvailable(String value) {
        return value != null && !value.trim().isEmpty()
                && !"Unavailable".equalsIgnoreCase(value.trim())
                && !"Unknown".equalsIgnoreCase(value.trim());
    }
}
