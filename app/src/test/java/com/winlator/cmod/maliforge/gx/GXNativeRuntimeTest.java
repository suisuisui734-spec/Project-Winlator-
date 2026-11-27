package com.winlator.cmod.maliforge.gx;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GXNativeRuntimeTest {
    @Test public void nativeStatesHaveStableNames() {
        assertEquals("UNINITIALIZED", GXNativeRuntime.stateName(GXNativeRuntime.STATE_UNINITIALIZED));
        assertEquals("READY", GXNativeRuntime.stateName(GXNativeRuntime.STATE_READY));
        assertEquals("FAILED", GXNativeRuntime.stateName(GXNativeRuntime.STATE_FAILED));
        assertEquals("RECOVERING", GXNativeRuntime.stateName(GXNativeRuntime.STATE_RECOVERING));
        assertEquals("UNKNOWN", GXNativeRuntime.stateName(999));
    }
    @Test public void nativeBridgeDoesNotPretendToBeRenderer() {
        GXNativeRuntime.Snapshot snapshot = GXNativeRuntime.getInstance().getSnapshot();
        assertFalse(snapshot.graphicsBackendAvailable);
        GXRuntimeDiagnostics diagnostics = new GXRuntimeDiagnostics(
                snapshot, GXCapabilityReport.from(null), MaliForgeGXEngine.VERSION);
        assertFalse(diagnostics.isReadyForGraphicsSelection());
        assertTrue(diagnostics.toDiagnosticText().contains("no GX ICD/renderer"));
    }
}
