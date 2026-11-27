package com.winlator.cmod.maliforge.gx;

import org.junit.Test;
import static org.junit.Assert.*;

public class GXFramePacingAnalyzerTest {
    private static final long MS = 1_000_000L;

    @Test public void stableSamplesProduceStableAnalysis() {
        GXFramePacingMonitor monitor = new GXFramePacingMonitor(12);
        for (int i = 0; i < 10; i++) monitor.recordFrame(i * 16_666_667L);
        GXFramePacingMonitor.Snapshot snapshot = monitor.snapshot(60);
        assertEquals(9, snapshot.sampleCount);
        assertTrue(snapshot.frameTimeStdDevMs < 0.01);
        GXFramePacingAnalyzer.Analysis analysis = new GXFramePacingAnalyzer().analyze(snapshot, 60);
        assertEquals(GXFramePacingAnalyzer.Status.STABLE, analysis.status);
        assertFalse(analysis.recommendations.isEmpty());
    }

    @Test public void measuresVarianceAndSpikesWithoutCallingThemFpsGain() {
        GXFramePacingMonitor monitor = new GXFramePacingMonitor(12);
        long[] durations = {16, 16, 16, 38, 16, 16};
        long timestamp = 0;
        monitor.recordFrame(timestamp);
        for (long duration : durations) {
            timestamp += duration * MS;
            monitor.recordFrame(timestamp);
        }
        GXFramePacingMonitor.Snapshot snapshot = monitor.snapshot(60);
        assertEquals(1, snapshot.spikeCount);
        assertTrue(snapshot.frameTimeStdDevMs > 0);
        assertEquals(38.0, snapshot.maxFrameTimeMs, 0.01);
        assertEquals(16.0, snapshot.minFrameTimeMs, 0.01);
    }

    @Test public void detectsLongRenderingStalls() {
        GXFramePacingMonitor monitor = new GXFramePacingMonitor(8);
        monitor.recordFrame(0);
        monitor.recordFrame(16 * MS);
        monitor.recordFrame(32 * MS);
        monitor.recordFrame(332 * MS);
        GXFramePacingMonitor.Snapshot snapshot = monitor.snapshot(60);
        assertEquals(1, snapshot.renderingStallCount);
        GXFramePacingAnalyzer.Analysis analysis = new GXFramePacingAnalyzer().analyze(snapshot, 60);
        assertEquals(GXFramePacingAnalyzer.Status.POOR, analysis.status);
    }

    @Test public void reportsUnknownWhenNoRendererSamplesExist() {
        GXFramePacingAnalyzer.Analysis analysis = new GXFramePacingAnalyzer().analyze(null, 60);
        assertEquals(GXFramePacingAnalyzer.Status.UNKNOWN, analysis.status);
    }

    @Test public void keepsExistingMonitorStutterMetrics() {
        GXFramePacingMonitor monitor = new GXFramePacingMonitor(8);
        monitor.recordFrame(0L);
        monitor.recordFrame(16 * MS);
        monitor.recordFrame(32 * MS);
        monitor.recordFrame(80 * MS);
        GXFramePacingMonitor.Snapshot snapshot = monitor.snapshot(60);
        assertEquals(1, snapshot.stutterCount);
        assertEquals(1, snapshot.spikeCount);
    }
}
