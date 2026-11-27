package com.winlator.cmod.maliforge.gx;

/** Immutable result. Only verifiedReady() can create a READY result. */
public final class GXBackendInitializationResult {
    private final String backendId;
    private final GXBackendStatus status;
    private final GXBackendFailureReason failureReason;
    private final String detail;
    private final boolean initializationVerified;

    private GXBackendInitializationResult(String backendId, GXBackendStatus status,
                                           GXBackendFailureReason failureReason,
                                           String detail, boolean initializationVerified) {
        this.backendId = backendId == null ? "Unknown" : backendId;
        this.status = status;
        this.failureReason = failureReason;
        this.detail = detail == null ? "" : detail;
        this.initializationVerified = initializationVerified;
    }

    static GXBackendInitializationResult preflight(String backendId, GXBackendStatus status,
                                                    GXBackendFailureReason reason, String detail) {
        if (status == GXBackendStatus.READY) throw new IllegalArgumentException("preflight cannot create READY");
        return new GXBackendInitializationResult(backendId, status, reason, detail, false);
    }

    static GXBackendInitializationResult verifiedReady(String backendId, String detail) {
        return new GXBackendInitializationResult(backendId, GXBackendStatus.READY,
                GXBackendFailureReason.NONE, detail, true);
    }

    static GXBackendInitializationResult initializationFailed(String backendId,
                                                                GXBackendFailureReason reason,
                                                                String detail) {
        return new GXBackendInitializationResult(backendId, GXBackendStatus.INITIALIZATION_FAILED,
                reason, detail, false);
    }

    public String getBackendId() { return backendId; }
    public GXBackendStatus getStatus() { return status; }
    public GXBackendFailureReason getFailureReason() { return failureReason; }
    public String getDetail() { return detail; }
    public boolean isInitializationVerified() { return initializationVerified; }
    public boolean isReady() { return status == GXBackendStatus.READY && initializationVerified; }
}
