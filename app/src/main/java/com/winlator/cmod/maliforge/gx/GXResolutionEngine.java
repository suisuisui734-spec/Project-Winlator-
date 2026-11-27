package com.winlator.cmod.maliforge.gx;

/** Per-game render-scale calculations and evidence-gated recommendations; never applies settings. */
public final class GXResolutionEngine {
    public enum Status { UNKNOWN, KEEP_CURRENT, RECOMMEND_SCALE, UNSUPPORTED, INVALID }

    public Resolution scale(int sourceWidth, int sourceHeight, int scalePercent) {
        if (!validDimension(sourceWidth) || !validDimension(sourceHeight)
                || scalePercent < 50 || scalePercent > 100) return null;
        int width = evenFloor(sourceWidth * (long) scalePercent / 100L);
        int height = evenFloor(sourceHeight * (long) scalePercent / 100L);
        if (width < 2 || height < 2) return null;
        return new Resolution(width, height, scalePercent,
                width / (double) height, sourceWidth, sourceHeight);
    }

    /** Validates against the actual display bounds, without assuming the game's aspect ratio. */
    public Validation validate(Resolution resolution, int displayWidth, int displayHeight) {
        if (resolution == null || !validDimension(displayWidth) || !validDimension(displayHeight)) {
            return new Validation(Status.INVALID, "Resolution or display dimensions are invalid.");
        }
        if (resolution.width > displayWidth || resolution.height > displayHeight) {
            return new Validation(Status.UNSUPPORTED, "Requested render size exceeds the supplied display bounds.");
        }
        if (resolution.width < 2 || resolution.height < 2) {
            return new Validation(Status.INVALID, "Render dimensions are below the minimum usable size.");
        }
        return new Validation(Status.KEEP_CURRENT, "Render dimensions are within the supplied display bounds.");
    }

    public Recommendation recommend(int sampleCount, double p95FrameTimeMs, int targetFps,
                                    GXPerformanceAdvisor.Workload workload,
                                    double memoryPressure, int currentScalePercent) {
        if (sampleCount < 30 || !isFinitePositive(p95FrameTimeMs)
                || targetFps < 1 || targetFps > 240
                || currentScalePercent < 50 || currentScalePercent > 100) {
            return new Recommendation(Status.UNKNOWN, currentScalePercent,
                    "Valid frame-time samples, target FPS and current scale are required.");
        }
        double targetFrameTimeMs = 1000.0 / targetFps;
        boolean memoryHigh = memoryPressure >= 0.90 && memoryPressure <= 1.0;
        if (p95FrameTimeMs <= targetFrameTimeMs * 1.25 && !memoryHigh) {
            return new Recommendation(Status.KEEP_CURRENT, currentScalePercent,
                    "Measured p95 frame time does not justify reducing render scale.");
        }
        if (workload == GXPerformanceAdvisor.Workload.CPU_BOUND) {
            return new Recommendation(Status.KEEP_CURRENT, currentScalePercent,
                    "CPU-bound evidence does not justify a resolution reduction.");
        }
        if (workload != GXPerformanceAdvisor.Workload.GPU_BOUND && !memoryHigh) {
            return new Recommendation(Status.UNKNOWN, currentScalePercent,
                    "GPU or memory pressure is not sufficiently established.");
        }
        if (currentScalePercent <= 50) {
            return new Recommendation(Status.KEEP_CURRENT, currentScalePercent,
                    "Render scale is already at the supported conservative lower limit.");
        }
        int reduction = memoryHigh ? 10 : 5;
        int suggested = Math.max(50, currentScalePercent - reduction);
        return new Recommendation(Status.RECOMMEND_SCALE, suggested,
                memoryHigh
                        ? "Measured memory pressure is high; test a lower per-game render scale in a separate profile."
                        : "Measured GPU-bound frame time exceeds the target; test a five-point scale reduction in a separate profile.");
    }

    private static int evenFloor(long value) {
        return (int) (value - (value & 1L));
    }

    private static boolean validDimension(int value) {
        return value >= 2 && value <= 16384;
    }

    private static boolean isFinitePositive(double value) {
        return value > 0 && !Double.isNaN(value) && !Double.isInfinite(value);
    }

    public static final class Resolution {
        public final int width;
        public final int height;
        public final int scalePercent;
        public final double aspectRatio;
        public final int sourceWidth;
        public final int sourceHeight;

        private Resolution(int width, int height, int scalePercent,
                           double aspectRatio, int sourceWidth, int sourceHeight) {
            this.width = width;
            this.height = height;
            this.scalePercent = scalePercent;
            this.aspectRatio = aspectRatio;
            this.sourceWidth = sourceWidth;
            this.sourceHeight = sourceHeight;
        }
    }

    public static final class Validation {
        public final Status status;
        public final String reason;
        private Validation(Status status, String reason) {
            this.status = status;
            this.reason = reason;
        }
    }

    public static final class Recommendation {
        public final Status status;
        public final int suggestedScalePercent;
        public final String reason;
        private Recommendation(Status status, int scale, String reason) {
            this.status = status;
            this.suggestedScalePercent = scale;
            this.reason = reason;
        }
    }
}
