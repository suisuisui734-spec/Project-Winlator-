package com.winlator.cmod.maliforge.gx;

import java.util.Arrays;

/**
 * Bounded frame timestamp sampler for future renderer/overlay integration.
 * A real rendering hook must feed monotonic frame timestamps before results represent a game.
 */
public final class GXFramePacingMonitor {
    private static final long NANOS_PER_SECOND = 1_000_000_000L;
    private final long[] timestamps;
    private int nextIndex;
    private int size;
    private long lastTimestamp;
    private boolean hasLastTimestamp;

    public GXFramePacingMonitor() { this(180); }

    public GXFramePacingMonitor(int capacity) {
        if (capacity < 2) throw new IllegalArgumentException("capacity must be at least 2");
        timestamps = new long[capacity];
    }

    public synchronized boolean recordFrame(long timestampNanos) {
        if (timestampNanos < 0) return false;
        if (hasLastTimestamp && timestampNanos <= lastTimestamp) return false;
        timestamps[nextIndex] = timestampNanos;
        nextIndex = (nextIndex + 1) % timestamps.length;
        if (size < timestamps.length) size++;
        lastTimestamp = timestampNanos;
        hasLastTimestamp = true;
        return true;
    }

    public synchronized Snapshot snapshot(int targetFps) {
        if (size < 2) return Snapshot.empty();
        int oldestIndex = (nextIndex - size + timestamps.length) % timestamps.length;
        long[] frameTimes = new long[size - 1];
        int sampleCount = 0;
        long previous = timestamps[oldestIndex];

        for (int i = 1; i < size; i++) {
            long current = timestamps[(oldestIndex + i) % timestamps.length];
            long duration = current - previous;
            if (duration > 0) frameTimes[sampleCount++] = duration;
            previous = current;
        }

        if (sampleCount == 0) return Snapshot.empty();
        long[] samples = Arrays.copyOf(frameTimes, sampleCount);
        Arrays.sort(samples);

        long total = 0;
        int stutterCount = 0;
        long targetFrameNanos = targetFps > 0
                ? NANOS_PER_SECOND / targetFps
                : NANOS_PER_SECOND / 60L;
        long stutterThreshold = targetFrameNanos + targetFrameNanos / 2L;
        for (long frameTime : samples) {
            total += frameTime;
            if (frameTime > stutterThreshold) stutterCount++;
        }

        double averageFrameTimeMs = (total / (double) sampleCount) / 1_000_000.0;
        double averageFps = averageFrameTimeMs > 0 ? 1000.0 / averageFrameTimeMs : 0.0;
        return new Snapshot(sampleCount, averageFps, averageFrameTimeMs,
                percentileMs(samples, 50), percentileMs(samples, 95), percentileMs(samples, 99),
                stutterCount, stutterCount / (double) sampleCount);
    }

    public synchronized void reset() {
        Arrays.fill(timestamps, 0L);
        nextIndex = 0;
        size = 0;
        lastTimestamp = 0L;
        hasLastTimestamp = false;
    }

    private static double percentileMs(long[] sortedSamples, int percentile) {
        int index = (int) Math.ceil((percentile / 100.0) * sortedSamples.length) - 1;
        index = Math.max(0, Math.min(index, sortedSamples.length - 1));
        return sortedSamples[index] / 1_000_000.0;
    }

    public static final class Snapshot {
        public final int sampleCount;
        public final double averageFps;
        public final double averageFrameTimeMs;
        public final double p50FrameTimeMs;
        public final double p95FrameTimeMs;
        public final double p99FrameTimeMs;
        public final int stutterCount;
        public final double stutterRate;

        private Snapshot(int sampleCount, double averageFps, double averageFrameTimeMs,
                         double p50FrameTimeMs, double p95FrameTimeMs, double p99FrameTimeMs,
                         int stutterCount, double stutterRate) {
            this.sampleCount = sampleCount;
            this.averageFps = averageFps;
            this.averageFrameTimeMs = averageFrameTimeMs;
            this.p50FrameTimeMs = p50FrameTimeMs;
            this.p95FrameTimeMs = p95FrameTimeMs;
            this.p99FrameTimeMs = p99FrameTimeMs;
            this.stutterCount = stutterCount;
            this.stutterRate = stutterRate;
        }

        private static Snapshot empty() { return new Snapshot(0, 0, 0, 0, 0, 0, 0, 0); }
        public boolean hasSamples() { return sampleCount > 0; }
    }
}
