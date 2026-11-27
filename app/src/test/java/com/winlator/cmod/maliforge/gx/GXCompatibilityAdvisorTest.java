package com.winlator.cmod.maliforge.gx;

import org.junit.Test;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import static org.junit.Assert.*;

public class GXCompatibilityAdvisorTest {
    private GXCompatibilityAdvisor.Environment env(
            GXCompatibilityAdvisor.Fact vulkan, GXCompatibilityAdvisor.Fact opengl,
            GXCompatibilityAdvisor.Fact installed, GXCompatibilityAdvisor.Initialization init,
            java.util.Map<String, GXCompatibilityAdvisor.Fact> extensions) {
        return new GXCompatibilityAdvisor.Environment(vulkan, opengl, installed, init, extensions);
    }

    @Test public void unknownApiOrBackendReturnsUnknown() {
        GXCompatibilityAdvisor.Result r = new GXCompatibilityAdvisor().evaluate(
                new GXCompatibilityAdvisor.Request("game", GXCompatibilityAdvisor.WindowsApi.UNKNOWN,
                        GXCompatibilityAdvisor.Backend.DXVK, null),
                env(GXCompatibilityAdvisor.Fact.YES, GXCompatibilityAdvisor.Fact.YES,
                        GXCompatibilityAdvisor.Fact.YES, GXCompatibilityAdvisor.Initialization.INITIALIZED, null),
                null);
        assertEquals(GXCompatibilityAdvisor.Status.UNKNOWN, r.status);
    }

    @Test public void rejectsBackendApiMismatch() {
        GXCompatibilityAdvisor.Result r = new GXCompatibilityAdvisor().evaluate(
                new GXCompatibilityAdvisor.Request("game", GXCompatibilityAdvisor.WindowsApi.DIRECT3D_12,
                        GXCompatibilityAdvisor.Backend.DXVK, null),
                env(GXCompatibilityAdvisor.Fact.YES, GXCompatibilityAdvisor.Fact.YES,
                        GXCompatibilityAdvisor.Fact.YES, GXCompatibilityAdvisor.Initialization.INITIALIZED, null),
                null);
        assertEquals(GXCompatibilityAdvisor.Status.UNSUPPORTED, r.status);
    }

    @Test public void distinguishesAvailableFromInitialized() {
        GXCompatibilityAdvisor.Result r = new GXCompatibilityAdvisor().evaluate(
                new GXCompatibilityAdvisor.Request("game", GXCompatibilityAdvisor.WindowsApi.DIRECT3D_11,
                        GXCompatibilityAdvisor.Backend.DXVK, null),
                env(GXCompatibilityAdvisor.Fact.YES, GXCompatibilityAdvisor.Fact.UNKNOWN,
                        GXCompatibilityAdvisor.Fact.YES, GXCompatibilityAdvisor.Initialization.NOT_INITIALIZED, null),
                null);
        assertEquals(GXCompatibilityAdvisor.Status.AVAILABLE_NOT_INITIALIZED, r.status);
    }

    @Test public void reportsUnknownRequiredExtensionRatherThanAssumingSupport() {
        HashSet<String> required = new HashSet<>();
        required.add("VK_EXT_descriptor_indexing");
        GXCompatibilityAdvisor.Result r = new GXCompatibilityAdvisor().evaluate(
                new GXCompatibilityAdvisor.Request("game", GXCompatibilityAdvisor.WindowsApi.DIRECT3D_11,
                        GXCompatibilityAdvisor.Backend.DXVK, required),
                env(GXCompatibilityAdvisor.Fact.YES, GXCompatibilityAdvisor.Fact.UNKNOWN,
                        GXCompatibilityAdvisor.Fact.YES, GXCompatibilityAdvisor.Initialization.INITIALIZED,
                        Collections.<String, GXCompatibilityAdvisor.Fact>emptyMap()),
                null);
        assertEquals(GXCompatibilityAdvisor.Status.UNKNOWN, r.status);
    }

    @Test public void rejectsExplicitlyUnsupportedCapability() {
        HashMap<String, GXCompatibilityAdvisor.Fact> extensions = new HashMap<>();
        extensions.put("VK_EXT_descriptor_indexing", GXCompatibilityAdvisor.Fact.NO);
        GXCompatibilityAdvisor.Result r = new GXCompatibilityAdvisor().evaluate(
                new GXCompatibilityAdvisor.Request("game", GXCompatibilityAdvisor.WindowsApi.DIRECT3D_11,
                        GXCompatibilityAdvisor.Backend.DXVK, extensions.keySet()),
                env(GXCompatibilityAdvisor.Fact.YES, GXCompatibilityAdvisor.Fact.UNKNOWN,
                        GXCompatibilityAdvisor.Fact.YES, GXCompatibilityAdvisor.Initialization.INITIALIZED, extensions),
                null);
        assertEquals(GXCompatibilityAdvisor.Status.UNSUPPORTED, r.status);
    }

    @Test public void doesNotClaimGameCompatibilityFromBackendInitializationAlone() {
        GXCompatibilityAdvisor.Result r = new GXCompatibilityAdvisor().evaluate(
                new GXCompatibilityAdvisor.Request("game", GXCompatibilityAdvisor.WindowsApi.DIRECT3D_12,
                        GXCompatibilityAdvisor.Backend.VKD3D, null),
                env(GXCompatibilityAdvisor.Fact.YES, GXCompatibilityAdvisor.Fact.UNKNOWN,
                        GXCompatibilityAdvisor.Fact.YES, GXCompatibilityAdvisor.Initialization.INITIALIZED, null),
                null);
        assertEquals(GXCompatibilityAdvisor.Status.BACKEND_INITIALIZED, r.status);
        assertTrue(r.summary.contains("game compatibility is not yet proven"));
    }

    @Test public void matchesOnlyCallerSuppliedGameIssues() {
        java.util.List<GXCompatibilityAdvisor.KnownIssue> db = Collections.singletonList(
                new GXCompatibilityAdvisor.KnownIssue("game-a", "Known launch issue", "verified issue report"));
        GXCompatibilityAdvisor.Result result = new GXCompatibilityAdvisor().evaluate(
                new GXCompatibilityAdvisor.Request("GAME-A", GXCompatibilityAdvisor.WindowsApi.DIRECT3D_11,
                        GXCompatibilityAdvisor.Backend.DXVK, null),
                env(GXCompatibilityAdvisor.Fact.YES, GXCompatibilityAdvisor.Fact.UNKNOWN,
                        GXCompatibilityAdvisor.Fact.YES, GXCompatibilityAdvisor.Initialization.INITIALIZED, null),
                db);
        assertEquals(1, result.knownIssues.size());
    }
}
