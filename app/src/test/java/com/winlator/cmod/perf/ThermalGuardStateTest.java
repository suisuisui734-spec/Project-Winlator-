package com.winlator.cmod.perf;

import android.os.PowerManager;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ThermalGuardStateTest {
    @Test
    public void moderateOrHigherPausesOptionalHints() {
        assertTrue(ThermalGuardState.update(false, PowerManager.THERMAL_STATUS_MODERATE));
        assertTrue(ThermalGuardState.update(false, PowerManager.THERMAL_STATUS_SEVERE));
        assertTrue(ThermalGuardState.update(false, PowerManager.THERMAL_STATUS_CRITICAL));
    }

    @Test
    public void lightOrNoneResumesOptionalHints() {
        assertFalse(ThermalGuardState.update(true, PowerManager.THERMAL_STATUS_LIGHT));
        assertFalse(ThermalGuardState.update(true, PowerManager.THERMAL_STATUS_NONE));
    }

    @Test
    public void warningRangePreservesPreviousState() {
        int warning = PowerManager.THERMAL_STATUS_LIGHT + 1;
        assertTrue(ThermalGuardState.update(true, warning));
        assertFalse(ThermalGuardState.update(false, warning));
    }

    @Test
    public void transitionsDoNotOscillateInsideWarningRange() {
        boolean throttled = false;
        throttled = ThermalGuardState.update(throttled, PowerManager.THERMAL_STATUS_MODERATE);
        throttled = ThermalGuardState.update(throttled, PowerManager.THERMAL_STATUS_LIGHT + 1);
        throttled = ThermalGuardState.update(throttled, PowerManager.THERMAL_STATUS_SEVERE);
        assertTrue(throttled);
        throttled = ThermalGuardState.update(throttled, PowerManager.THERMAL_STATUS_LIGHT);
        assertFalse(throttled);
    }
}
