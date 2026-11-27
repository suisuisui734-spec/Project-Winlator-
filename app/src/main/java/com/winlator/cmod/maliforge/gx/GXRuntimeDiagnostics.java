package com.winlator.cmod.maliforge.gx;

public final class GXRuntimeDiagnostics {
    private final GXNativeRuntime.Snapshot nativeRuntime;
    private final GXCapabilityReport gpuCapabilities;
    private final GXBackendCapabilities backendCapabilities;
    private final GXBackendSelector.Selection backendSelection;
    private final String engineVersion;

    public GXRuntimeDiagnostics(GXNativeRuntime.Snapshot nativeRuntime,
                                GXCapabilityReport gpuCapabilities, String engineVersion) {
        this(nativeRuntime, gpuCapabilities, engineVersion, GXBackendCapabilities.from(null), null);
    }

    public GXRuntimeDiagnostics(GXNativeRuntime.Snapshot nativeRuntime,
                                GXCapabilityReport gpuCapabilities, String engineVersion,
                                GXBackendCapabilities backendCapabilities,
                                GXBackendSelector.Selection backendSelection) {
        this.nativeRuntime = nativeRuntime == null
                ? GXNativeRuntime.getInstance().getSnapshot() : nativeRuntime;
        this.gpuCapabilities = gpuCapabilities == null
                ? GXCapabilityReport.from(null) : gpuCapabilities;
        this.engineVersion = engineVersion == null ? "Unknown" : engineVersion;
        this.backendCapabilities = backendCapabilities == null
                ? GXBackendCapabilities.from(null) : backendCapabilities;
        this.backendSelection = backendSelection;
    }

    public GXNativeRuntime.Snapshot getNativeRuntime() { return nativeRuntime; }
    public GXCapabilityReport getGpuCapabilities() { return gpuCapabilities; }
    public GXBackendCapabilities getBackendCapabilities() { return backendCapabilities; }
    public GXBackendSelector.Selection getBackendSelection() { return backendSelection; }
    public String getEngineVersion() { return engineVersion; }

    /** This report is observational; it never marks a renderer ready or changes a Container. */
    public boolean isReadyForGraphicsSelection() {
        return backendSelection != null && backendSelection.status == GXBackendStatus.READY
                && backendSelection.initializationVerified;
    }

    public String toDiagnosticText() {
        StringBuilder s = new StringBuilder();
        s.append("MaliForge GX ").append(engineVersion).append('\n');
        s.append("Native bridge: ").append(nativeRuntime.getStateName()).append('\n');
        s.append("Native ABI: ").append(nativeRuntime.abi).append('\n');
        s.append("Android API: ").append(nativeRuntime.androidApiLevel < 0
                ? "Unavailable" : String.valueOf(nativeRuntime.androidApiLevel)).append('\n');
        s.append("Native error: ").append(nativeRuntime.errorCode).append('\n');
        s.append("GPU renderer: ").append(gpuCapabilities.getRenderer()).append('\n');
        s.append("GPU identity probe: ").append(backendCapabilities.getGpuIdentityState()).append('\n');
        s.append("Vulkan version: ").append(gpuCapabilities.getVulkanVersion()).append('\n');
        s.append("Native host Vulkan probe: ").append(nativeRuntime.hostVulkanProbe.summary()).append('\n');
        s.append("Vulkan API capability: ").append(backendCapabilities.getVulkanApiState()).append('\n');
        s.append("Vulkan extensions observed: ").append(gpuCapabilities.getExtensionCount()).append('\n');
        s.append("Game OpenGL ES context: ").append(backendCapabilities.getGlesGameContextState()).append('\n');
        s.append("DXVK baseline: ").append(gpuCapabilities.supportsDxvkBaseline()).append('\n');
        s.append("VKD3D baseline: ").append(gpuCapabilities.supportsVkd3dBaseline()).append('\n');
        if (backendSelection != null) {
            s.append("Backend requested: ").append(backendSelection.requestedBackendId).append('\n');
            s.append("Backend selected plan: ").append(backendSelection.selectedBackendId).append('\n');
            s.append("Backend status: ").append(backendSelection.status).append('\n');
            s.append("Backend failure reason: ").append(backendSelection.failureReason).append('\n');
            s.append("Fallback recommended: ").append(backendSelection.fallbackRecommended).append('\n');
            s.append("Fallback applied: ").append(backendSelection.fallbackApplied).append('\n');
            s.append("Initialization verified: ").append(backendSelection.initializationVerified).append('\n');
            s.append("Backend detail: ").append(backendSelection.detail).append('\n');
        } else {
            s.append("Backend registry: unavailable\n");
        }
        s.append("GX graphics backend: NOT AVAILABLE (no GX ICD/renderer registered)\n");
        s.append("Native detail: ").append(nativeRuntime.diagnostics);
        return s.toString();
    }
}
