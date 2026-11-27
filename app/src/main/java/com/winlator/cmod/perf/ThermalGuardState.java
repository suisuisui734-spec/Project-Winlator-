package com.winlator.cmod.perf;

import android.os.PowerManager;

/** Pure thermal hysteresis policy, testable without physical device thermal sensors. */
public final class ThermalGuardState {
    private ThermalGuardState() {}

    /**
     * Pause optional hints at MODERATE or higher. Resume only at LIGHT or lower.
     * Unknown status values preserve the previous state.
     */
    public static boolean update(boolean currentlyThrottled, int thermalStatus) {
        if (thermalStatus < PowerManager.THERMAL_STATUS_NONE) return currentlyThrottled;
        if (thermalStatus >= PowerManager.THERMAL_STATUS_MODERATE) return true;
        if (thermalStatus <= PowerManager.THERMAL_STATUS_LIGHT) return false;
        return currentlyThrottled;
    }
}
