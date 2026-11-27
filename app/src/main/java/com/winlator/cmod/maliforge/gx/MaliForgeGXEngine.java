package com.winlator.cmod.maliforge.gx;

import android.content.Context;
import com.winlator.cmod.execution.DependencyDetector;
import com.winlator.cmod.graphics.GraphicsCapabilities;
import com.winlator.cmod.graphics.GraphicsCapabilityDetector;

public final class MaliForgeGXEngine {
    public static final String VERSION = "0.3.0-stage3-experimental";
    private MaliForgeGXEngine() {}

    public static GXEngineReport inspect(Context context, DependencyDetector.GraphicsApi api) {
        GraphicsCapabilities caps = context == null ? new GraphicsCapabilities(null, null, null)
                : GraphicsCapabilityDetector.detect(context);
        GXCapabilityReport capabilityReport = GXCapabilityReport.from(caps);
        GXBackendPlan plan = GXBackendPlan.recommend(caps, api);
        GXNativeRuntime.Snapshot nativeSnapshot = GXNativeRuntime.getInstance().initialize();

        GXBackendRegistry registry = GXBackendRegistry.createDefault();
        GXBackendCapabilities backendCapabilities = GXBackendCapabilities.from(caps);
        GXBackendValidator validator = new GXBackendValidator();
        GXBackendSelector.Selection selection = new GXBackendSelector().select(
                registry, validator, backendCapabilities, caps,
                GXBackendRegistry.MALIFORGE_NATIVE, GXBackendRegistry.CURRENT);
        GXBackendDescriptor requested = registry.get(GXBackendRegistry.MALIFORGE_NATIVE);
        GXBackendInitializationResult validation = validator.validate(requested, backendCapabilities, caps);
        registry.recordResult(validation);

        GXRuntimeDiagnostics diagnostics = new GXRuntimeDiagnostics(
                nativeSnapshot, capabilityReport, VERSION, backendCapabilities, selection);
        return new GXEngineReport(capabilityReport, plan, diagnostics, selection);
    }
}
