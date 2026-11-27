package com.winlator.cmod.maliforge.gx;

import org.junit.Test;
import static org.junit.Assert.*;

public class GXReleaseInfoTest {
    @Test public void releaseMetadataDoesNotClaimRendererOrDeviceValidation() {
        assertEquals("0.1.0-experimental", GXReleaseInfo.VERSION);
        assertEquals("EXPERIMENTAL", GXReleaseInfo.CHANNEL);
        assertEquals(23, GXReleaseInfo.STAGE_COUNT);
        assertTrue(GXReleaseInfo.DEVICE_VALIDATION_REQUIRED);
        assertFalse(GXReleaseInfo.GAME_RENDERER_INCLUDED);
        assertTrue(GXReleaseInfo.getRuntimeReadiness().contains("validation required"));
    }
}
