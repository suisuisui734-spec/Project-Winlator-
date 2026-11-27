package com.winlator.cmod.maliforge.gx;

import org.junit.Test;
import static org.junit.Assert.*;

public class GXGuestVulkanLayerConfigTest {
    @Test public void parsesExplicitOptInValues() {
        assertTrue(GXGuestVulkanLayerConfig.isRequested("1"));
        assertTrue(GXGuestVulkanLayerConfig.isRequested(" TRUE "));
        assertTrue(GXGuestVulkanLayerConfig.isRequested("yes"));
        assertTrue(GXGuestVulkanLayerConfig.isRequested("on"));
        assertFalse(GXGuestVulkanLayerConfig.isRequested("0"));
        assertFalse(GXGuestVulkanLayerConfig.isRequested(null));
    }

    @Test public void disabledByDefault() {
        assertFalse(GXGuestVulkanLayerConfig.prepare(null, "x86_64", true, null, null,
                "/guest/implicit", "/tmp/gx.log").enabled);
    }

    @Test public void explicitDisableOverridesOptIn() {
        GXGuestVulkanLayerConfig.Activation a = GXGuestVulkanLayerConfig.prepare(
                "1", "x86_64", true, null, "1", "/guest/implicit", "/tmp/gx.log");
        assertFalse(a.enabled);
        assertTrue(a.reason.contains("disabled"));
    }

    @Test public void refusesNonX8664Guest() {
        assertFalse(GXGuestVulkanLayerConfig.prepare("1", "arm64", true, null, null,
                "/guest/implicit", "/tmp/gx.log").enabled);
    }

    @Test public void refusesMissingInstallOrPaths() {
        assertFalse(GXGuestVulkanLayerConfig.prepare("1", "x86_64", false, null, null,
                "/dir", "/log").enabled);
        assertFalse(GXGuestVulkanLayerConfig.prepare("1", "x86_64", true, null, null,
                null, "/log").enabled);
        assertFalse(GXGuestVulkanLayerConfig.prepare("1", "x86_64", true, null, null,
                "/dir", null).enabled);
    }

    @Test public void preservesExistingPathsAndAvoidsDuplicates() {
        String separator = java.io.File.pathSeparator;
        String old = "/system/implicit" + separator + "/vendor/implicit";
        GXGuestVulkanLayerConfig.Activation a = GXGuestVulkanLayerConfig.prepare(
                "1", "x86_64", true, old, null, "/guest/implicit", "/guest/gx.log");
        assertTrue(a.enabled);
        assertEquals(old + separator + "/guest/implicit", a.addImplicitLayerPaths);
        GXGuestVulkanLayerConfig.Activation duplicate = GXGuestVulkanLayerConfig.prepare(
                "1", "x86_64", true, a.addImplicitLayerPaths, null,
                "/guest/implicit", "/guest/gx.log");
        assertEquals(a.addImplicitLayerPaths, duplicate.addImplicitLayerPaths);
    }
}
