package com.winlator.cmod.maliforge.gx;

import org.junit.Test;
import java.util.Collections;
import static org.junit.Assert.*;

public class GXGameAdvisorTest {
    @Test public void unknownGameAndMissingTelemetryStayLowConfidence() {
        GXGameAdvisor.Request request = new GXGameAdvisor.Request(
                "", false, null, null, null, null, null, 0, 0, 0, 0, 100);
        GXGameAdvisor.Result result = new GXGameAdvisor().advise(request);
        assertEquals(GXGameAdvisor.Confidence.LOW, result.confidence);
        assertFalse(result.gameIdentityVerified);
        assertFalse(result.settingsApplied);
        assertFalse(result.recommendations.isEmpty());
    }

    @Test public void doesNotAttachGameIssuesWithoutVerifiedIdentity() {
        GXCompatibilityAdvisor.Request compatibility = new GXCompatibilityAdvisor.Request(
                "some-game", GXCompatibilityAdvisor.WindowsApi.DIRECT3D_11,
                GXCompatibilityAdvisor.Backend.DXVK, Collections.<String>emptySet());
        GXCompatibilityAdvisor.Environment environment = new GXCompatibilityAdvisor.Environment(
                GXCompatibilityAdvisor.Fact.YES, GXCompatibilityAdvisor.Fact.UNKNOWN,
                GXCompatibilityAdvisor.Fact.YES, GXCompatibilityAdvisor.Initialization.UNKNOWN,
                Collections.<String, GXCompatibilityAdvisor.Fact>emptyMap());
        GXGameAdvisor.Request request = new GXGameAdvisor.Request(
                "some-game", false, compatibility, environment,
                Collections.singletonList(new GXCompatibilityAdvisor.KnownIssue(
                        "some-game", "Unverified issue", "untrusted source")),
                null, null, 1920, 1080, 2400, 1080, 100);
        GXGameAdvisor.Result result = new GXGameAdvisor().advise(request);
        for (GXGameAdvisor.Advice item : result.recommendations) {
            if (item.type == GXGameAdvisor.AdviceType.GAME_DATABASE) {
                assertTrue(item.explanation.contains("not verified")
                        || item.explanation.contains("not assumed"));
            }
        }
        assertFalse(result.settingsApplied);
    }

    @Test public void measuredPerformanceCanProduceOnlyARecommendation() {
        GXPerformanceAdvisor.Telemetry telemetry = new GXPerformanceAdvisor.Telemetry(
                120, 42.0, 29.0, 60, GXPerformanceAdvisor.Workload.GPU_BOUND,
                GXPerformanceAdvisor.ThermalState.NORMAL, 4L * 1024 * 1024 * 1024,
                3L * 1024 * 1024 * 1024, 90, 100);
        GXMemoryIntelligence memory = new GXMemoryIntelligence();
        GXMemoryIntelligence.SystemMemorySnapshot snapshot = memory.analyzeSystemMemory(
                4L * 1024 * 1024 * 1024, 3L * 1024 * 1024 * 1024,
                256L * 1024 * 1024, false, 1L);
        GXGameAdvisor.Request request = new GXGameAdvisor.Request(
                "verified-game", true,
                new GXCompatibilityAdvisor.Request("verified-game",
                        GXCompatibilityAdvisor.WindowsApi.DIRECT3D_11,
                        GXCompatibilityAdvisor.Backend.DXVK, Collections.<String>emptySet()),
                new GXCompatibilityAdvisor.Environment(GXCompatibilityAdvisor.Fact.YES,
                        GXCompatibilityAdvisor.Fact.UNKNOWN, GXCompatibilityAdvisor.Fact.YES,
                        GXCompatibilityAdvisor.Initialization.NOT_INITIALIZED,
                        Collections.<String, GXCompatibilityAdvisor.Fact>emptyMap()),
                null, telemetry, snapshot, 1920, 1080, 2400, 1080, 100);
        GXGameAdvisor.Result result = new GXGameAdvisor().advise(request);
        assertTrue(result.gameIdentityVerified);
        assertFalse(result.settingsApplied);
        assertTrue(result.recommendations.stream().anyMatch(
                a -> a.type == GXGameAdvisor.AdviceType.PERFORMANCE
                        && a.state == GXGameAdvisor.AdviceState.RECOMMENDATION));
        assertTrue(result.recommendations.stream().anyMatch(
                a -> a.type == GXGameAdvisor.AdviceType.RESOLUTION));
    }
}
