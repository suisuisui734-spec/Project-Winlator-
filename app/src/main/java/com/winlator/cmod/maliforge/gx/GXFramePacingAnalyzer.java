package com.winlator.cmod.maliforge.gx;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Converts measured frame-time samples into advisory status; it never changes rendering settings. */
public final class GXFramePacingAnalyzer {
    public enum Status { UNKNOWN, STABLE, DEGRADED, POOR }

    public Analysis analyze(GXFramePacingMonitor.Snapshot sample, int targetFps) {
        if (sample == null || !sample.hasSamples()) {
            return new Analysis(Status.UNKNOWN, 0, 0, 0, 0, 0,
                    Collections.singletonList("No renderer frame timestamps are available."));
        }

        double targetFrameMs = targetFps > 0 ? 1000.0 / targetFps : 1000.0 / 60.0;
        double stutterRate = sample.stutterRate;
        double p95Ratio = sample.p95FrameTimeMs / targetFrameMs;
        double deviationRatio = sample.frameTimeStdDevMs / targetFrameMs;
        Status status;
        if (sample.renderingStallCount > 0 || stutterRate >= 0.15
                || sample.averageFrameTimeMs > targetFrameMs * 1.5) {
            status = Status.POOR;
        } else if (sample.spikeCount > 0 || p95Ratio >= 1.25 || deviationRatio >= 0.20) {
            status = Status.DEGRADED;
        } else {
            status = Status.STABLE;
        }

        List<String> recommendations = new ArrayList<>();
        if (sample.renderingStallCount > 0) {
            recommendations.add("Investigate long render stalls and correlate them with shader compilation, storage I/O, and backend errors.");
        }
        if (sample.spikeCount > 0) {
            recommendations.add("Inspect CPU/GPU contention and driver or shader compilation logs around frame-time spikes.");
        }
        if (sample.averageFrameTimeMs > targetFrameMs * 1.10 || p95Ratio > 1.10) {
            recommendations.add("Consider a small, reversible render-scale reduction and compare another measurement session.");
        }
        if (deviationRatio >= 0.20) {
            recommendations.add("Check workload variability, background tasks, and frame queue behavior; avoid changing thermal or clock controls.");
        }
        if (recommendations.isEmpty()) {
            recommendations.add("No significant pacing warning was detected in this sample; retain the current configuration and continue measuring.");
        }
        return new Analysis(status, sample.averageFps, sample.averageFrameTimeMs,
                sample.p95FrameTimeMs, sample.frameTimeStdDevMs,
                sample.stutterRate, recommendations);
    }

    public static final class Analysis {
        public final Status status;
        public final double averageFps;
        public final double averageFrameTimeMs;
        public final double p95FrameTimeMs;
        public final double frameTimeStdDevMs;
        public final double stutterRate;
        public final List<String> recommendations;

        private Analysis(Status status, double averageFps, double averageFrameTimeMs,
                         double p95FrameTimeMs, double frameTimeStdDevMs, double stutterRate,
                         List<String> recommendations) {
            this.status = status;
            this.averageFps = averageFps;
            this.averageFrameTimeMs = averageFrameTimeMs;
            this.p95FrameTimeMs = p95FrameTimeMs;
            this.frameTimeStdDevMs = frameTimeStdDevMs;
            this.stutterRate = stutterRate;
            this.recommendations = Collections.unmodifiableList(new ArrayList<>(recommendations));
        }
    }
}
