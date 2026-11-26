package com.winlator.cmod.core;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.os.PowerManager;

import java.util.Locale;

/**
 * Read-only device capability summary for troubleshooting and graphics compatibility.
 * This class does not alter graphics drivers or device performance settings.
 */
public final class DeviceProfileInfo {
    private DeviceProfileInfo() {}

    public static String buildSummary(Context context) {
        StringBuilder summary = new StringBuilder();
        summary.append("Device: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && Build.SOC_MODEL != null
                && !Build.SOC_MODEL.trim().isEmpty()) {
            summary.append("\nSoC: ").append(Build.SOC_MODEL);
        } else {
            summary.append("\nSoC: Not exposed by Android");
        }

        summary.append("\nHardware: ").append(valueOrUnavailable(Build.HARDWARE));
        summary.append("\nSupported ABIs: ").append(joinAbis());
        summary.append("\nARM64: ").append(supportsArm64() ? "supported" : "not reported");

        String renderer = "Not exposed by the system renderer";
        try {
            String detected = GPUInformation.getRenderer(null, context);
            if (detected != null && !detected.trim().isEmpty()
                    && !detected.toLowerCase(Locale.ROOT).contains("unknown")) {
                renderer = detected.trim();
            }
        } catch (Throwable ignored) {
            // Keep this diagnostics page usable when a native/EGL renderer query is unavailable.
        }
        summary.append("\nSystem GPU renderer: ").append(renderer);

        ActivityManager activityManager =
                (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (activityManager != null) {
            ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
            activityManager.getMemoryInfo(memoryInfo);
            if (memoryInfo.totalMem > 0) {
                summary.append(String.format(Locale.US, "\nDevice RAM: %.1f GiB",
                        memoryInfo.totalMem / (1024.0 * 1024.0 * 1024.0)));
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (powerManager != null) {
                try {
                    summary.append("\nThermal API: available (")
                            .append(thermalStatusName(powerManager.getCurrentThermalStatus()))
                            .append(")");
                } catch (Throwable ignored) {
                    summary.append("\nThermal API: available, current status unavailable");
                }
            }
        } else {
            summary.append("\nThermal API: unavailable (Android 10+ required)");
        }

        if (renderer.toLowerCase(Locale.ROOT).contains("mali")) {
            summary.append("\nMali baseline: use the existing Wrapper driver with BCn ASTC or ETC2; do not select Adreno Turnip.");
        } else {
            summary.append("\nGraphics profile: no automatic driver changes applied; keep the current compatible driver.");
        }
        return summary.toString();
    }

    private static String joinAbis() {
        if (Build.SUPPORTED_ABIS == null || Build.SUPPORTED_ABIS.length == 0) return "Unavailable";
        StringBuilder result = new StringBuilder();
        for (String abi : Build.SUPPORTED_ABIS) {
            if (result.length() > 0) result.append(", ");
            result.append(abi);
        }
        return result.toString();
    }

    private static boolean supportsArm64() {
        if (Build.SUPPORTED_ABIS == null) return false;
        for (String abi : Build.SUPPORTED_ABIS) {
            if ("arm64-v8a".equals(abi)) return true;
        }
        return false;
    }

    private static String valueOrUnavailable(String value) {
        return value == null || value.trim().isEmpty() ? "Unavailable" : value;
    }

    private static String thermalStatusName(int status) {
        switch (status) {
            case PowerManager.THERMAL_STATUS_NONE: return "None";
            case PowerManager.THERMAL_STATUS_LIGHT: return "Light";
            case PowerManager.THERMAL_STATUS_MODERATE: return "Moderate";
            case PowerManager.THERMAL_STATUS_SEVERE: return "Severe";
            case PowerManager.THERMAL_STATUS_CRITICAL: return "Critical";
            case PowerManager.THERMAL_STATUS_EMERGENCY: return "Emergency";
            case PowerManager.THERMAL_STATUS_SHUTDOWN: return "Shutdown";
            default: return "Unknown (" + status + ")";
        }
    }
}
