package com.winlator.cmod.maliforge.gx;

import com.winlator.cmod.graphics.GraphicsCapabilities;

/**
 * Single source of truth for the MaliForge GX Graphics Driver UI entry.
 *
 * A device capability probe alone can never make the renderer selectable. The package must
 * be bundled, the runtime initializer must exist, and initialization must be verified.
 */
public final class GXGraphicsDriverAvailability {
    public static final String DRIVER_ID = GXBackendRegistry.MALIFORGE_NATIVE;
    private static final String DISPLAY_NAME = "MaliForge GX";

    private final GXBackendDescriptor descriptor;
    private final GXBackendInitializationResult result;
    private final boolean maliDeviceDetected;
    private final boolean containerSupportsDriver;

    private GXGraphicsDriverAvailability(GXBackendDescriptor descriptor,
                                         GXBackendInitializationResult result,
                                         boolean maliDeviceDetected,
                                         boolean containerSupportsDriver) {
        this.descriptor = descriptor;
        this.result = result;
        this.maliDeviceDetected = maliDeviceDetected;
        this.containerSupportsDriver = containerSupportsDriver;
    }

    public static GXGraphicsDriverAvailability inspect(GraphicsCapabilities capabilities) {
        return inspect(capabilities, false);
    }

    /**
     * containerSupportsDriver must reflect the host application's real persistence allow-list.
     * Keeping it explicit avoids loading Android-backed Container static fields in JVM unit tests.
     */
    public static GXGraphicsDriverAvailability inspect(GraphicsCapabilities capabilities,
                                                        boolean containerSupportsDriver) {
        GXBackendRegistry registry = GXBackendRegistry.createDefault();
        GXBackendDescriptor descriptor = registry.get(DRIVER_ID);
        GXBackendCapabilities backendCapabilities = GXBackendCapabilities.from(capabilities);
        GXBackendInitializationResult result = new GXBackendValidator().validate(
                descriptor, backendCapabilities, capabilities);
        boolean maliDetected = capabilities != null && capabilities.isMali();
        return new GXGraphicsDriverAvailability(descriptor, result, maliDetected, containerSupportsDriver);
    }

    public String getDriverId() {
        return descriptor == null ? DRIVER_ID : descriptor.getId();
    }

    public String getDisplayName() {
        return DISPLAY_NAME;
    }

    public String getDropdownLabel() {
        return DISPLAY_NAME + " (" + getStatusLabel() + ")";
    }

    public String getVersion() {
        return descriptor == null ? "Unknown" : descriptor.getVersion();
    }

    public GXBackendStatus getStatus() {
        return result == null ? GXBackendStatus.UNKNOWN : result.getStatus();
    }

    public String getStatusLabel() {
        switch (getStatus()) {
            case READY: return "Ready";
            case AVAILABLE: return "Available — not initialized";
            case UNSUPPORTED: return "Unsupported";
            case INCOMPATIBLE: return "Incompatible";
            case INITIALIZATION_FAILED: return "Initialization failed";
            case DEGRADED: return "Degraded";
            case UNKNOWN: return "Unknown";
            case UNAVAILABLE:
            default: return "Unavailable";
        }
    }

    public boolean isSelectable() {
        return descriptor != null && descriptor.isSelectable()
                && result != null && result.isReady() && containerSupportsDriver;
    }

    public boolean isPackageBundled() {
        return descriptor != null && descriptor.isPackageBundled();
    }

    public boolean hasRuntimeInitializer() {
        return descriptor != null && descriptor.hasRuntimeInitializer();
    }

    public boolean isInitializationVerified() {
        return result != null && result.isInitializationVerified();
    }

    public boolean isMaliDeviceDetected() {
        return maliDeviceDetected;
    }

    public String getDetail() {
        String version = "Version " + getVersion() + ". ";
        String reason = result == null ? "Backend status could not be determined." : result.getDetail();
        return version + reason;
    }
}
