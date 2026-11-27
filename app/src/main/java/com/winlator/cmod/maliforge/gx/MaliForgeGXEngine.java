package com.winlator.cmod.maliforge.gx;

import android.content.Context;
import com.winlator.cmod.execution.DependencyDetector;
import com.winlator.cmod.graphics.GraphicsCapabilities;
import com.winlator.cmod.graphics.GraphicsCapabilityDetector;

public final class MaliForgeGXEngine {
    public static final String VERSION = "0.2.0-stage2";
    private MaliForgeGXEngine() {}
    public static GXEngineReport inspect(Context context, DependencyDetector.GraphicsApi api) {
        GraphicsCapabilities caps = context == null ? new GraphicsCapabilities(null, null, null)
                : GraphicsCapabilityDetector.detect(context);
        GXCapabilityReport capabilityReport = GXCapabilityReport.from(caps);
        GXBackendPlan plan = GXBackendPlan.recommend(caps, api);
        GXNativeRuntime.Snapshot nativeSnapshot = GXNativeRuntime.getInstance().initialize();
        GXRuntimeDiagnostics diagnostics = new GXRuntimeDiagnostics(nativeSnapshot, capabilityReport, VERSION);
        return new GXEngineReport(capabilityReport, plan, diagnostics);
    }
}
