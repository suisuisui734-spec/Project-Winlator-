package com.winlator.cmod.perf;

/**
 * Pure policy decisions for optional performance hooks.
 * Kept free of Android framework types so it can be unit-tested on the JVM.
 */
public final class PerformancePolicy {
    private PerformancePolicy() {}

    public static boolean shouldSkipOptionalBoosts(boolean leanMode, boolean thermalThrottled) {
        return leanMode || thermalThrottled;
    }
}
