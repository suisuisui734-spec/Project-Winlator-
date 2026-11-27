package com.winlator.cmod.maliforge.gx;

/** AVAILABLE means preflight passed; READY is reserved for verified initialization. */
public enum GXBackendStatus {
    AVAILABLE,
    UNAVAILABLE,
    UNSUPPORTED,
    INCOMPATIBLE,
    INITIALIZATION_FAILED,
    READY,
    DEGRADED,
    UNKNOWN
}
