package com.winlator.cmod.maliforge.gx;

import android.app.ActivityManager;
import android.content.Context;

/** System RAM pressure and optional Vulkan heap-budget reporting; never equates shared RAM with VRAM. */
public final class GXMemoryIntelligence {
    public enum Pressure { UNKNOWN, NORMAL, MODERATE, HIGH, CRITICAL }
    public enum GpuBudgetStatus { UNSUPPORTED, REPORTED, INVALID }

    public SystemMemorySnapshot collectAndroidSystemMemory(Context context) {
        if (context == null) return SystemMemorySnapshot.unknown("Android Context unavailable.");
        try {
            ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (manager == null) return SystemMemorySnapshot.unknown("ActivityManager unavailable.");
            ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
            manager.getMemoryInfo(info);
            return analyzeSystemMemory(info.totalMem, info.availMem, info.threshold,
                    info.lowMemory, System.currentTimeMillis());
        } catch (RuntimeException exception) {
            return SystemMemorySnapshot.unknown("Memory query failed: " + exception.getClass().getSimpleName());
        }
    }

    public SystemMemorySnapshot analyzeSystemMemory(long totalBytes, long availableBytes,
                                                    long lowMemoryThresholdBytes,
                                                    boolean platformLowMemory, long timestampMillis) {
        if (totalBytes <= 0 || availableBytes < 0 || availableBytes > totalBytes
                || lowMemoryThresholdBytes < 0) {
            return SystemMemorySnapshot.unknown("System memory values are missing or inconsistent.");
        }
        double pressure = 1.0 - availableBytes / (double) totalBytes;
        Pressure state;
        if (platformLowMemory || pressure >= 0.95) state = Pressure.CRITICAL;
        else if (pressure >= 0.90) state = Pressure.HIGH;
        else if (pressure >= 0.80) state = Pressure.MODERATE;
        else state = Pressure.NORMAL;
        return new SystemMemorySnapshot(state, totalBytes, availableBytes,
                lowMemoryThresholdBytes, pressure, platformLowMemory,
                timestampMillis, "ANDROID_SYSTEM_RAM");
    }

    /**
     * VK_EXT_memory_budget reports per-heap budget/usage. A device-local heap is not proof
     * of dedicated VRAM; dedicatedVramBytes intentionally remains unknown.
     */
    public GpuMemoryReport analyzeGpuBudget(boolean extensionEnabled, long heapBudgetBytes,
                                            long heapUsageBytes) {
        if (!extensionEnabled) {
            return GpuMemoryReport.unsupported("VK_EXT_memory_budget was not enabled or observed.");
        }
        if (heapBudgetBytes <= 0 || heapUsageBytes < 0) {
            return GpuMemoryReport.invalid("Vulkan heap budget values are unavailable or invalid.");
        }
        double utilization = heapUsageBytes / (double) heapBudgetBytes;
        return new GpuMemoryReport(GpuBudgetStatus.REPORTED, heapBudgetBytes,
                heapUsageBytes, utilization, -1L,
                "Driver-reported Vulkan heap budget; dedicated VRAM is UNKNOWN.");
    }

    public String recommendation(SystemMemorySnapshot snapshot) {
        if (snapshot == null || snapshot.pressure == Pressure.UNKNOWN) {
            return "System memory pressure is unknown; collect valid Android memory information.";
        }
        switch (snapshot.pressure) {
            case CRITICAL:
                return "Android reports critical memory pressure; close unnecessary background apps. Do not clear game data or unknown caches.";
            case HIGH:
                return "System memory is heavily used; avoid opening additional memory-heavy workloads and review GX-owned cache inventory.";
            case MODERATE:
                return "System memory headroom is reduced; monitor pressure during the next workload.";
            case NORMAL:
            default:
                return "No elevated system memory pressure was observed in this sample.";
        }
    }

    public static final class SystemMemorySnapshot {
        public final Pressure pressure;
        public final long totalBytes;
        public final long availableBytes;
        public final long lowMemoryThresholdBytes;
        public final double pressureRatio;
        public final boolean platformLowMemory;
        public final long timestampMillis;
        public final String source;

        private SystemMemorySnapshot(Pressure pressure, long total, long available,
                                     long threshold, double ratio, boolean low,
                                     long timestamp, String source) {
            this.pressure=pressure; totalBytes=total; availableBytes=available;
            lowMemoryThresholdBytes=threshold; pressureRatio=ratio;
            platformLowMemory=low; timestampMillis=timestamp; this.source=source;
        }
        private static SystemMemorySnapshot unknown(String reason) {
            return new SystemMemorySnapshot(Pressure.UNKNOWN,-1,-1,-1,Double.NaN,
                    false,-1,reason);
        }
    }

    public static final class GpuMemoryReport {
        public final GpuBudgetStatus status;
        public final long heapBudgetBytes;
        public final long heapUsageBytes;
        public final double utilization;
        public final long dedicatedVramBytes;
        public final String detail;

        private GpuMemoryReport(GpuBudgetStatus status,long budget,long usage,
                                double utilization,long dedicatedVram,String detail) {
            this.status=status; heapBudgetBytes=budget; heapUsageBytes=usage;
            this.utilization=utilization; dedicatedVramBytes=dedicatedVram; this.detail=detail;
        }
        private static GpuMemoryReport unsupported(String detail) {
            return new GpuMemoryReport(GpuBudgetStatus.UNSUPPORTED,-1,-1,Double.NaN,-1,detail);
        }
        private static GpuMemoryReport invalid(String detail) {
            return new GpuMemoryReport(GpuBudgetStatus.INVALID,-1,-1,Double.NaN,-1,detail);
        }
    }
}
