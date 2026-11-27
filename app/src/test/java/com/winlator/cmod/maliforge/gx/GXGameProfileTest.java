package com.winlator.cmod.maliforge.gx;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class GXGameProfileTest {
    @Test
    public void presetsUseConservativeSupportedRanges() {
        GXGameProfile eco = GXGameProfile.forMode(GXGameProfile.Mode.ECO);
        assertEquals(30, eco.getTargetFps());
        assertEquals(75, eco.getRenderScalePercent());
        assertTrue(eco.isSafeMode());
        assertTrue(eco.isThermalAware());

        GXGameProfile compatibility = GXGameProfile.forMode(GXGameProfile.Mode.COMPATIBILITY);
        assertEquals(GXGameProfile.BackendPreference.KEEP_CURRENT, compatibility.getBackendPreference());
        assertTrue(compatibility.isSafeMode());
    }

    @Test
    public void storageRoundTripPreservesValues() {
        GXGameProfile expected = new GXGameProfile(
                GXGameProfile.Mode.CUSTOM, GXGameProfile.BackendPreference.KEEP_CURRENT,
                45, 92, false, true, true);
        GXGameProfile actual = GXGameProfile.fromStorageValue(expected.toStorageValue());

        assertNotNull(actual);
        assertEquals(expected.getMode(), actual.getMode());
        assertEquals(expected.getBackendPreference(), actual.getBackendPreference());
        assertEquals(expected.getTargetFps(), actual.getTargetFps());
        assertEquals(expected.getRenderScalePercent(), actual.getRenderScalePercent());
        assertFalse(actual.isShaderCacheEnabled());
        assertTrue(actual.isThermalAware());
    }

    @Test
    public void invalidStorageReturnsNull() {
        assertNull(GXGameProfile.fromStorageValue("not-a-profile"));
        assertNull(GXGameProfile.fromStorageValue("1|UNKNOWN|AUTO|60|100|1|1|1"));
        assertNull(GXGameProfile.fromStorageValue("1|BALANCED|AUTO|999|100|1|1|1"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidRenderScaleIsRejected() {
        new GXGameProfile(GXGameProfile.Mode.CUSTOM,
                GXGameProfile.BackendPreference.AUTO, 60, 120, true, true, true);
    }
}
