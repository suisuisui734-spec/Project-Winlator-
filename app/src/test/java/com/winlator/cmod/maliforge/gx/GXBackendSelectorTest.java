package com.winlator.cmod.maliforge.gx;

import com.winlator.cmod.graphics.GraphicsCapabilities;
import org.junit.Test;
import static org.junit.Assert.*;

public class GXBackendSelectorTest {
    @Test public void manualCurrentSelectionPreservesWinlatorConfiguration() {
        GXBackendSelector.Selection result = new GXBackendSelector().select(
                GXBackendRegistry.createDefault(), new GXBackendValidator(),
                GXBackendCapabilities.from(null), null,
                GXBackendRegistry.CURRENT, GXBackendRegistry.CURRENT);
        assertEquals(GXBackendRegistry.CURRENT, result.selectedBackendId);
        assertEquals(GXBackendStatus.AVAILABLE, result.status);
        assertFalse(result.fallbackApplied);
    }

    @Test public void unavailableNativeBackendOnlyRecommendsCurrentFallback() {
        GXBackendSelector.Selection result = new GXBackendSelector().select(
                GXBackendRegistry.createDefault(), new GXBackendValidator(),
                GXBackendCapabilities.from(null), null,
                GXBackendRegistry.MALIFORGE_NATIVE, GXBackendRegistry.CURRENT);
        assertEquals(GXBackendRegistry.CURRENT, result.selectedBackendId);
        assertEquals(GXBackendStatus.DEGRADED, result.status);
        assertTrue(result.fallbackRecommended);
        assertFalse(result.fallbackApplied);
        assertFalse(result.initializationVerified);
    }

    @Test public void availablePreflightDoesNotMarkExistingDxvkReady() {
        GraphicsCapabilities source = new GraphicsCapabilities("Mali-G57", "1.2",
                new String[]{"VK_KHR_swapchain"});
        GXBackendSelector.Selection result = new GXBackendSelector().select(
                GXBackendRegistry.createDefault(), new GXBackendValidator(),
                GXBackendCapabilities.from(source), source,
                GXBackendRegistry.DXVK, GXBackendRegistry.CURRENT);
        assertEquals(GXBackendStatus.AVAILABLE, result.status);
        assertFalse(result.initializationVerified);
    }

    @Test public void automaticSelectionKeepsCurrentBackend() {
        GXBackendSelector.Selection result = new GXBackendSelector().selectAutomatic(
                GXBackendRegistry.createDefault());
        assertEquals(GXBackendRegistry.CURRENT, result.selectedBackendId);
        assertFalse(result.fallbackApplied);
    }
}
