package com.winlator.cmod.maliforge.gx;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Compares measured sessions only when their source and device/workload identity are compatible. */
public final class GXPerformanceComparison {
    public enum Source { GAME_RENDERER, SOFTWARE_GPU_TEST, SYNTHETIC }
    public enum Status { COMPARABLE, NOT_COMPARABLE }

    public Result compare(Session before, Session after) {
        if (before == null || after == null) return Result.invalid("Both baseline and after sessions are required.");
        if (before.source == Source.SYNTHETIC || after.source == Source.SYNTHETIC) {
            return Result.invalid("Synthetic data cannot be presented as measured game or software-GPU performance.");
        }
        if (before.source != after.source) return Result.invalid("Measurement sources differ.");
        if (before.deviceId.isEmpty() || !before.deviceId.equals(after.deviceId)) {
            return Result.invalid("Device identity is missing or differs.");
        }
        if (before.workloadId.isEmpty() || !before.workloadId.equals(after.workloadId)) {
            return Result.invalid("Workload or test identity differs.");
        }
        if (before.sampleCount < 30 || after.sampleCount < 30) {
            return Result.invalid("Each session needs at least 30 measured frame intervals.");
        }
        if (before.averageFps <= 0 || after.averageFps <= 0
                || before.p95FrameTimeMs <= 0 || after.p95FrameTimeMs <= 0) {
            return Result.invalid("FPS and p95 frame time must contain valid measurements.");
        }

        List<String> differences = new ArrayList<>();
        if (!before.backendId.equals(after.backendId)) differences.add("Backend changed.");
        if (before.width != after.width || before.height != after.height) differences.add("Resolution changed.");
        if (before.profileId.equals(after.profileId) == false) differences.add("Profile changed.");
        if (before.thermalBand != after.thermalBand) differences.add("Thermal band differed.");
        double fpsChange = (after.averageFps - before.averageFps) / before.averageFps * 100.0;
        double p95Improvement = (before.p95FrameTimeMs - after.p95FrameTimeMs)
                / before.p95FrameTimeMs * 100.0;
        return new Result(Status.COMPARABLE, fpsChange, p95Improvement, differences, "");
    }

    public static final class Session {
        public final Source source;
        public final String deviceId;
        public final String workloadId;
        public final String backendId;
        public final String profileId;
        public final int width;
        public final int height;
        public final int sampleCount;
        public final double averageFps;
        public final double p95FrameTimeMs;
        public final int thermalBand;

        public Session(Source source, String deviceId, String workloadId, String backendId,
                       String profileId, int width, int height, int sampleCount,
                       double averageFps, double p95FrameTimeMs, int thermalBand) {
            this.source = source;
            this.deviceId = safe(deviceId);
            this.workloadId = safe(workloadId);
            this.backendId = safe(backendId);
            this.profileId = safe(profileId);
            this.width = width;
            this.height = height;
            this.sampleCount = sampleCount;
            this.averageFps = validMetric(averageFps) ? averageFps : 0;
            this.p95FrameTimeMs = validMetric(p95FrameTimeMs) ? p95FrameTimeMs : 0;
            this.thermalBand = thermalBand;
        }

        private static String safe(String value) { return value == null ? "" : value.trim(); }
        private static boolean validMetric(double value) {
            return value > 0 && !Double.isNaN(value) && !Double.isInfinite(value);
        }
    }

    public static final class Result {
        public final Status status;
        public final double averageFpsChangePercent;
        public final double p95FrameTimeImprovementPercent;
        public final List<String> conditionDifferences;
        public final String reason;

        private Result(Status status, double fps, double p95, List<String> differences, String reason) {
            this.status = status;
            this.averageFpsChangePercent = fps;
            this.p95FrameTimeImprovementPercent = p95;
            this.conditionDifferences = Collections.unmodifiableList(new ArrayList<>(differences));
            this.reason = reason;
        }
        private static Result invalid(String reason) {
            return new Result(Status.NOT_COMPARABLE, 0, 0, Collections.<String>emptyList(), reason);
        }
    }
}
