package com.winlator.cmod.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ProcessHelperTest {
    @Test
    public void affinityMaskIsNotTruncatedAtEightBits() {
        assertEquals("1ff", ProcessHelper.formatAffinityMask(0x1ff));
    }

    @Test
    public void affinityMaskPreservesHighestIntBit() {
        assertEquals("80000000", ProcessHelper.formatAffinityMask(Integer.MIN_VALUE));
    }
}
