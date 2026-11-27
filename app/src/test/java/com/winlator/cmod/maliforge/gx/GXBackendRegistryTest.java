package com.winlator.cmod.maliforge.gx;

import org.junit.Test;
import java.util.Collections;
import static org.junit.Assert.*;

public class GXBackendRegistryTest {
    @Test public void defaultRegistryListsNativeBackendAsUnavailableMetadata() {
        GXBackendRegistry registry = GXBackendRegistry.createDefault();
        GXBackendDescriptor nativeBackend = registry.get(GXBackendRegistry.MALIFORGE_NATIVE);
        assertNotNull(nativeBackend);
        assertFalse(nativeBackend.isSelectable());
        assertFalse(nativeBackend.hasRuntimeInitializer());
        assertEquals(GXBackendDescriptor.Kind.NATIVE_RENDERER, nativeBackend.getKind());
        assertEquals(5, registry.size());
        assertEquals(GXBackendStatus.UNKNOWN, registry.getStatus(GXBackendRegistry.MALIFORGE_NATIVE));
    }

    @Test public void duplicateRegistrationIsRejected() {
        GXBackendRegistry registry = new GXBackendRegistry();
        GXBackendDescriptor descriptor = descriptor("test", true, true);
        assertTrue(registry.register(descriptor));
        assertFalse(registry.register(descriptor));
        assertEquals(1, registry.size());
    }

    @Test public void missingBackendReturnsNull() {
        assertNull(GXBackendRegistry.createDefault().get("not-registered"));
    }

    @Test public void registryRecordsStatusOnlyForRegisteredBackend() {
        GXBackendRegistry registry = GXBackendRegistry.createDefault();
        GXBackendInitializationResult missing = new GXBackendValidator().validate(null, null, null);
        assertFalse(registry.recordResult(missing));
        GXBackendInitializationResult current = new GXBackendValidator().validate(
                registry.get(GXBackendRegistry.CURRENT), GXBackendCapabilities.from(null), null);
        assertTrue(registry.recordResult(current));
        assertEquals(GXBackendStatus.AVAILABLE, registry.getStatus(GXBackendRegistry.CURRENT));
    }

    static GXBackendDescriptor descriptor(String id, boolean bundled, boolean initializer) {
        return new GXBackendDescriptor(id, id, "test", GXBackendDescriptor.Kind.NATIVE_RENDERER,
                GXBackendDescriptor.RequiredApi.NONE, 0, 0, Collections.emptySet(),
                false, bundled, initializer, true, true, "test backend");
    }
}
