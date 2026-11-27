package com.winlator.cmod.maliforge.gx;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GXFramePacingMonitorTest {
    private static final long MS = 1_000_000L;

    @Test
    public void calculatesStableSixtyFpsSamples() {
        GXFramePacingMonitor monitor = new GXFramePacingMonitor(8);
        for (int i = 0; i < 7; i++) assertTrue(monitor.recordFrame(i * 16_666_667L));

        GXFramePacingMonitor.Snapshot snapshot = monitor.snapshot(60);
        assertEquals(6, snapshot.sampleCount);
        assertEquals(60.0, snapshot.averageFps, 0.2);
        assertEquals(16.67, snapshot.p95FrameTimeMs, 0.1);
        assertEquals(0, snapshot.stutterCount);
    }

    @Test
    public void rejectsDuplicateAndOutOfOrderTimestamps() {
        GXFramePacingMonitor monitor = new GXFramePacingMonitor();
        assertTrue(monitor.recordFrame(10 * MS));
        assertFalse(monitor.recordFrame(10 * MS));
        assertFalse(monitor.recordFrame(9 * MS));
        assertTrue(monitor.recordFrame(26 * MS));
        assertEquals(1, monitor.snapshot(60).sampleCount);
    }

    @Test
    public void detectsFrameTimeSpikesAgainstTarget() {
        GXFramePacingMonitor monitor = new GXFramePacingMonitor(8);
        monitor.recordFrame(0L);
        monitor.recordFrame(16 * MS);
        monitor.recordFrame(32 * MS);
        monitor.recordFrame(80 * MS);
        monitor.recordFrame(96 * MS);

        GXFramePacingMonitor.Snapshot snapshot = monitor.snapshot(60);
        assertEquals(4, snapshot.sampleCount);
        assertEquals(1, snapshot.stutterCount);
        assertTrue(snapshot.stutterRate > 0);
    }

    @Test
    public void boundedBufferKeepsMostRecentFrames() {
        GXFramePacingMonitor monitor = new GXFramePacingMonitor(4);
        for (int i = 0; i < 6; i++) monitor.recordFrame(i * 20 * MS);
        assertEquals(3, monitor.snapshot(50).sampleCount);
        assertEquals(50.0, monitor.snapshot(50).averageFps, 0.1);
    }
}
