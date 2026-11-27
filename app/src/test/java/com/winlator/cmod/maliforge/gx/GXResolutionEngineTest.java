package com.winlator.cmod.maliforge.gx;

import org.junit.Test;
import static org.junit.Assert.*;

public class GXResolutionEngineTest {
    @Test public void scalePreservesSourceAspectRatioAndUsesEvenDimensions() {
        GXResolutionEngine engine = new GXResolutionEngine();
        GXResolutionEngine.Resolution scaled = engine.scale(1920, 1080, 85);
        assertNotNull(scaled);
        assertEquals(1632, scaled.width);
        assertEquals(918, scaled.height);
        assertEquals(16.0 / 9.0, scaled.aspectRatio, 0.002);
    }

    @Test public void rejectsInvalidScaleAndSourceDimensions() {
        GXResolutionEngine engine = new GXResolutionEngine();
        assertNull(engine.scale(1920, 1080, 120));
        assertNull(engine.scale(1, 1080, 80));
        assertNull(engine.scale(1920, 1080, 49));
    }

    @Test public void validatesAgainstActualDisplayBounds() {
        GXResolutionEngine engine = new GXResolutionEngine();
        GXResolutionEngine.Resolution scaled = engine.scale(1920, 1080, 75);
        assertEquals(GXResolutionEngine.Status.KEEP_CURRENT,
                engine.validate(scaled, 2340, 1080).status);
        assertEquals(GXResolutionEngine.Status.UNSUPPORTED,
                engine.validate(engine.scale(3840, 2160, 100), 2340, 1080).status);
    }

    @Test public void onlySuggestsScaleReductionForMeasuredGpuOrMemoryPressure() {
        GXResolutionEngine engine = new GXResolutionEngine();
        assertEquals(GXResolutionEngine.Status.RECOMMEND_SCALE,
                engine.recommend(120, 30, 60, GXPerformanceAdvisor.Workload.GPU_BOUND,
                        0.5, 100).status);
        assertEquals(95, engine.recommend(120, 30, 60,
                GXPerformanceAdvisor.Workload.GPU_BOUND, 0.5, 100).suggestedScalePercent);
        assertEquals(GXResolutionEngine.Status.KEEP_CURRENT,
                engine.recommend(120, 30, 60, GXPerformanceAdvisor.Workload.CPU_BOUND,
                        0.5, 100).status);
    }

    @Test public void unknownOrInsufficientSamplesDoNotProduceRecommendations() {
        GXResolutionEngine engine = new GXResolutionEngine();
        assertEquals(GXResolutionEngine.Status.UNKNOWN,
                engine.recommend(10, 35, 60, GXPerformanceAdvisor.Workload.GPU_BOUND,
                        0.4, 100).status);
        assertEquals(GXResolutionEngine.Status.UNKNOWN,
                engine.recommend(120, 35, 60, GXPerformanceAdvisor.Workload.UNKNOWN,
                        0.4, 100).status);
    }

    @Test public void highMemoryPressureUsesConservativeTenPointSuggestion() {
        GXResolutionEngine.Recommendation r = new GXResolutionEngine().recommend(
                120, 16, 60, GXPerformanceAdvisor.Workload.UNKNOWN, 0.95, 85);
        assertEquals(GXResolutionEngine.Status.RECOMMEND_SCALE, r.status);
        assertEquals(75, r.suggestedScalePercent);
    }
}
