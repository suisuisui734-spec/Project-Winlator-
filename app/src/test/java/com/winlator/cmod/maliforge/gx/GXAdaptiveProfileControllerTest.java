package com.winlator.cmod.maliforge.gx;

import org.junit.Test;
import static org.junit.Assert.*;

public class GXAdaptiveProfileControllerTest {
    private GXAdaptiveProfileController.Input input(
            GXGameProfile.Mode mode, GXFramePacingAnalyzer.Status pacing,
            GXPerformanceAdvisor.ThermalState thermal, double pressure,
            boolean backendFailure, boolean safeMode, boolean manual) {
        return new GXAdaptiveProfileController.Input(mode, pacing, thermal, pressure,
                backendFailure, safeMode, manual);
    }

    @Test public void requiresRepeatedObservationsBeforeNormalSuggestion() {
        GXAdaptiveProfileController controller = new GXAdaptiveProfileController(3, 1000);
        GXAdaptiveProfileController.Input poor = input(GXGameProfile.Mode.PERFORMANCE,
                GXFramePacingAnalyzer.Status.DEGRADED, GXPerformanceAdvisor.ThermalState.NORMAL,
                0.4, false, false, false);
        assertEquals(GXAdaptiveProfileController.DecisionState.HOLD_FOR_HYSTERESIS,
                controller.evaluate(poor, 100).state);
        assertEquals(GXAdaptiveProfileController.DecisionState.HOLD_FOR_HYSTERESIS,
                controller.evaluate(poor, 200).state);
        GXAdaptiveProfileController.Decision third = controller.evaluate(poor, 300);
        assertEquals(GXAdaptiveProfileController.DecisionState.SUGGESTED, third.state);
        assertEquals(GXGameProfile.Mode.BALANCED, third.recommendedMode);
    }

    @Test public void appliesCooldownAfterSuggestion() {
        GXAdaptiveProfileController controller = new GXAdaptiveProfileController(1, 5000);
        GXAdaptiveProfileController.Input thermal = input(GXGameProfile.Mode.PERFORMANCE,
                GXFramePacingAnalyzer.Status.STABLE, GXPerformanceAdvisor.ThermalState.CRITICAL,
                0.4, false, false, false);
        assertEquals(GXGameProfile.Mode.ECO, controller.evaluate(thermal, 10).recommendedMode);
        GXAdaptiveProfileController.Input poor = input(GXGameProfile.Mode.PERFORMANCE,
                GXFramePacingAnalyzer.Status.POOR, GXPerformanceAdvisor.ThermalState.NORMAL,
                0.4, false, false, false);
        assertEquals(GXAdaptiveProfileController.DecisionState.HOLD_FOR_COOLDOWN,
                controller.evaluate(poor, 100).state);
    }

    @Test public void emergencyConditionsCanSuggestSafeModeWithoutApplyingIt() {
        GXAdaptiveProfileController controller = new GXAdaptiveProfileController(4, 30_000);
        GXAdaptiveProfileController.Decision d = controller.evaluate(
                input(GXGameProfile.Mode.PERFORMANCE, GXFramePacingAnalyzer.Status.STABLE,
                        GXPerformanceAdvisor.ThermalState.NORMAL, 0.4, true, false, false), 100);
        assertEquals(GXAdaptiveProfileController.DecisionState.SUGGESTED, d.state);
        assertEquals(GXGameProfile.Mode.COMPATIBILITY, d.recommendedMode);
    }

    @Test public void manualOverridePreventsAdaptiveSuggestion() {
        GXAdaptiveProfileController controller = new GXAdaptiveProfileController(1, 0);
        GXAdaptiveProfileController.Decision d = controller.evaluate(
                input(GXGameProfile.Mode.PERFORMANCE, GXFramePacingAnalyzer.Status.POOR,
                        GXPerformanceAdvisor.ThermalState.NORMAL, 0.4, false, false, true), 100);
        assertEquals(GXAdaptiveProfileController.DecisionState.MANUAL_OVERRIDE, d.state);
        assertEquals(GXGameProfile.Mode.PERFORMANCE, d.recommendedMode);
    }

    @Test public void unknownSignalsKeepCurrentProfile() {
        GXAdaptiveProfileController controller = new GXAdaptiveProfileController(1, 0);
        GXAdaptiveProfileController.Decision d = controller.evaluate(
                input(GXGameProfile.Mode.BALANCED, GXFramePacingAnalyzer.Status.UNKNOWN,
                        GXPerformanceAdvisor.ThermalState.UNKNOWN, Double.NaN,
                        false, false, false), 100);
        assertEquals(GXAdaptiveProfileController.DecisionState.NO_CHANGE, d.state);
        assertEquals(GXGameProfile.Mode.BALANCED, d.recommendedMode);
    }

    @Test public void rejectsNonMonotonicControllerTime() {
        GXAdaptiveProfileController controller = new GXAdaptiveProfileController(1, 0);
        GXAdaptiveProfileController.Input poor = input(GXGameProfile.Mode.PERFORMANCE,
                GXFramePacingAnalyzer.Status.POOR, GXPerformanceAdvisor.ThermalState.NORMAL,
                0.4, false, false, false);
        controller.evaluate(poor, 200);
        assertEquals(GXAdaptiveProfileController.DecisionState.INVALID_TIME,
                controller.evaluate(poor, 100).state);
    }
}
