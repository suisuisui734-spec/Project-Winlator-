package com.winlator.cmod.graphics;

import com.winlator.cmod.execution.DependencyDetector;

/** Capability-gated backend recommendation. This class never selects Adreno Turnip on Mali. */
public final class MaliBackendSelector {
    public enum Backend { DXVK, VKD3D, WINED3D, KEEP_CURRENT }

    public static final class Recommendation {
        public final Backend backend;
        public final boolean baselineValidated;
        public final String reason;
        private Recommendation(Backend backend, boolean validated, String reason) {
            this.backend = backend;
            baselineValidated = validated;
            this.reason = reason;
        }
    }

    private MaliBackendSelector() {}

    public static Recommendation recommend(GraphicsCapabilities caps,
                                            DependencyDetector.GraphicsApi api) {
        if (caps == null || api == null || api == DependencyDetector.GraphicsApi.UNKNOWN
                || api == DependencyDetector.GraphicsApi.VULKAN) {
            return new Recommendation(Backend.KEEP_CURRENT, false,
                    "The executable API or active-driver capabilities are not sufficiently known.");
        }
        switch (api) {
            case DIRECT3D12:
                return caps.supportsVkd3dBaseline()
                        ? new Recommendation(Backend.VKD3D, true,
                            "Conservative Vulkan VKD3D baseline detected; per-game feature compatibility remains unverified.")
                        : new Recommendation(Backend.WINED3D, caps.hasVulkanVersion(),
                            "Vulkan VKD3D baseline not detected; prefer the existing WineD3D/OpenGL fallback.");
            case DIRECT3D8:
            case DIRECT3D9:
            case DIRECT3D10:
            case DIRECT3D11:
                return caps.supportsDxvkBaseline()
                        ? new Recommendation(Backend.DXVK, true,
                            "Conservative Vulkan DXVK baseline detected; per-game extension compatibility remains unverified.")
                        : new Recommendation(Backend.WINED3D, caps.hasVulkanVersion(),
                            "Vulkan DXVK baseline not detected; prefer the existing WineD3D/OpenGL fallback.");
            case DIRECTDRAW:
            case OPENGL:
                return new Recommendation(Backend.WINED3D, true,
                        "Use the existing DirectDraw/OpenGL-compatible path.");
            default:
                return new Recommendation(Backend.KEEP_CURRENT, false,
                        "Keep the current per-game backend because no safe recommendation is available.");
        }
    }
}
