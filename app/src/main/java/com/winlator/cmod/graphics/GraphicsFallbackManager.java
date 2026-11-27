package com.winlator.cmod.graphics;

/**
 * Safely reduces a selected DX wrapper when the system Vulkan baseline is known
 * to be insufficient. Unknown probes preserve the user's existing selection.
 */
public final class GraphicsFallbackManager {
    private GraphicsFallbackManager() {}

    public static String selectSafeDxWrapper(String requested, GraphicsCapabilities caps) {
        if (requested == null || requested.trim().isEmpty() || caps == null || !caps.hasVulkanVersion()) {
            return requested;
        }
        String normalized = requested.toLowerCase(java.util.Locale.ROOT);
        boolean dxvk = normalized.contains("dxvk");
        boolean vkd3d = normalized.contains("vkd3d");
        if (!dxvk && !vkd3d) return requested;
        boolean dxvkOkay = caps.supportsDxvkBaseline();
        boolean vkd3dOkay = caps.supportsVkd3dBaseline();

        if (dxvk && !dxvkOkay) return "wined3d";
        if (vkd3d && !vkd3dOkay) {
            // Winlator stores compound wrapper choices as semicolon-separated DXVK/VKD3D/DDraw IDs.
            String[] parts = requested.split(";", -1);
            if (parts.length >= 3 && parts[0].toLowerCase(java.util.Locale.ROOT).contains("dxvk")) {
                return parts[0] + ";None;" + parts[2];
            }
            // Unknown encoding: do not mutate an unrecognized user configuration.
        }
        return requested;
    }
}
