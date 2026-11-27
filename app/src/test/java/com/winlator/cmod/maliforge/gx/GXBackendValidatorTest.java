package com.winlator.cmod.maliforge.gx;

import com.winlator.cmod.graphics.GraphicsCapabilities;
import org.junit.Test;
import java.util.Collections;
import static org.junit.Assert.*;

public class GXBackendValidatorTest {
    private final GXBackendValidator validator = new GXBackendValidator();

    @Test public void dxvkPassesWhenVulkanAndSwapchainAreKnown() {
        GraphicsCapabilities source = new GraphicsCapabilities("Mali-G57", "1.2",
                new String[]{"VK_KHR_swapchain"});
        GXBackendInitializationResult result = validator.validate(
                GXBackendRegistry.createDefault().get(GXBackendRegistry.DXVK),
                GXBackendCapabilities.from(source), source);
        assertEquals(GXBackendStatus.AVAILABLE, result.getStatus());
        assertFalse(result.isReady());
    }

    @Test public void unknownVulkanIsNotTreatedAsSupported() {
        GraphicsCapabilities source = new GraphicsCapabilities("Unknown GPU", "Unavailable", null);
        GXBackendInitializationResult result = validator.validate(
                GXBackendRegistry.createDefault().get(GXBackendRegistry.DXVK),
                GXBackendCapabilities.from(source), source);
        assertEquals(GXBackendStatus.UNKNOWN, result.getStatus());
        assertEquals(GXBackendFailureReason.REQUIRED_API_UNKNOWN, result.getFailureReason());
    }

    @Test public void missingKnownExtensionIsIncompatible() {
        GraphicsCapabilities source = new GraphicsCapabilities("Mali-G57", "1.2",
                new String[]{"VK_KHR_timeline_semaphore"});
        GXBackendInitializationResult result = validator.validate(
                GXBackendRegistry.createDefault().get(GXBackendRegistry.DXVK),
                GXBackendCapabilities.from(source), source);
        assertEquals(GXBackendStatus.INCOMPATIBLE, result.getStatus());
        assertEquals(GXBackendFailureReason.REQUIRED_EXTENSION_MISSING, result.getFailureReason());
    }

    @Test public void emptyExtensionSnapshotRemainsUnknown() {
        GraphicsCapabilities source = new GraphicsCapabilities("Mali-G57", "1.2", new String[0]);
        GXBackendInitializationResult result = validator.validate(
                GXBackendRegistry.createDefault().get(GXBackendRegistry.DXVK),
                GXBackendCapabilities.from(source), source);
        assertEquals(GXBackendStatus.UNKNOWN, result.getStatus());
        assertEquals(GXBackendFailureReason.REQUIRED_EXTENSION_UNKNOWN, result.getFailureReason());
    }

    @Test public void vkd3dRequiresVulkan13OrSynchronization2() {
        GraphicsCapabilities source = new GraphicsCapabilities("Mali-G57", "1.2",
                new String[]{"VK_KHR_swapchain"});
        GXBackendInitializationResult result = validator.validate(
                GXBackendRegistry.createDefault().get(GXBackendRegistry.VKD3D),
                GXBackendCapabilities.from(source), source);
        assertEquals(GXBackendStatus.UNSUPPORTED, result.getStatus());
    }

    @Test public void gameGlesRequirementRemainsUnknownWithoutGameContextProbe() {
        GXBackendDescriptor descriptor = new GXBackendDescriptor("gles-game", "GLES game backend", "1",
                GXBackendDescriptor.Kind.WINE_OPENGL_TRANSLATION,
                GXBackendDescriptor.RequiredApi.OPENGL_ES, 3, 0, Collections.emptySet(),
                false, true, false, true, true, "test");
        GraphicsCapabilities source = new GraphicsCapabilities("Mali-G57", "1.3", new String[0]);
        GXBackendInitializationResult result = validator.validate(
                descriptor, GXBackendCapabilities.from(source), source);
        assertEquals(GXBackendStatus.UNKNOWN, result.getStatus());
        assertEquals(GXBackendFailureReason.REQUIRED_API_UNKNOWN, result.getFailureReason());
    }

    @Test public void nativeBackendIsUnavailableWithoutRendererHookEvenOnVulkan() {
        GraphicsCapabilities source = new GraphicsCapabilities("Mali-G57", "1.3",
                new String[]{"VK_KHR_swapchain", "VK_KHR_synchronization2"});
        GXBackendInitializationResult result = validator.validate(
                GXBackendRegistry.createDefault().get(GXBackendRegistry.MALIFORGE_NATIVE),
                GXBackendCapabilities.from(source), source);
        assertEquals(GXBackendStatus.UNAVAILABLE, result.getStatus());
        assertEquals(GXBackendFailureReason.RENDERER_HOOK_UNAVAILABLE, result.getFailureReason());
    }

    @Test public void missingBackendIsUnavailable() {
        GXBackendInitializationResult result = validator.validate(null, null, null);
        assertEquals(GXBackendStatus.UNAVAILABLE, result.getStatus());
        assertEquals(GXBackendFailureReason.BACKEND_NOT_REGISTERED, result.getFailureReason());
    }
}
