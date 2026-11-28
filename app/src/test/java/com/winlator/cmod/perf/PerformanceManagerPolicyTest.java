package com.winlator.cmod.perf;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PerformanceManagerPolicyTest {
    @Test
    public void leanModeSkipsOptionalBoosts() {
        assertTrue(PerformanceManager.shouldSkipOptionalBoosts(true, false));
    }

    @Test
    public void thermalGuardSkipsOptionalBoostsWithoutLeanMode() {
        assertTrue(PerformanceManager.shouldSkipOptionalBoosts(false, true));
    }

    @Test
    public void normalModeKeepsExistingOptionalBoostPolicy() {
        assertFalse(PerformanceManager.shouldSkipOptionalBoosts(false, false));
    }
}
