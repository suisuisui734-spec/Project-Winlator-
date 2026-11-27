package com.winlator.cmod.maliforge.gx;

import org.junit.Test;
import static org.junit.Assert.*;

public class GXPerformanceAdvisorTest {
    private GXPerformanceAdvisor.Telemetry telemetry(
            GXPerformanceAdvisor.Workload workload, GXPerformanceAdvisor.ThermalState thermal,
            long totalMemory, long availableMemory, int confidence, int scale,
            double averageFps, double p95) {
        return new GXPerformanceAdvisor.Telemetry(120, averageFps, p95, 60, workload, thermal,
                totalMemory, availableMemory, confidence, scale);
    }

    @Test public void doesNotRecommendWithoutRealSamples() {
        GXPerformanceAdvisor.Advice a = new GXPerformanceAdvisor().advise(
                new GXPerformanceAdvisor.Telemetry(0, 0, 0, 60,
                        GXPerformanceAdvisor.Workload.GPU_BOUND,
                        GXPerformanceAdvisor.ThermalState.NORMAL, 0, 0, 100, 100));
        assertEquals(GXPerformanceAdvisor.Recommendation.COLLECT_MORE_DATA, a.recommendation);
        assertEquals(0, a.suggestedRenderScalePercent);
    }

    @Test public void severeThermalStateSuggestsCooldownNotPerformanceBoost() {
        GXPerformanceAdvisor.Advice a = new GXPerformanceAdvisor().advise(telemetry(
                GXPerformanceAdvisor.Workload.GPU_BOUND, GXPerformanceAdvisor.ThermalState.CRITICAL,
                8_000_000_000L, 4_000_000_000L, 100, 100, 25, 40));
        assertEquals(GXPerformanceAdvisor.Recommendation.ALLOW_COOLDOWN, a.recommendation);
        assertEquals(GXPerformanceAdvisor.ProfileSuggestion.ECO, a.profileSuggestion);
    }

    @Test public void gpuBoundRecommendationRequiresCapabilityConfidence() {
        GXPerformanceAdvisor.Advice unknown = new GXPerformanceAdvisor().advise(telemetry(
                GXPerformanceAdvisor.Workload.GPU_BOUND, GXPerformanceAdvisor.ThermalState.NORMAL,
                8_000_000_000L, 4_000_000_000L, 40, 90, 30, 40));
        assertEquals(GXPerformanceAdvisor.Recommendation.COLLECT_MORE_DATA, unknown.recommendation);

        GXPerformanceAdvisor.Advice known = new GXPerformanceAdvisor().advise(telemetry(
                GXPerformanceAdvisor.Workload.GPU_BOUND, GXPerformanceAdvisor.ThermalState.NORMAL,
                8_000_000_000L, 4_000_000_000L, 90, 90, 30, 40));
        assertEquals(GXPerformanceAdvisor.Recommendation.REDUCE_RENDER_SCALE, known.recommendation);
        assertEquals(85, known.suggestedRenderScalePercent);
    }

    @Test public void cpuBoundDoesNotSuggestResolutionReduction() {
        GXPerformanceAdvisor.Advice a = new GXPerformanceAdvisor().advise(telemetry(
                GXPerformanceAdvisor.Workload.CPU_BOUND, GXPerformanceAdvisor.ThermalState.NORMAL,
                8_000_000_000L, 4_000_000_000L, 90, 100, 30, 40));
        assertEquals(GXPerformanceAdvisor.Recommendation.REDUCE_CPU_WORKLOAD, a.recommendation);
        assertEquals(0, a.suggestedRenderScalePercent);
    }

    @Test public void memoryPressureProducesConservativeAdvice() {
        GXPerformanceAdvisor.Advice a = new GXPerformanceAdvisor().advise(telemetry(
                GXPerformanceAdvisor.Workload.UNKNOWN, GXPerformanceAdvisor.ThermalState.NORMAL,
                8_000_000_000L, 500_000_000L, 0, 100, 30, 40));
        assertEquals(GXPerformanceAdvisor.Recommendation.REDUCE_MEMORY_PRESSURE, a.recommendation);
    }
}
