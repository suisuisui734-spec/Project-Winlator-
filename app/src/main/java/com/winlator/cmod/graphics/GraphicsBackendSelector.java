package com.winlator.cmod.graphics;

import com.winlator.cmod.execution.DependencyDetector;

/** Stable selector facade for diagnostics and launch configuration. */
public final class GraphicsBackendSelector {
    private GraphicsBackendSelector() {}
    public static MaliBackendSelector.Recommendation select(
            GraphicsCapabilities caps, DependencyDetector.GraphicsApi api) {
        return MaliBackendSelector.recommend(caps, api);
    }
}
