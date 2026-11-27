package com.winlator.cmod.graphics;

import com.winlator.cmod.execution.DependencyDetector;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MaliBackendSelectorTest {
    @Test
    public void dxvkRequiresVulkanAndSwapchain() {
        GraphicsCapabilities missing = new GraphicsCapabilities("Mali-G57", "Unavailable", null);
        assertFalse(missing.supportsDxvkBaseline());
        assertEquals(MaliBackendSelector.Backend.WINED3D,
                MaliBackendSelector.recommend(missing, DependencyDetector.GraphicsApi.DIRECT3D11).backend);
        GraphicsCapabilities supported = new GraphicsCapabilities(
                "Mali-G57 MC2", "Vulkan 1.1", new String[]{"VK_KHR_swapchain"});
        assertTrue(supported.isMali());
        assertTrue(supported.supportsDxvkBaseline());
        assertEquals(MaliBackendSelector.Backend.DXVK,
                MaliBackendSelector.recommend(supported, DependencyDetector.GraphicsApi.DIRECT3D11).backend);
    }

    @Test
    public void vkd3dUsesConservativeVulkan12Baseline() {
        GraphicsCapabilities capabilities = new GraphicsCapabilities(
                "Mali-G57", "1.2.0", new String[]{"VK_KHR_swapchain"});
        assertFalse(capabilities.supportsVkd3dBaseline());
        assertEquals(MaliBackendSelector.Backend.WINED3D,
                MaliBackendSelector.recommend(capabilities, DependencyDetector.GraphicsApi.DIRECT3D12).backend);
        GraphicsCapabilities vk13 = new GraphicsCapabilities(
                "Mali-G57", "1.3.0", new String[]{"VK_KHR_swapchain"});
        assertTrue(vk13.supportsVkd3dBaseline());
    }

    @Test
    public void unknownProbePreservesRequestedWrapper() {
        GraphicsCapabilities unknown = new GraphicsCapabilities("", "", null);
        assertEquals("dxvk;vkd3d;none",
                GraphicsFallbackManager.selectSafeDxWrapper("dxvk;vkd3d;none", unknown));
    }
}
