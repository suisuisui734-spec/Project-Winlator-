package com.winlator.cmod.maliforge.gx;

/**
 * Static release metadata only. This is not a readiness assertion for a GPU backend.
 */
public final class GXReleaseInfo {
    public static final String VERSION = "0.1.0-experimental";
    public static final String CHANNEL = "EXPERIMENTAL";
    public static final int STAGE_COUNT = 23;
    public static final boolean DEVICE_VALIDATION_REQUIRED = true;
    public static final boolean GAME_RENDERER_INCLUDED = false;

    private GXReleaseInfo() { }

    public static String getRuntimeReadiness() {
        return "EXPERIMENTAL — Android Mali device and real Winlator game validation required";
    }
}
