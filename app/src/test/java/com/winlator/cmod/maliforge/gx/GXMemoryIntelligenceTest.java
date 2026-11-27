package com.winlator.cmod.maliforge.gx;

import org.junit.Test;
import static org.junit.Assert.*;

public class GXMemoryIntelligenceTest {
    @Test public void classifiesAndroidSystemMemoryPressure() {
        GXMemoryIntelligence memory = new GXMemoryIntelligence();
        GXMemoryIntelligence.SystemMemorySnapshot normal =
                memory.analyzeSystemMemory(8_000_000_000L, 3_000_000_000L, 500_000_000L, false, 1);
        assertEquals(GXMemoryIntelligence.Pressure.NORMAL, normal.pressure);
        GXMemoryIntelligence.SystemMemorySnapshot high =
                memory.analyzeSystemMemory(8_000_000_000L, 700_000_000L, 500_000_000L, false, 2);
        assertEquals(GXMemoryIntelligence.Pressure.HIGH, high.pressure);
    }

    @Test public void platformLowMemoryFlagRaisesCriticalState() {
        GXMemoryIntelligence.SystemMemorySnapshot report =
                new GXMemoryIntelligence().analyzeSystemMemory(
                        8_000_000_000L, 4_000_000_000L, 400_000_000L, true, 5);
        assertEquals(GXMemoryIntelligence.Pressure.CRITICAL, report.pressure);
    }

    @Test public void invalidOrUnknownMemoryIsNotClassified() {
        GXMemoryIntelligence memory = new GXMemoryIntelligence();
        assertEquals(GXMemoryIntelligence.Pressure.UNKNOWN,
                memory.analyzeSystemMemory(0, 0, 0, false, 0).pressure);
        assertEquals(GXMemoryIntelligence.Pressure.UNKNOWN,
                memory.analyzeSystemMemory(8, 9, 0, false, 0).pressure);
        assertEquals(GXMemoryIntelligence.Pressure.UNKNOWN,
                memory.analyzeSystemMemory(-1, -1, -1, false, -1).pressure);
    }

    @Test public void doesNotInventDedicatedVramWhenBudgetExtensionIsMissing() {
        GXMemoryIntelligence.GpuMemoryReport report =
                new GXMemoryIntelligence().analyzeGpuBudget(false, 0, 0);
        assertEquals(GXMemoryIntelligence.GpuBudgetStatus.UNSUPPORTED, report.status);
        assertEquals(-1L, report.dedicatedVramBytes);
    }

    @Test public void reportsVulkanHeapBudgetButKeepsDedicatedVramUnknown() {
        GXMemoryIntelligence.GpuMemoryReport report =
                new GXMemoryIntelligence().analyzeGpuBudget(true, 2_000_000_000L, 1_000_000_000L);
        assertEquals(GXMemoryIntelligence.GpuBudgetStatus.REPORTED, report.status);
        assertEquals(0.5, report.utilization, 0.001);
        assertEquals(-1L, report.dedicatedVramBytes);
    }
}
