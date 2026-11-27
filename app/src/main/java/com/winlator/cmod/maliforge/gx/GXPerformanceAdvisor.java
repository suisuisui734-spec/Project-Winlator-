package com.winlator.cmod.maliforge.gx;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Evidence-gated recommendations only. This class does not apply profiles, alter driver settings,
 * control clocks, bypass Android thermal policy, or invent missing telemetry.
 */
public final class GXPerformanceAdvisor {
    public enum Workload { CPU_BOUND, GPU_BOUND, MEMORY_BOUND, UNKNOWN }
    public enum ThermalState { UNKNOWN, NORMAL, MODERATE, SEVERE, CRITICAL }
    public enum Recommendation {
        KEEP_CURRENT, COLLECT_MORE_DATA, REDUCE_CPU_WORKLOAD, REDUCE_RENDER_SCALE,
        REDUCE_MEMORY_PRESSURE, ALLOW_COOLDOWN
    }
    public enum ProfileSuggestion { KEEP_CURRENT, ECO, BALANCED, PERFORMANCE, COMPATIBILITY, CUSTOM }

    public Advice advise(Telemetry telemetry) {
        if (telemetry == null || telemetry.sampleCount < 30
                || telemetry.averageFps <= 0 || telemetry.p95FrameTimeMs <= 0) {
            return advice(Recommendation.COLLECT_MORE_DATA, ProfileSuggestion.KEEP_CURRENT, 0,
                    "At least 30 valid frame-time intervals are required for recommendations.",
                    Collections.singletonList("Collect real renderer timestamps; do not infer performance from API capability alone."));
        }

        double memoryPressure = telemetry.memoryPressure();
        if (telemetry.thermalState == ThermalState.CRITICAL
                || telemetry.thermalState == ThermalState.SEVERE) {
            return advice(Recommendation.ALLOW_COOLDOWN, ProfileSuggestion.ECO, 0,
                    "Thermal state is elevated; performance mode changes are not recommended.",
                    Collections.singletonList("Pause or reduce workload and allow the device to cool under Android's normal thermal policy."));
        }
        if (!Double.isNaN(memoryPressure) && memoryPressure >= 0.90) {
            return advice(Recommendation.REDUCE_MEMORY_PRESSURE, ProfileSuggestion.ECO, 0,
                    "Measured available system memory is at or below 10 percent.",
                    Collections.singletonList("Close unnecessary background applications and keep existing game data and driver caches intact."));
        }

        double targetFrameMs = 1000.0 / (telemetry.targetFps > 0 ? telemetry.targetFps : 60);
        boolean belowTarget = telemetry.averageFps < (telemetry.targetFps > 0
                ? telemetry.targetFps * 0.90 : 54.0)
                || telemetry.p95FrameTimeMs > targetFrameMs * 1.50;
        if (!belowTarget) {
            List<String> notes = new ArrayList<>();
            notes.add("Measured frame time is within the configured reference target.");
            if (telemetry.thermalState == ThermalState.MODERATE) {
                notes.add("Thermal state is moderate; avoid raising performance mode without more measurements.");
            }
            return advice(Recommendation.KEEP_CURRENT, ProfileSuggestion.KEEP_CURRENT, 0,
                    "No evidence-based reason to change the current profile was found.", notes);
        }

        switch (telemetry.workload) {
            case CPU_BOUND:
                return advice(Recommendation.REDUCE_CPU_WORKLOAD, ProfileSuggestion.BALANCED, 0,
                        "The supplied workload classifier reports a CPU-bound workload.",
                        Collections.singletonList("Review CPU-heavy game effects or emulation workload options; lowering render resolution is unlikely to address a CPU bottleneck."));
            case GPU_BOUND:
                if (telemetry.gpuCapabilityConfidence < 75) {
                    return advice(Recommendation.COLLECT_MORE_DATA, ProfileSuggestion.KEEP_CURRENT, 0,
                            "GPU-bound telemetry exists, but GPU capability confidence is below 75 percent.",
                            Collections.singletonList("Collect a verified GPU/driver capability report before applying device-specific recommendations."));
                }
                if (telemetry.renderScalePercent <= 50) {
                    return advice(Recommendation.KEEP_CURRENT, ProfileSuggestion.KEEP_CURRENT, 0,
                            "The current render scale is already at the conservative lower bound.",
                            Collections.singletonList("Keep the current profile and inspect backend/runtime diagnostics rather than lowering scale further."));
                }
                return advice(Recommendation.REDUCE_RENDER_SCALE, ProfileSuggestion.CUSTOM,
                        Math.max(50, telemetry.renderScalePercent - 5),
                        "Measured GPU-bound frame times exceed the reference target.",
                        Collections.singletonList("Test a separate profile with render scale reduced by five percentage points; preserve the original profile for rollback."));
            case MEMORY_BOUND:
                return advice(Recommendation.REDUCE_MEMORY_PRESSURE, ProfileSuggestion.ECO, 0,
                        "The supplied workload classifier reports a memory-bound workload.",
                        Collections.singletonList("Reduce optional memory-heavy game settings and review memory telemetry; do not delete unknown cache or user data."));
            case UNKNOWN:
            default:
                return advice(Recommendation.COLLECT_MORE_DATA, ProfileSuggestion.KEEP_CURRENT, 0,
                        "The workload bottleneck is unknown; no targeted optimization is justified.",
                        Collections.singletonList("Collect CPU/GPU workload evidence and frame-time samples before changing configuration."));
        }
    }

    private static Advice advice(Recommendation type, ProfileSuggestion profile, int scale,
                                 String reason, List<String> notes) {
        return new Advice(type, profile, scale, reason, notes);
    }

    public static final class Telemetry {
        public final int sampleCount;
        public final double averageFps;
        public final double p95FrameTimeMs;
        public final int targetFps;
        public final Workload workload;
        public final ThermalState thermalState;
        public final long totalMemoryBytes;
        public final long availableMemoryBytes;
        public final int gpuCapabilityConfidence;
        public final int renderScalePercent;

        public Telemetry(int sampleCount, double averageFps, double p95FrameTimeMs, int targetFps,
                         Workload workload, ThermalState thermalState, long totalMemoryBytes,
                         long availableMemoryBytes, int gpuCapabilityConfidence, int renderScalePercent) {
            this.sampleCount = Math.max(0, sampleCount);
            this.averageFps = isFinitePositive(averageFps) ? averageFps : 0;
            this.p95FrameTimeMs = isFinitePositive(p95FrameTimeMs) ? p95FrameTimeMs : 0;
            this.targetFps = targetFps >= 0 && targetFps <= 240 ? targetFps : 0;
            this.workload = workload == null ? Workload.UNKNOWN : workload;
            this.thermalState = thermalState == null ? ThermalState.UNKNOWN : thermalState;
            boolean memoryValid = totalMemoryBytes > 0 && availableMemoryBytes >= 0
                    && availableMemoryBytes <= totalMemoryBytes;
            this.totalMemoryBytes = memoryValid ? totalMemoryBytes : -1;
            this.availableMemoryBytes = memoryValid ? availableMemoryBytes : -1;
            this.gpuCapabilityConfidence = Math.max(0, Math.min(100, gpuCapabilityConfidence));
            this.renderScalePercent = renderScalePercent >= 50 && renderScalePercent <= 100
                    ? renderScalePercent : 100;
        }

        public double memoryPressure() {
            if (totalMemoryBytes <= 0 || availableMemoryBytes < 0) return Double.NaN;
            return 1.0 - availableMemoryBytes / (double) totalMemoryBytes;
        }

        private static boolean isFinitePositive(double value) {
            return value > 0 && !Double.isNaN(value) && !Double.isInfinite(value);
        }
    }

    public static final class Advice {
        public final Recommendation recommendation;
        public final ProfileSuggestion profileSuggestion;
        public final int suggestedRenderScalePercent;
        public final String reason;
        public final List<String> notes;

        private Advice(Recommendation recommendation, ProfileSuggestion profileSuggestion,
                       int scale, String reason, List<String> notes) {
            this.recommendation = recommendation;
            this.profileSuggestion = profileSuggestion;
            this.suggestedRenderScalePercent = scale;
            this.reason = reason;
            this.notes = Collections.unmodifiableList(new ArrayList<>(notes));
        }
    }
}
