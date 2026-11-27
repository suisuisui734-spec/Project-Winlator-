package com.winlator.cmod.graphics;

import android.content.Context;

import com.winlator.cmod.core.GPUInformation;

/** Read-only system-driver capability probe; it does not install or replace graphics drivers. */
public final class GraphicsCapabilityDetector {
    private GraphicsCapabilityDetector() {}

    public static GraphicsCapabilities detect(Context context) {
        String renderer = "";
        String version = "";
        String[] extensions = new String[0];
        try { renderer = GPUInformation.getRenderer(null, context); } catch (Throwable ignored) {}
        try { version = GPUInformation.getVulkanVersion(null, context); } catch (Throwable ignored) {}
        try {
            String[] result = GPUInformation.enumerateExtensions(null, context);
            if (result != null) extensions = result;
        } catch (Throwable ignored) {}
        return new GraphicsCapabilities(renderer, version, extensions);
    }
}
