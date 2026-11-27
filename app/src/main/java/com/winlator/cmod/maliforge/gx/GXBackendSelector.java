package com.winlator.cmod.maliforge.gx;

import com.winlator.cmod.graphics.GraphicsCapabilities;

/** Pure selection planner. It never changes Container driver settings or starts a renderer. */
public final class GXBackendSelector {
    public Selection select(GXBackendRegistry registry, GXBackendValidator validator,
                             GXBackendCapabilities capabilities, GraphicsCapabilities rawCapabilities,
                             String requestedBackendId, String fallbackBackendId) {
        if (registry == null || validator == null) {
            return Selection.failed(requestedBackendId, fallbackBackendId,
                    GXBackendFailureReason.BACKEND_NOT_REGISTERED, "Registry or validator is missing.");
        }
        GXBackendDescriptor requested = registry.get(requestedBackendId);
        if (requested == null) {
            return Selection.fallback(requestedBackendId, fallbackBackendId,
                    GXBackendFailureReason.BACKEND_NOT_REGISTERED, "Requested backend is not registered.");
        }
        if (!requested.isSelectable()) {
            return Selection.fallback(requestedBackendId, fallbackBackendId,
                    requested.getKind() == GXBackendDescriptor.Kind.NATIVE_RENDERER
                            ? GXBackendFailureReason.RENDERER_HOOK_UNAVAILABLE
                            : GXBackendFailureReason.BACKEND_PACKAGE_MISSING,
                    "Requested backend is not selectable in this build.");
        }
        if (requested.getKind() == GXBackendDescriptor.Kind.PRESERVE_CURRENT) {
            return Selection.preserved(requestedBackendId);
        }

        GXBackendInitializationResult result = validator.validate(requested, capabilities, rawCapabilities);
        if (result.getStatus() == GXBackendStatus.AVAILABLE) {
            return Selection.preflight(requestedBackendId, result);
        }
        return Selection.fallback(requestedBackendId, fallbackBackendId,
                result.getFailureReason(), result.getDetail());
    }

    public Selection selectAutomatic(GXBackendRegistry registry) {
        if (registry == null || registry.get(GXBackendRegistry.CURRENT) == null) {
            return Selection.failed("auto", GXBackendRegistry.CURRENT,
                    GXBackendFailureReason.FALLBACK_UNAVAILABLE, "No safe current-configuration fallback is registered.");
        }
        return Selection.preserved(GXBackendRegistry.CURRENT);
    }

    public static final class Selection {
        public final String requestedBackendId;
        public final String selectedBackendId;
        public final GXBackendStatus status;
        public final GXBackendFailureReason failureReason;
        public final boolean fallbackRecommended;
        public final boolean fallbackApplied;
        public final boolean initializationVerified;
        public final String detail;

        private Selection(String requested, String selected, GXBackendStatus status,
                          GXBackendFailureReason reason, boolean fallbackRecommended,
                          boolean fallbackApplied, boolean initializationVerified, String detail) {
            requestedBackendId = requested == null ? "Unknown" : requested;
            selectedBackendId = selected == null ? "Unknown" : selected;
            this.status = status;
            failureReason = reason;
            this.fallbackRecommended = fallbackRecommended;
            this.fallbackApplied = fallbackApplied;
            this.initializationVerified = initializationVerified;
            this.detail = detail == null ? "" : detail;
        }

        private static Selection preserved(String id) {
            return new Selection(id, id, GXBackendStatus.AVAILABLE, GXBackendFailureReason.NONE,
                    false, false, false, "Existing per-container Winlator settings are preserved.");
        }

        private static Selection preflight(String id, GXBackendInitializationResult result) {
            return new Selection(id, id, result.getStatus(), result.getFailureReason(),
                    false, false, false, result.getDetail());
        }

        private static Selection fallback(String requested, String fallback,
                                           GXBackendFailureReason reason, String detail) {
            return new Selection(requested, fallback, GXBackendStatus.DEGRADED, reason,
                    true, false, false, detail + " Existing configuration is not modified.");
        }

        private static Selection failed(String requested, String fallback,
                                        GXBackendFailureReason reason, String detail) {
            return new Selection(requested, fallback, GXBackendStatus.UNAVAILABLE, reason,
                    true, false, false, detail);
        }
    }
}
