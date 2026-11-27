package com.winlator.cmod.maliforge.gx;

import android.content.Context;

import com.winlator.cmod.execution.DependencyDetector;
import com.winlator.cmod.graphics.GraphicsCapabilities;
import com.winlator.cmod.graphics.GraphicsCapabilityDetector;

/**
 * MaliForge GX foundation entry point.
 * Performs read-only capability inspection and planning without replacing any driver.
 */
public final class MaliForgeGXEngine {
    public static final String VERSION = "0.1.0-foundation";

    private MaliForgeGXEngine() {}

    public static GXEngineReport inspect(Context context, DependencyDetector.GraphicsApi api) {
        GraphicsCapabilities capabilities = context == null
                ? new GraphicsCapabilities(null, null, null)
                : GraphicsCapabilityDetector.detect(context);
        GXCapabilityReport capabilityReport = GXCapabilityReport.from(capabilities);
        GXBackendPlan backendPlan = GXBackendPlan.recommend(capabilities, api);
        return new GXEngineReport(capabilityReport, backendPlan);
    }
}
