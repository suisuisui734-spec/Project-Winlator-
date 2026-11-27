package com.winlator.cmod.maliforge.gx;

import com.winlator.cmod.graphics.GraphicsCapabilities;

/** Immutable, read-only report from the currently exposed Android graphics driver probe. */
public final class GXCapabilityReport {
    public enum Status { DETECTED, PARTIAL, UNKNOWN }

    private final String renderer;
    private final String vulkanVersion;
    private final int extensionCount;
    private final boolean mali;
    private final boolean vulkanVersionAvailable;
    private final boolean dxvkBaseline;
    private final boolean vkd3dBaseline;
    private final Status status;

    private GXCapabilityReport(GraphicsCapabilities capabilities) {
        renderer = capabilities.getRenderer();
        vulkanVersion = capabilities.getVulkanVersion();
        extensionCount = capabilities.getExtensionCount();
        mali = capabilities.isMali();
        vulkanVersionAvailable = capabilities.hasVulkanVersion();
        dxvkBaseline = capabilities.supportsDxvkBaseline();
        vkd3dBaseline = capabilities.supportsVkd3dBaseline();
        if ("Unavailable".equalsIgnoreCase(renderer) && !vulkanVersionAvailable) {
            status = Status.UNKNOWN;
        } else if (vulkanVersionAvailable && !"Unavailable".equalsIgnoreCase(renderer)) {
            status = Status.DETECTED;
        } else {
            status = Status.PARTIAL;
        }
    }

    public static GXCapabilityReport from(GraphicsCapabilities capabilities) {
        if (capabilities == null) capabilities = new GraphicsCapabilities(null, null, null);
        return new GXCapabilityReport(capabilities);
    }

    public String getRenderer() { return renderer; }
    public String getVulkanVersion() { return vulkanVersion; }
    public int getExtensionCount() { return extensionCount; }
    public boolean isMali() { return mali; }
    public boolean hasVulkanVersion() { return vulkanVersionAvailable; }
    public boolean supportsDxvkBaseline() { return dxvkBaseline; }
    public boolean supportsVkd3dBaseline() { return vkd3dBaseline; }
    public Status getStatus() { return status; }
}
