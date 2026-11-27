package com.winlator.cmod.graphics;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GraphicsCapabilitiesTest {
    @Test
    public void exposesVulkanPhysicalDeviceVendorIdAndExtensions() {
        GraphicsCapabilities capabilities = new GraphicsCapabilities(
                "Mali-G57 MC2",
                "1.3.0",
                0x13B5,
                new String[]{"VK_KHR_swapchain", "VK_KHR_synchronization2"});

        assertTrue(capabilities.hasVendorId());
        assertEquals(0x13B5, capabilities.getVendorId());
        assertTrue(capabilities.hasExtension("vk_khr_swapchain"));
        assertTrue(capabilities.hasExtension("VK_KHR_synchronization2"));
        assertTrue(capabilities.supportsDxvkBaseline());
        assertTrue(capabilities.supportsVkd3dBaseline());
    }

    @Test
    public void legacyConstructorKeepsVendorIdUnavailable() {
        GraphicsCapabilities capabilities = new GraphicsCapabilities(
                "Mali-G57 MC2", "1.1.0", new String[]{"VK_KHR_swapchain"});

        assertFalse(capabilities.hasVendorId());
        assertEquals(0, capabilities.getVendorId());
        assertTrue(capabilities.supportsDxvkBaseline());
    }

    @Test
    public void failedVendorProbeDoesNotInventAnId() {
        GraphicsCapabilities capabilities = new GraphicsCapabilities(
                "Unavailable", "Unavailable", 0, null);

        assertFalse(capabilities.hasVendorId());
        assertEquals(0, capabilities.getVendorId());
        assertFalse(capabilities.supportsDxvkBaseline());
        assertFalse(capabilities.hasExtension("VK_KHR_swapchain"));
    }
}
