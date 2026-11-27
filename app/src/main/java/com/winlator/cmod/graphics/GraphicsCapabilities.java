package com.winlator.cmod.graphics;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Immutable snapshot of the Vulkan information exposed by the active Android system driver. */
public final class GraphicsCapabilities {
    private static final Pattern VERSION_PATTERN = Pattern.compile("(\\d+)\\.(\\d+)");
    private final String renderer;
    private final String vulkanVersion;
    private final int major, minor;
    private final Set<String> extensions;

    public GraphicsCapabilities(String renderer, String vulkanVersion, String[] extensions) {
        this.renderer = clean(renderer);
        this.vulkanVersion = clean(vulkanVersion);
        int[] version = parseVersion(this.vulkanVersion);
        major = version[0];
        minor = version[1];
        HashSet<String> normalized = new HashSet<>();
        if (extensions != null) {
            for (String extension : extensions) {
                if (extension != null && !extension.trim().isEmpty()) {
                    normalized.add(extension.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        this.extensions = Collections.unmodifiableSet(normalized);
    }

    public String getRenderer() { return renderer; }
    public String getVulkanVersion() { return vulkanVersion; }
    public boolean hasVulkanVersion() { return major > 0; }
    public int getVulkanMajor() { return major; }
    public int getVulkanMinor() { return minor; }
    public boolean isMali() { return renderer.toLowerCase(Locale.ROOT).contains("mali"); }
    public boolean hasExtension(String name) {
        return name != null && extensions.contains(name.toLowerCase(Locale.ROOT));
    }
    public int getExtensionCount() { return extensions.size(); }

    /** Baseline only: actual DXVK compatibility also depends on many optional extensions and the game. */
    public boolean supportsDxvkBaseline() {
        return atLeast(1, 1) && hasExtension("VK_KHR_swapchain");
    }

    /** Conservative VKD3D baseline; this is not a complete feature/format compatibility check. */
    public boolean supportsVkd3dBaseline() {
        return atLeast(1, 2) && hasExtension("VK_KHR_swapchain")
                && (atLeast(1, 3) || hasExtension("VK_KHR_synchronization2"));
    }

    private boolean atLeast(int reqMajor, int reqMinor) {
        return major > reqMajor || (major == reqMajor && minor >= reqMinor);
    }

    private static int[] parseVersion(String value) {
        Matcher matcher = VERSION_PATTERN.matcher(value);
        if (!matcher.find()) return new int[]{0, 0};
        try {
            return new int[]{Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2))};
        } catch (NumberFormatException ignored) {
            return new int[]{0, 0};
        }
    }

    private static String clean(String value) {
        return value == null || value.trim().isEmpty() ? "Unavailable" : value.trim();
    }
}
