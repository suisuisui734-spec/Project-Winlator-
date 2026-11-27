package com.winlator.cmod.maliforge.gx;

import java.util.Arrays;

/**
 * Bounded frame timestamp sampler.
 * A real renderer hook must feed monotonic game-render timestamps before data represents a game.
 */
public final class GXFramePacingMonitor {
    private static final long NANOS_PER_SECOND = 1_000_000_000L;
    private static final long NANOS_PER_MILLISECOND = 1_000_000L;
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
        int fps = targetFps > 0 ? targetFps : 60;
        long targetFrameNanos = NANOS_PER_SECOND / fps;
        long stutterThreshold = targetFrameNanos + targetFrameNanos / 2L;
        long spikeThreshold = targetFrameNanos * 2L;
        long stallThreshold = Math.max(targetFrameNanos * 5L, 250L * NANOS_PER_MILLISECOND);

        double total = 0.0;
        int stutterCount = 0;
        int spikeCount = 0;
        int stallCount = 0;
        for (long frameTime : samples) {
            total += frameTime;
            if (frameTime > stutterThreshold) stutterCount++;
            if (frameTime >= spikeThreshold) spikeCount++;
            if (frameTime >= stallThreshold) stallCount++;
        }

        double meanNanos = total / sampleCount;
        double varianceNanosSquared = 0.0;
        for (long frameTime : samples) {
            double delta = frameTime - meanNanos;
            varianceNanosSquared += delta * delta;
        }
        varianceNanosSquared /= sampleCount;

        double averageFrameTimeMs = meanNanos / NANOS_PER_MILLISECOND;
        double averageFps = meanNanos > 0.0 ? NANOS_PER_SECOND / meanNanos : 0.0;
        return new Snapshot(sampleCount, averageFps, averageFrameTimeMs,
                percentileMs(samples, 50), percentileMs(samples, 95), percentileMs(samples, 99),
                samples[0] / (double) NANOS_PER_MILLISECOND,
                samples[samples.length - 1] / (double) NANOS_PER_MILLISECOND,
                Math.sqrt(varianceNanosSquared) / NANOS_PER_MILLISECOND,
                varianceNanosSquared / (NANOS_PER_MILLISECOND * (double) NANOS_PER_MILLISECOND),
                stutterCount, stutterCount / (double) sampleCount,
                spikeCount, stallCount, targetFrameNanos / (double) NANOS_PER_MILLISECOND);
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
        return sortedSamples[index] / (double) NANOS_PER_MILLISECOND;
    }

    public static final class Snapshot {
        public final int sampleCount;
        public final double averageFps;
        public final double averageFrameTimeMs;
        public final double p50FrameTimeMs;
        public final double p95FrameTimeMs;
        public final double p99FrameTimeMs;
        public final double minFrameTimeMs;
        public final double maxFrameTimeMs;
        public final double frameTimeStdDevMs;
        public final double frameTimeVarianceMsSquared;
        public final int stutterCount;
        public final double stutterRate;
        public final int spikeCount;
        public final int renderingStallCount;
        public final double targetFrameTimeMs;

        private Snapshot(int sampleCount, double averageFps, double averageFrameTimeMs,
                         double p50FrameTimeMs, double p95FrameTimeMs, double p99FrameTimeMs,
                         double minFrameTimeMs, double maxFrameTimeMs,
                         double frameTimeStdDevMs, double frameTimeVarianceMsSquared,
                         int stutterCount, double stutterRate, int spikeCount,
                         int renderingStallCount, double targetFrameTimeMs) {
            this.sampleCount = sampleCount;
            this.averageFps = averageFps;
            this.averageFrameTimeMs = averageFrameTimeMs;
            this.p50FrameTimeMs = p50FrameTimeMs;
            this.p95FrameTimeMs = p95FrameTimeMs;
            this.p99FrameTimeMs = p99FrameTimeMs;
            this.minFrameTimeMs = minFrameTimeMs;
            this.maxFrameTimeMs = maxFrameTimeMs;
            this.frameTimeStdDevMs = frameTimeStdDevMs;
            this.frameTimeVarianceMsSquared = frameTimeVarianceMsSquared;
            this.stutterCount = stutterCount;
            this.stutterRate = stutterRate;
            this.spikeCount = spikeCount;
            this.renderingStallCount = renderingStallCount;
            this.targetFrameTimeMs = targetFrameTimeMs;
        }

        private static Snapshot empty() {
            return new Snapshot(0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0, 0);
        }

        public boolean hasSamples() { return sampleCount > 0; }
    }
}
