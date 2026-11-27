package com.winlator.cmod.maliforge.gx;

import org.junit.Test;
import static org.junit.Assert.*;

public class GXBackendInitializationTest {
    @Test public void readyRequiresSuccessfulExplicitInitialization() {
        GXBackendDescriptor descriptor = GXBackendRegistryTest.descriptor("test-native", true, true);
        GXBackendValidator.Initializer initializer = new GXBackendValidator.Initializer() {
            @Override public boolean initialize(GXBackendDescriptor backend) { return true; }
            @Override public void cleanup() { fail("cleanup must not run after success"); }
        };
        GXBackendInitializationResult result = new GXBackendValidator().initialize(
                descriptor, GXBackendCapabilities.from(null), null, initializer);
        assertEquals(GXBackendStatus.READY, result.getStatus());
        assertTrue(result.isInitializationVerified());
        assertTrue(result.isReady());
    }

    @Test public void rejectedInitializationCleansPartialResources() {
        final boolean[] cleaned = {false};
        GXBackendValidator.Initializer initializer = new GXBackendValidator.Initializer() {
            @Override public boolean initialize(GXBackendDescriptor backend) { return false; }
            @Override public void cleanup() { cleaned[0] = true; }
        };
        GXBackendInitializationResult result = new GXBackendValidator().initialize(
                GXBackendRegistryTest.descriptor("test-native", true, true),
                GXBackendCapabilities.from(null), null, initializer);
        assertTrue(cleaned[0]);
        assertEquals(GXBackendStatus.INITIALIZATION_FAILED, result.getStatus());
        assertFalse(result.isReady());
    }

    @Test public void exceptionAlsoTriggersCleanup() {
        final boolean[] cleaned = {false};
        GXBackendValidator.Initializer initializer = new GXBackendValidator.Initializer() {
            @Override public boolean initialize(GXBackendDescriptor backend) throws Exception {
                throw new Exception("init failed");
            }
            @Override public void cleanup() { cleaned[0] = true; }
        };
        GXBackendInitializationResult result = new GXBackendValidator().initialize(
                GXBackendRegistryTest.descriptor("test-native", true, true),
                GXBackendCapabilities.from(null), null, initializer);
        assertTrue(cleaned[0]);
        assertEquals(GXBackendFailureReason.INITIALIZATION_EXCEPTION, result.getFailureReason());
    }

    @Test public void missingInitializerNeverReturnsReady() {
        GXBackendInitializationResult result = new GXBackendValidator().initialize(
                GXBackendRegistryTest.descriptor("test-native", true, true),
                GXBackendCapabilities.from(null), null, null);
        assertEquals(GXBackendStatus.UNAVAILABLE, result.getStatus());
        assertFalse(result.isReady());
    }
}
