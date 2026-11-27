package com.winlator.cmod.maliforge.gx;

public final class GXGameProfile {
    public enum Mode { ECO, BALANCED, PERFORMANCE, COMPATIBILITY, CUSTOM }
    public enum BackendPreference { AUTO, KEEP_CURRENT }

    private static final String STORAGE_VERSION = "1";
    private final Mode mode;
    private final BackendPreference backendPreference;
    private final int targetFps;
    private final int renderScalePercent;
    private final boolean shaderCacheEnabled;
    private final boolean thermalAware;
    private final boolean safeMode;

    public GXGameProfile(Mode mode, BackendPreference backendPreference, int targetFps,
                         int renderScalePercent, boolean shaderCacheEnabled,
                         boolean thermalAware, boolean safeMode) {
        if (mode == null) throw new IllegalArgumentException("mode is required");
        if (backendPreference == null) throw new IllegalArgumentException("backendPreference is required");
        if (targetFps < 0 || targetFps > 240) throw new IllegalArgumentException("targetFps must be 0..240");
        if (renderScalePercent < 50 || renderScalePercent > 100) {
            throw new IllegalArgumentException("renderScalePercent must be 50..100");
        }
        this.mode = mode;
        this.backendPreference = backendPreference;
        this.targetFps = targetFps;
        this.renderScalePercent = renderScalePercent;
        this.shaderCacheEnabled = shaderCacheEnabled;
        this.thermalAware = thermalAware;
        this.safeMode = safeMode;
    }

    public static GXGameProfile forMode(Mode mode) {
        if (mode == null) mode = Mode.BALANCED;
        switch (mode) {
            case ECO:
                return new GXGameProfile(mode, BackendPreference.AUTO, 30, 75, true, true, true);
            case PERFORMANCE:
                return new GXGameProfile(mode, BackendPreference.AUTO, 0, 100, true, true, true);
            case COMPATIBILITY:
                return new GXGameProfile(mode, BackendPreference.KEEP_CURRENT, 60, 100, true, true, true);
            case CUSTOM:
                return new GXGameProfile(mode, BackendPreference.KEEP_CURRENT, 60, 100, true, true, true);
            case BALANCED:
            default:
                return new GXGameProfile(Mode.BALANCED, BackendPreference.AUTO, 60, 85, true, true, true);
        }
    }

    public static GXGameProfile defaultProfile() { return forMode(Mode.BALANCED); }

    public Mode getMode() { return mode; }
    public BackendPreference getBackendPreference() { return backendPreference; }
    public int getTargetFps() { return targetFps; }
    public int getRenderScalePercent() { return renderScalePercent; }
    public boolean isShaderCacheEnabled() { return shaderCacheEnabled; }
    public boolean isThermalAware() { return thermalAware; }
    public boolean isSafeMode() { return safeMode; }

    public String toStorageValue() {
        return STORAGE_VERSION + "|" + mode.name() + "|" + backendPreference.name() + "|"
                + targetFps + "|" + renderScalePercent + "|"
                + (shaderCacheEnabled ? "1" : "0") + "|"
                + (thermalAware ? "1" : "0") + "|" + (safeMode ? "1" : "0");
    }

    public static GXGameProfile fromStorageValue(String value) {
        if (value == null) return null;
        String[] parts = value.split("\\|", -1);
        if (parts.length != 8 || !STORAGE_VERSION.equals(parts[0])) return null;
        try {
            return new GXGameProfile(
                    Mode.valueOf(parts[1]),
                    BackendPreference.valueOf(parts[2]),
                    Integer.parseInt(parts[3]),
                    Integer.parseInt(parts[4]),
                    parseBoolean(parts[5]),
                    parseBoolean(parts[6]),
                    parseBoolean(parts[7]));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static boolean parseBoolean(String value) {
        if ("1".equals(value)) return true;
        if ("0".equals(value)) return false;
        throw new IllegalArgumentException("invalid boolean value");
    }
}
