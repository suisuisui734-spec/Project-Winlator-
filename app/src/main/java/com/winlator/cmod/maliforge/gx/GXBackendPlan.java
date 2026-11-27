package com.winlator.cmod.maliforge.gx;

import com.winlator.cmod.execution.DependencyDetector;
import com.winlator.cmod.graphics.GraphicsBackendSelector;
import com.winlator.cmod.graphics.GraphicsCapabilities;
import com.winlator.cmod.graphics.MaliBackendSelector;

/** Capability-gated backend recommendation. It never applies or changes a user's renderer. */
public final class GXBackendPlan {
    private final MaliBackendSelector.Backend backend;
    private final boolean baselineValidated;
    private final String reason;

    private GXBackendPlan(MaliBackendSelector.Backend backend, boolean baselineValidated, String reason) {
        this.backend = backend;
        this.baselineValidated = baselineValidated;
        this.reason = reason == null ? "" : reason;
    }

    public static GXBackendPlan recommend(GraphicsCapabilities capabilities,
                                          DependencyDetector.GraphicsApi api) {
        MaliBackendSelector.Recommendation recommendation =
                GraphicsBackendSelector.select(capabilities, api);
        return new GXBackendPlan(recommendation.backend,
                recommendation.baselineValidated, recommendation.reason);
    }

    public MaliBackendSelector.Backend getBackend() { return backend; }
    public boolean isBaselineValidated() { return baselineValidated; }
    public String getReason() { return reason; }
    public boolean requiresManualConfirmation() { return true; }
}
