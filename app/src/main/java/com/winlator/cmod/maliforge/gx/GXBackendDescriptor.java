package com.winlator.cmod.maliforge.gx;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** Immutable description of a backend route; it contains no driver-loading side effects. */
public final class GXBackendDescriptor {
    public enum Kind {
        PRESERVE_CURRENT,
        GRAPHICS_DRIVER,
        D3D_TRANSLATION,
        WINE_OPENGL_TRANSLATION,
        NATIVE_RENDERER
    }

    public enum RequiredApi {
        NONE,
        VULKAN,
        OPENGL_ES
    }

    private final String id;
    private final String displayName;
    private final String version;
    private final Kind kind;
    private final RequiredApi requiredApi;
    private final int minimumApiMajor;
    private final int minimumApiMinor;
    private final Set<String> requiredExtensions;
    private final boolean requiresVulkan13OrSynchronization2;
    private final boolean packageBundled;
    private final boolean runtimeInitializerAvailable;
    private final boolean selectable;
    private final boolean fallbackSupported;
    private final String description;

    public GXBackendDescriptor(String id, String displayName, String version, Kind kind,
                               RequiredApi requiredApi, int minimumApiMajor, int minimumApiMinor,
                               Set<String> requiredExtensions,
                               boolean requiresVulkan13OrSynchronization2,
                               boolean packageBundled, boolean runtimeInitializerAvailable,
                               boolean selectable, boolean fallbackSupported, String description) {
        if (id == null || id.trim().isEmpty()) throw new IllegalArgumentException("id is required");
        if (displayName == null || displayName.trim().isEmpty()) throw new IllegalArgumentException("displayName is required");
        if (kind == null || requiredApi == null) throw new IllegalArgumentException("backend kind and API are required");
        if (minimumApiMajor < 0 || minimumApiMinor < 0) throw new IllegalArgumentException("API version must be non-negative");
        this.id = id.trim();
        this.displayName = displayName.trim();
        this.version = version == null || version.trim().isEmpty() ? "Unknown" : version.trim();
        this.kind = kind;
        this.requiredApi = requiredApi;
        this.minimumApiMajor = minimumApiMajor;
        this.minimumApiMinor = minimumApiMinor;
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (requiredExtensions != null) {
            for (String extension : requiredExtensions) {
                if (extension != null && !extension.trim().isEmpty()) {
                    normalized.add(extension.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        this.requiredExtensions = Collections.unmodifiableSet(normalized);
        this.requiresVulkan13OrSynchronization2 = requiresVulkan13OrSynchronization2;
        this.packageBundled = packageBundled;
        this.runtimeInitializerAvailable = runtimeInitializerAvailable;
        this.selectable = selectable;
        this.fallbackSupported = fallbackSupported;
        this.description = description == null ? "" : description;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getVersion() { return version; }
    public Kind getKind() { return kind; }
    public RequiredApi getRequiredApi() { return requiredApi; }
    public int getMinimumApiMajor() { return minimumApiMajor; }
    public int getMinimumApiMinor() { return minimumApiMinor; }
    public Set<String> getRequiredExtensions() { return requiredExtensions; }
    public boolean requiresVulkan13OrSynchronization2() { return requiresVulkan13OrSynchronization2; }
    public boolean isPackageBundled() { return packageBundled; }
    public boolean hasRuntimeInitializer() { return runtimeInitializerAvailable; }
    public boolean isSelectable() { return selectable; }
    public boolean supportsFallback() { return fallbackSupported; }
    public String getDescription() { return description; }
}
