package com.winlator.cmod.maliforge.gx;

public final class GXRuntimeDiagnostics {
    private final GXNativeRuntime.Snapshot nativeRuntime;
    private final GXCapabilityReport gpuCapabilities;
    private final String engineVersion;
    public GXRuntimeDiagnostics(GXNativeRuntime.Snapshot nativeRuntime,
                                GXCapabilityReport gpuCapabilities, String engineVersion) {
        this.nativeRuntime = nativeRuntime == null
                ? GXNativeRuntime.getInstance().getSnapshot() : nativeRuntime;
        this.gpuCapabilities = gpuCapabilities == null
                ? GXCapabilityReport.from(null) : gpuCapabilities;
        this.engineVersion = engineVersion == null ? "Unknown" : engineVersion;
    }
    public GXNativeRuntime.Snapshot getNativeRuntime() { return nativeRuntime; }
    public GXCapabilityReport getGpuCapabilities() { return gpuCapabilities; }
    public String getEngineVersion() { return engineVersion; }
    public boolean isReadyForGraphicsSelection() { return false; }
    public String toDiagnosticText() {
        StringBuilder s = new StringBuilder();
        s.append("MaliForge GX ").append(engineVersion).append('\n');
        s.append("Native bridge: ").append(nativeRuntime.getStateName()).append('\n');
        s.append("Native ABI: ").append(nativeRuntime.abi).append('\n');
        s.append("Android API: ").append(nativeRuntime.androidApiLevel < 0
                ? "Unavailable" : String.valueOf(nativeRuntime.androidApiLevel)).append('\n');
        s.append("Native error: ").append(nativeRuntime.errorCode).append('\n');
        s.append("GPU renderer: ").append(gpuCapabilities.getRenderer()).append('\n');
        s.append("Vulkan version: ").append(gpuCapabilities.getVulkanVersion()).append('\n');
        s.append("Vulkan extensions: ").append(gpuCapabilities.getExtensionCount()).append('\n');
        s.append("DXVK baseline: ").append(gpuCapabilities.supportsDxvkBaseline()).append('\n');
        s.append("VKD3D baseline: ").append(gpuCapabilities.supportsVkd3dBaseline()).append('\n');
        s.append("GX graphics backend: NOT AVAILABLE (no GX ICD/renderer registered)\n");
        s.append("Native detail: ").append(nativeRuntime.diagnostics);
        return s.toString();
    }
}
