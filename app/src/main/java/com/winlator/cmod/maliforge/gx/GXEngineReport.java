package com.winlator.cmod.maliforge.gx;

public final class GXEngineReport {
    public static final String RUNTIME_NOT_INTEGRATED =
            "NATIVE_BRIDGE_ONLY: no MaliForge Vulkan ICD or rendering hook is installed";
    private final GXCapabilityReport capabilities;
    private final GXBackendPlan backendPlan;
    private final GXRuntimeDiagnostics runtimeDiagnostics;
    GXEngineReport(GXCapabilityReport capabilities, GXBackendPlan backendPlan,
                   GXRuntimeDiagnostics runtimeDiagnostics) {
        this.capabilities = capabilities;
        this.backendPlan = backendPlan;
        this.runtimeDiagnostics = runtimeDiagnostics;
    }
    public GXCapabilityReport getCapabilities() { return capabilities; }
    public GXBackendPlan getBackendPlan() { return backendPlan; }
    public GXRuntimeDiagnostics getRuntimeDiagnostics() { return runtimeDiagnostics; }
    public String getRuntimeStatus() { return RUNTIME_NOT_INTEGRATED; }
    public boolean isRuntimeDriverAvailable() { return false; }
}
