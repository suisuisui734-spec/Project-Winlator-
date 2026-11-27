package com.winlator.cmod.maliforge.gx;

import org.junit.Test;
import static org.junit.Assert.*;

public class GXPerformanceComparisonTest {
    private GXPerformanceComparison.Session session(
            GXPerformanceComparison.Source source, String device, String workload,
            String backend, String profile, int width, int height, int samples,
            double fps, double p95, int thermal) {
        return new GXPerformanceComparison.Session(source, device, workload, backend, profile,
                width, height, samples, fps, p95, thermal);
    }

    @Test public void comparesCompatibleMeasuredGameSessions() {
        GXPerformanceComparison.Result r = new GXPerformanceComparison().compare(
                session(GXPerformanceComparison.Source.GAME_RENDERER, "helio-device", "game-x",
                        "dxvk", "balanced", 1280, 720, 120, 30, 40, 1),
                session(GXPerformanceComparison.Source.GAME_RENDERER, "helio-device", "game-x",
                        "dxvk", "custom", 1280, 720, 120, 33, 35, 1));
        assertEquals(GXPerformanceComparison.Status.COMPARABLE, r.status);
        assertTrue(r.averageFpsChangePercent > 0);
        assertTrue(r.p95FrameTimeImprovementPercent > 0);
        assertTrue(r.conditionDifferences.contains("Profile changed."));
    }

    @Test public void rejectsSyntheticDataAndInsufficientSamples() {
        assertEquals(GXPerformanceComparison.Status.NOT_COMPARABLE,
                new GXPerformanceComparison().compare(
                        session(GXPerformanceComparison.Source.SYNTHETIC, "d", "t", "a", "p", 1, 1, 100, 1, 1, 0),
                        session(GXPerformanceComparison.Source.SYNTHETIC, "d", "t", "a", "p", 1, 1, 100, 1, 1, 0)).status);
        assertEquals(GXPerformanceComparison.Status.NOT_COMPARABLE,
                new GXPerformanceComparison().compare(
                        session(GXPerformanceComparison.Source.GAME_RENDERER, "d", "t", "a", "p", 1, 1, 10, 1, 1, 0),
                        session(GXPerformanceComparison.Source.GAME_RENDERER, "d", "t", "a", "p", 1, 1, 10, 1, 1, 0)).status);
    }

    @Test public void requiresSameWorkloadAndDevice() {
        assertEquals(GXPerformanceComparison.Status.NOT_COMPARABLE,
                new GXPerformanceComparison().compare(
                        session(GXPerformanceComparison.Source.GAME_RENDERER, "device-a", "game-x", "dxvk", "a", 1, 1, 40, 60, 16, 0),
                        session(GXPerformanceComparison.Source.GAME_RENDERER, "device-b", "game-y", "dxvk", "b", 1, 1, 40, 60, 16, 0)).status);
    }
}
