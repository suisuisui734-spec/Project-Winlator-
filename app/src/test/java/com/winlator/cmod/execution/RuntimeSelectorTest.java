package com.winlator.cmod.execution;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RuntimeSelectorTest {
    @Test
    public void x64UsesConfiguredTranslator() {
        RuntimeSelector.RuntimePlan plan = RuntimeSelector.select(
                PEArchitectureParser.Architecture.X86_64, "Box64", false, false);
        assertTrue(plan.supported);
        assertEquals(RuntimeSelector.RuntimeKind.WINE_X86_64, plan.runtime);
        assertEquals("Box64", plan.emulator);
    }

    @Test
    public void x86RequiresExplicitWow64Capability() {
        RuntimeSelector.RuntimePlan unavailable = RuntimeSelector.select(
                PEArchitectureParser.Architecture.X86, "FEXCore", false, false);
        assertFalse(unavailable.supported);
        assertEquals(RuntimeSelector.RuntimeKind.UNSUPPORTED, unavailable.runtime);
        RuntimeSelector.RuntimePlan available = RuntimeSelector.select(
                PEArchitectureParser.Architecture.X86, "FEXCore", true, false);
        assertTrue(available.supported);
        assertEquals(RuntimeSelector.RuntimeKind.WINE_WOW64, available.runtime);
    }

    @Test
    public void arm64EcNeedsItsOwnRuntimeCapability() {
        RuntimeSelector.RuntimePlan plan = RuntimeSelector.select(
                PEArchitectureParser.Architecture.ARM64EC, "FEXCore", false, false);
        assertFalse(plan.supported);
    }
}
