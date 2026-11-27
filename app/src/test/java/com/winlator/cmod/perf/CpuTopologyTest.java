package com.winlator.cmod.perf;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class CpuTopologyTest {
    @Test
    public void buildsMaskForCoresAcrossEightBitBoundary() {
        assertEquals(0x101, CpuTopology.buildCpuMask(Arrays.asList(0, 8)));
    }

    @Test
    public void supportsCoreIndexThirtyOne() {
        assertEquals(Integer.MIN_VALUE, CpuTopology.buildCpuMask(Collections.singletonList(31)));
    }

    @Test
    public void ignoresInvalidAndNullCoreIndices() {
        assertEquals(0x81, CpuTopology.buildCpuMask(Arrays.asList(-1, null, 0, 7, 32, 64)));
    }

    @Test
    public void emptyInputProducesNoAffinityMask() {
        assertEquals(0, CpuTopology.buildCpuMask(Collections.<Integer>emptyList()));
        assertEquals(0, CpuTopology.buildCpuMask(null));
    }
}
