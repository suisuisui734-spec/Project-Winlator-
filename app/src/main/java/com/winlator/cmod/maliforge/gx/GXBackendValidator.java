package com.winlator.cmod.maliforge.gx;

import com.winlator.cmod.graphics.GraphicsCapabilities;

/** Capability preflight plus an explicit injectable initialization contract for future real backends. */
public final class GXBackendValidator {
    public interface Initializer {
        /** Must initialize and verify the real backend; returning true is an explicit verification result. */
        boolean initialize(GXBackendDescriptor descriptor) throws Exception;
        /** Release partial allocations after a failed initialization attempt. */
        void cleanup();
    }

    public GXBackendInitializationResult validate(GXBackendDescriptor descriptor,
                                                  GXBackendCapabilities capabilities,
                                                  GraphicsCapabilities rawCapabilities) {
        if (descriptor == null) {
            return GXBackendInitializationResult.preflight("Unknown", GXBackendStatus.UNAVAILABLE,
                    GXBackendFailureReason.BACKEND_NOT_REGISTERED, "Backend is not registered.");
        }
        if (descriptor.getKind() == GXBackendDescriptor.Kind.NATIVE_RENDERER
                && (!descriptor.isPackageBundled() || !descriptor.hasRuntimeInitializer())) {
            return GXBackendInitializationResult.preflight(descriptor.getId(), GXBackendStatus.UNAVAILABLE,
                    GXBackendFailureReason.RENDERER_HOOK_UNAVAILABLE,
                    "A real game-renderer package and initialization hook are not available.");
        }
        if (!descriptor.isPackageBundled()) {
            return GXBackendInitializationResult.preflight(descriptor.getId(), GXBackendStatus.UNAVAILABLE,
                    GXBackendFailureReason.BACKEND_PACKAGE_MISSING, "Required backend package is not bundled.");
        }
        if (capabilities == null) {
            return GXBackendInitializationResult.preflight(descriptor.getId(), GXBackendStatus.UNKNOWN,
                    GXBackendFailureReason.CAPABILITY_UNKNOWN, "Capability snapshot is unavailable.");
        }

        switch (descriptor.getRequiredApi()) {
            case VULKAN:
                if (capabilities.getVulkanApiState() == GXCapabilityState.UNKNOWN) {
                    return GXBackendInitializationResult.preflight(descriptor.getId(), GXBackendStatus.UNKNOWN,
                            GXBackendFailureReason.REQUIRED_API_UNKNOWN, "Vulkan API availability is unknown.");
                }
                GXCapabilityState version = capabilities.supportsVulkanVersion(
                        descriptor.getMinimumApiMajor(), descriptor.getMinimumApiMinor());
                if (version == GXCapabilityState.UNKNOWN) {
                    return GXBackendInitializationResult.preflight(descriptor.getId(), GXBackendStatus.UNKNOWN,
                            GXBackendFailureReason.REQUIRED_API_UNKNOWN, "Vulkan version is unknown.");
                }
                if (version == GXCapabilityState.UNSUPPORTED) {
                    return GXBackendInitializationResult.preflight(descriptor.getId(), GXBackendStatus.UNSUPPORTED,
                            GXBackendFailureReason.REQUIRED_API_UNSUPPORTED, "Required Vulkan API version is not supported.");
                }
                for (String extension : descriptor.getRequiredExtensions()) {
                    GXCapabilityState extensionState = capabilities.supportsVulkanExtension(extension, rawCapabilities);
                    if (extensionState == GXCapabilityState.UNKNOWN) {
                        return GXBackendInitializationResult.preflight(descriptor.getId(), GXBackendStatus.UNKNOWN,
                                GXBackendFailureReason.REQUIRED_EXTENSION_UNKNOWN,
                                "Required extension support is unknown: " + extension);
                    }
                    if (extensionState == GXCapabilityState.UNSUPPORTED) {
                        return GXBackendInitializationResult.preflight(descriptor.getId(), GXBackendStatus.INCOMPATIBLE,
                                GXBackendFailureReason.REQUIRED_EXTENSION_MISSING,
                                "Required Vulkan extension is missing: " + extension);
                    }
                }
                if (descriptor.requiresVulkan13OrSynchronization2()) {
                    GXCapabilityState api13 = capabilities.supportsVulkanVersion(1, 3);
                    GXCapabilityState sync2 = capabilities.supportsVulkanExtension(
                            "VK_KHR_synchronization2", rawCapabilities);
                    if (api13 != GXCapabilityState.SUPPORTED && sync2 != GXCapabilityState.SUPPORTED) {
                        if (api13 == GXCapabilityState.UNKNOWN || sync2 == GXCapabilityState.UNKNOWN) {
                            return GXBackendInitializationResult.preflight(descriptor.getId(), GXBackendStatus.UNKNOWN,
                                    GXBackendFailureReason.CAPABILITY_UNKNOWN,
                                    "Cannot verify Vulkan 1.3 or VK_KHR_synchronization2.");
                        }
                        return GXBackendInitializationResult.preflight(descriptor.getId(), GXBackendStatus.UNSUPPORTED,
                                GXBackendFailureReason.REQUIRED_API_UNSUPPORTED,
                                "Vulkan 1.3 or VK_KHR_synchronization2 is required.");
                    }
                }
                break;
            case OPENGL_ES:
                // Stage 2 only observes Android GPU metadata. It does not expose the game's GLES context.
                return GXBackendInitializationResult.preflight(descriptor.getId(), GXBackendStatus.UNKNOWN,
                        GXBackendFailureReason.REQUIRED_API_UNKNOWN,
                        "The game renderer's OpenGL ES context has not been safely probed.");
            case NONE:
            default:
                break;
        }
        return GXBackendInitializationResult.preflight(descriptor.getId(), GXBackendStatus.AVAILABLE,
                GXBackendFailureReason.NONE, "Preflight passed; backend has not been initialized.");
    }

    public GXBackendInitializationResult initialize(GXBackendDescriptor descriptor,
                                                    GXBackendCapabilities capabilities,
                                                    GraphicsCapabilities rawCapabilities,
                                                    Initializer initializer) {
        GXBackendInitializationResult validation = validate(descriptor, capabilities, rawCapabilities);
        if (validation.getStatus() != GXBackendStatus.AVAILABLE) return validation;
        if (descriptor == null || !descriptor.hasRuntimeInitializer() || initializer == null) {
            return GXBackendInitializationResult.preflight(
                    descriptor == null ? "Unknown" : descriptor.getId(),
                    GXBackendStatus.UNAVAILABLE, GXBackendFailureReason.INITIALIZER_NOT_PROVIDED,
                    "Preflight passed, but no runtime initializer was provided.");
        }
        try {
            if (initializer.initialize(descriptor)) {
                return GXBackendInitializationResult.verifiedReady(descriptor.getId(),
                        "Backend initializer returned verified success.");
            }
            boolean cleaned = cleanup(initializer);
            return GXBackendInitializationResult.initializationFailed(descriptor.getId(),
                    cleaned ? GXBackendFailureReason.INITIALIZATION_REJECTED : GXBackendFailureReason.CLEANUP_FAILED,
                    cleaned ? "Backend initializer rejected initialization; partial resources were released."
                            : "Initialization was rejected and cleanup reported a failure.");
        } catch (Exception exception) {
            boolean cleaned = cleanup(initializer);
            String reason = exception.getClass().getSimpleName() + ": "
                    + (exception.getMessage() == null ? "No details" : exception.getMessage());
            return GXBackendInitializationResult.initializationFailed(descriptor.getId(),
                    cleaned ? GXBackendFailureReason.INITIALIZATION_EXCEPTION : GXBackendFailureReason.CLEANUP_FAILED,
                    reason + (cleaned ? "; cleanup completed." : "; cleanup failed."));
        }
    }

    private static boolean cleanup(Initializer initializer) {
        try {
            initializer.cleanup();
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
