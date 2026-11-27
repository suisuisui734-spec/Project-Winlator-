package com.winlator.cmod.execution;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DependencyDetectorTest {
    @Test
    public void identifiesGraphicsAndInputImportsCaseInsensitively() {
        DependencyDetector.Result result = DependencyDetector.detect(Arrays.asList(
                "D3D11.DLL", "DXGI.DLL", "xinput1_4.dll", "dinput8.dll", "VCRUNTIME140.dll"));
        assertTrue(result.graphicsApis.contains(DependencyDetector.GraphicsApi.DIRECT3D11));
        assertTrue(result.usesXInput);
        assertTrue(result.usesDirectInput);
        assertTrue(result.usesVisualCRuntime);
    }

    @Test
    public void identifiesOpenGlAndVulkanImports() {
        DependencyDetector.Result result = DependencyDetector.detect(Arrays.asList("opengl32.dll", "vulkan-1.dll"));
        assertTrue(result.graphicsApis.contains(DependencyDetector.GraphicsApi.OPENGL));
        assertTrue(result.graphicsApis.contains(DependencyDetector.GraphicsApi.VULKAN));
        assertFalse(result.usesDirectSound);
    }
}
