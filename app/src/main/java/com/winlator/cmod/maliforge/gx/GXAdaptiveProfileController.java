package com.winlator.cmod.maliforge.gx;

/**
 * Hysteresis/cooldown controller for adaptive profile suggestions.
 * Decisions are advisory; callers must apply changes explicitly and preserve a rollback profile.
 */
public final class GXAdaptiveProfileController {
    public enum DecisionState { NO_CHANGE, HOLD_FOR_HYSTERESIS, HOLD_FOR_COOLDOWN, SUGGESTED, MANUAL_OVERRIDE, INVALID_TIME }

    private final int requiredStableObservations;
    private final long cooldownMillis;
    private GXGameProfile.Mode pendingMode;
    private int pendingCount;
    private long lastSuggestedAtMillis = -1L;
    private long lastEvaluationAtMillis = -1L;

    public GXAdaptiveProfileController() {
        this(3, 30_000L);
    }

    public GXAdaptiveProfileController(int requiredStableObservations, long cooldownMillis) {
        if (requiredStableObservations < 1) throw new IllegalArgumentException("requiredStableObservations must be positive");
        if (cooldownMillis < 0) throw new IllegalArgumentException("cooldownMillis must not be negative");
        this.requiredStableObservations = requiredStableObservations;
        this.cooldownMillis = cooldownMillis;
    }

    public synchronized Decision evaluate(Input input, long monotonicTimeMillis) {
        if (input == null) return Decision.hold(null, DecisionState.NO_CHANGE, "Input is unavailable.");
        if (monotonicTimeMillis < 0 || (lastEvaluationAtMillis >= 0
                && monotonicTimeMillis < lastEvaluationAtMillis)) {
            resetPending();
            return Decision.hold(input.currentMode, DecisionState.INVALID_TIME,
                    "A valid monotonic clock value is required.");
        }
        lastEvaluationAtMillis = monotonicTimeMillis;

        if (input.manualOverride) {
            resetPending();
            return Decision.hold(input.currentMode, DecisionState.MANUAL_OVERRIDE,
                    "Manual profile selection takes precedence over adaptive suggestions.");
        }

        GXGameProfile.Mode candidate = selectCandidate(input);
        if (candidate == input.currentMode) {
            resetPending();
            return Decision.hold(input.currentMode, DecisionState.NO_CHANGE,
                    "Current profile remains appropriate for the available evidence.");
        }

        boolean emergency = input.safeMode || input.backendFailure
                || input.thermalState == GXPerformanceAdvisor.ThermalState.SEVERE
                || input.thermalState == GXPerformanceAdvisor.ThermalState.CRITICAL
                || (!Double.isNaN(input.memoryPressure) && input.memoryPressure >= 0.95);
        if (emergency) {
            pendingMode = null;
            pendingCount = 0;
            lastSuggestedAtMillis = monotonicTimeMillis;
            return Decision.suggest(candidate, DecisionState.SUGGESTED,
                    "High-priority stability or device-state warning; user confirmation is still required.");
        }

        if (lastSuggestedAtMillis >= 0 && monotonicTimeMillis - lastSuggestedAtMillis < cooldownMillis) {
            return Decision.hold(input.currentMode, DecisionState.HOLD_FOR_COOLDOWN,
                    "Adaptive profile cooldown is active; no profile change was applied.");
        }

        if (pendingMode != candidate) {
            pendingMode = candidate;
            pendingCount = 1;
        } else {
            pendingCount++;
        }

        if (pendingCount < requiredStableObservations) {
            return Decision.hold(input.currentMode, DecisionState.HOLD_FOR_HYSTERESIS,
                    "Candidate profile has not persisted for enough observations.");
        }

        pendingMode = null;
        pendingCount = 0;
        lastSuggestedAtMillis = monotonicTimeMillis;
        return Decision.suggest(candidate, DecisionState.SUGGESTED,
                "The same adaptive candidate persisted across the configured observation window.");
    }

    public synchronized void reset() {
        resetPending();
        lastSuggestedAtMillis = -1L;
        lastEvaluationAtMillis = -1L;
    }

    private GXGameProfile.Mode selectCandidate(Input input) {
        if (input.safeMode || input.backendFailure) return GXGameProfile.Mode.COMPATIBILITY;
        if (input.thermalState == GXPerformanceAdvisor.ThermalState.CRITICAL
                || input.thermalState == GXPerformanceAdvisor.ThermalState.SEVERE
                || (!Double.isNaN(input.memoryPressure) && input.memoryPressure >= 0.95)) {
            return GXGameProfile.Mode.ECO;
        }
        if (input.frameStatus == GXFramePacingAnalyzer.Status.POOR) {
            return GXGameProfile.Mode.COMPATIBILITY;
        }
        if (input.frameStatus == GXFramePacingAnalyzer.Status.DEGRADED
                && input.currentMode == GXGameProfile.Mode.PERFORMANCE) {
            return GXGameProfile.Mode.BALANCED;
        }
        if (input.frameStatus == GXFramePacingAnalyzer.Status.STABLE
                && (input.currentMode == GXGameProfile.Mode.ECO
                || input.currentMode == GXGameProfile.Mode.COMPATIBILITY)
                && input.thermalState == GXPerformanceAdvisor.ThermalState.NORMAL
                && (Double.isNaN(input.memoryPressure) || input.memoryPressure < 0.80)) {
            return GXGameProfile.Mode.BALANCED;
        }
        return input.currentMode;
    }

    private void resetPending() {
        pendingMode = null;
        pendingCount = 0;
    }

    public static final class Input {
        public final GXGameProfile.Mode currentMode;
        public final GXFramePacingAnalyzer.Status frameStatus;
        public final GXPerformanceAdvisor.ThermalState thermalState;
        public final double memoryPressure;
        public final boolean backendFailure;
        public final boolean safeMode;
        public final boolean manualOverride;

        public Input(GXGameProfile.Mode currentMode, GXFramePacingAnalyzer.Status frameStatus,
                     GXPerformanceAdvisor.ThermalState thermalState, double memoryPressure,
                     boolean backendFailure, boolean safeMode, boolean manualOverride) {
            this.currentMode = currentMode == null ? GXGameProfile.Mode.BALANCED : currentMode;
            this.frameStatus = frameStatus == null ? GXFramePacingAnalyzer.Status.UNKNOWN : frameStatus;
            this.thermalState = thermalState == null
                    ? GXPerformanceAdvisor.ThermalState.UNKNOWN : thermalState;
            this.memoryPressure = memoryPressure >= 0 && memoryPressure <= 1
                    ? memoryPressure : Double.NaN;
            this.backendFailure = backendFailure;
            this.safeMode = safeMode;
            this.manualOverride = manualOverride;
        }
    }

    public static final class Decision {
        public final DecisionState state;
        public final GXGameProfile.Mode recommendedMode;
        public final String reason;

        private Decision(DecisionState state, GXGameProfile.Mode mode, String reason) {
            this.state = state;
            this.recommendedMode = mode;
            this.reason = reason;
        }
        private static Decision hold(GXGameProfile.Mode current, DecisionState state, String reason) {
            return new Decision(state, current, reason);
        }
        private static Decision suggest(GXGameProfile.Mode candidate, DecisionState state, String reason) {
            return new Decision(state, candidate, reason);
        }
    }
}
