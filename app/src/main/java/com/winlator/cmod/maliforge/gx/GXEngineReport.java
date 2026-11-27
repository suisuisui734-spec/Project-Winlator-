package com.winlator.cmod.maliforge.gx;

/** Diagnostic-only snapshot. This milestone does not install a Vulkan ICD or render hook. */
public final class GXEngineReport {
    public static final String RUNTIME_NOT_INTEGRATED =
            "FOUNDATION_ONLY: no MaliForge Vulkan ICD or rendering hook is installed";

    private final GXCapabilityReport capabilities;
    private final GXBackendPlan backendPlan;

    GXEngineReport(GXCapabilityReport capabilities, GXBackendPlan backendPlan) {
        this.capabilities = capabilities;
        this.backendPlan = backendPlan;
    }

    public GXCapabilityReport getCapabilities() { return capabilities; }
    public GXBackendPlan getBackendPlan() { return backendPlan; }
    public String getRuntimeStatus() { return RUNTIME_NOT_INTEGRATED; }
    public boolean isRuntimeDriverAvailable() { return false; }
}
