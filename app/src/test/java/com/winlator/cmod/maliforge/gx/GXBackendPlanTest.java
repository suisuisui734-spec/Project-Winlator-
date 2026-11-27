package com.winlator.cmod.maliforge.gx;

import com.winlator.cmod.execution.DependencyDetector;
import com.winlator.cmod.graphics.GraphicsCapabilities;
import com.winlator.cmod.graphics.MaliBackendSelector;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GXBackendPlanTest {
    @Test
    public void missingVulkanBaselineRecommendsWineD3DFallback() {
        GXBackendPlan plan = GXBackendPlan.recommend(
                new GraphicsCapabilities(null, null, null),
                DependencyDetector.GraphicsApi.DIRECT3D11);

        assertEquals(MaliBackendSelector.Backend.WINED3D, plan.getBackend());
        assertFalse(plan.isBaselineValidated());
        assertTrue(plan.requiresManualConfirmation());
    }

    @Test
    public void supportedBaselineIsOnlyARecommendation() {
        GraphicsCapabilities capabilities = new GraphicsCapabilities(
                "Mali-G57 MC2", "Vulkan 1.1", new String[]{"VK_KHR_swapchain"});
        GXBackendPlan plan = GXBackendPlan.recommend(
                capabilities, DependencyDetector.GraphicsApi.DIRECT3D11);

        assertEquals(MaliBackendSelector.Backend.DXVK, plan.getBackend());
        assertTrue(plan.isBaselineValidated());
        assertTrue(plan.requiresManualConfirmation());
    }
}
