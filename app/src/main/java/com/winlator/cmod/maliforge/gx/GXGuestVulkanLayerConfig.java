package com.winlator.cmod.maliforge.gx;

public final class GXGuestVulkanLayerConfig {
    public static final String LAYER_NAME = "VK_LAYER_MALIFORGE_gx_diagnostics";
    public static final String ENABLE_ENV = "MALIFORGE_GX_VULKAN_LAYER";
    public static final String DISABLE_ENV = "MALIFORGE_GX_VULKAN_LAYER_DISABLE";
    public static final String ADD_PATH_ENV = "VK_ADD_IMPLICIT_LAYER_PATH";
    public static final String LOG_ENV = "MALIFORGE_GX_LAYER_LOG";

    private GXGuestVulkanLayerConfig() {}

    public static boolean isRequested(String value) {
        if (value == null) return false;
        String normalized = value.trim();
        return "1".equals(normalized) || "true".equalsIgnoreCase(normalized)
                || "yes".equalsIgnoreCase(normalized) || "on".equalsIgnoreCase(normalized);
    }

    public static Activation prepare(String request, String guestArchitecture,
                                     boolean installAvailable, String existingAddPaths,
                                     String disable, String manifestDirectory, String logPath) {
        if (!isRequested(request)) return Activation.disabled("Layer was not requested.");
        if (isRequested(disable)) return Activation.disabled("Layer is disabled by explicit opt-out.");
        if (!"x86_64".equalsIgnoreCase(guestArchitecture == null ? "" : guestArchitecture.trim()))
            return Activation.disabled("Only x86_64 Wine guests are supported.");
        if (!installAvailable) return Activation.disabled("Guest layer installation is unavailable.");
        if (manifestDirectory == null || manifestDirectory.trim().isEmpty())
            return Activation.disabled("Implicit-layer manifest directory is unavailable.");
        if (logPath == null || logPath.trim().isEmpty())
            return Activation.disabled("Diagnostics log path is unavailable.");

        String path = manifestDirectory.trim();
        String current = existingAddPaths == null ? "" : existingAddPaths.trim();
        if (current.isEmpty()) current = path;
        else {
            boolean alreadyPresent = false;
            for (String entry : current.split(java.util.regex.Pattern.quote(java.io.File.pathSeparator))) {
                if (path.equals(entry.trim())) { alreadyPresent = true; break; }
            }
            if (!alreadyPresent) current += java.io.File.pathSeparator + path;
        }
        return new Activation(true, "", current, logPath.trim());
    }

    public static final class Activation {
        public final boolean enabled;
        public final String reason;
        public final String addImplicitLayerPaths;
        public final String logPath;

        private Activation(boolean enabled, String reason, String paths, String logPath) {
            this.enabled = enabled;
            this.reason = reason == null ? "" : reason;
            this.addImplicitLayerPaths = paths == null ? "" : paths;
            this.logPath = logPath == null ? "" : logPath;
        }
        private static Activation disabled(String reason) {
            return new Activation(false, reason, "", "");
        }
    }
}
