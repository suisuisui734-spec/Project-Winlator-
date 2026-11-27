package com.winlator.cmod.maliforge.gx;

import com.winlator.cmod.graphics.GraphicsCapabilities;

import org.junit.Test;

import static org.junit.Assert.*;

public class GXGraphicsDriverAvailabilityTest {
    @Test
    public void nativeRendererAppearsAsUnavailableAndCannotBeSelected() {
        GXGraphicsDriverAvailability availability = GXGraphicsDriverAvailability.inspect(null);

        assertEquals(GXBackendRegistry.MALIFORGE_NATIVE, availability.getDriverId());
        assertEquals(GXBackendStatus.UNAVAILABLE, availability.getStatus());
        assertEquals("MaliForge GX (Unavailable)", availability.getDropdownLabel());
        assertFalse(availability.isSelectable());
        assertFalse(availability.isPackageBundled());
        assertFalse(availability.hasRuntimeInitializer());
        assertFalse(availability.isInitializationVerified());
    }

    @Test
    public void strongHostVulkanCapabilitiesDoNotPretendRendererExists() {
        GraphicsCapabilities host = new GraphicsCapabilities(
                "Mali-G57 MC2", "1.3.0", 0x13B5,
                new String[]{"VK_KHR_swapchain", "VK_KHR_synchronization2"});

        GXGraphicsDriverAvailability availability = GXGraphicsDriverAvailability.inspect(host);

        assertTrue(availability.isMaliDeviceDetected());
        assertEquals(GXBackendStatus.UNAVAILABLE, availability.getStatus());
        assertFalse(availability.isSelectable());
        assertTrue(availability.getDetail().contains("game-renderer package"));
    }

    @Test
    public void noMaliRendererDoesNotChangeExistingDriverRegistration() {
        GXBackendRegistry registry = GXBackendRegistry.createDefault();

        assertNotNull(registry.get(GXBackendRegistry.CURRENT));
        assertNotNull(registry.get(GXBackendRegistry.DXVK));
        assertNotNull(registry.get(GXBackendRegistry.VKD3D));
        assertNotNull(registry.get(GXBackendRegistry.WINED3D));
        assertNotNull(registry.get(GXBackendRegistry.MALIFORGE_NATIVE));
        assertEquals(5, registry.size());
    }
}
