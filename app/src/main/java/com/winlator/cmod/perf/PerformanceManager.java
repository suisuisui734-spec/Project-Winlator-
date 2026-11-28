package com.winlator.cmod.perf;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import androidx.preference.PreferenceManager;

import java.lang.ref.WeakReference;

/**
 * Coordinates optional Android performance hints while keeping thermal protection
 * under Android's control. This class never writes CPU/GPU frequencies or voltages.
 */
public class PerformanceManager {
    private static final String TAG = "PerformanceManager";
    private static final Handler THERMAL_HANDLER = new Handler(Looper.getMainLooper());

    public static final String PREF_GAME_MODE_SIGNAL = "perf_game_mode_signal";
    public static final String PREF_THREAD_PRIORITY_BOOST = "perf_thread_priority_boost";
    public static final String PREF_PREFER_BIG_CORES = "perf_prefer_big_cores";
    public static final String PREF_SUSTAINED_PERFORMANCE = "perf_sustained_performance";
    public static final String PREF_SAMSUNG_PERF_BOOST = "perf_samsung_boost";
    public static final String PREF_THERMAL_GUARD = "perf_thermal_guard";
    public static final String PREF_LEAN_MODE = "perf_lean_mode";

    private static SamsungSPerfDriver samsungDriver = null;
    private static int currentGuestPid = -1;
    private static boolean isSessionActive = false;
    private static boolean thermalThrottled = false;
    private static PowerManager thermalPowerManager;
    private static PowerManager.OnThermalStatusChangedListener thermalStatusListener;

    public static void init(Context context) {
        if (samsungDriver == null && context != null && SamsungSPerfDriver.isSamsungDevice()) {
            samsungDriver = new SamsungSPerfDriver(context);
        }
    }

    public static void onGameStart(Activity activity, int guestRootPid, SharedPreferences prefs) {
        if (activity == null) return;
        currentGuestPid = guestRootPid;
        isSessionActive = true;
        if (prefs == null) prefs = PreferenceManager.getDefaultSharedPreferences(activity);

        configureThermalGuard(activity, prefs);
        if (prefs.getBoolean(PREF_GAME_MODE_SIGNAL, true)) {
            GameModeSignal.enterGameplay(activity);
        }
        applyOptionalBoosts(activity, prefs, true);

        Log.d(TAG, "Performance hints applied (pid=" + guestRootPid
                + ", thermalGuard=" + prefs.getBoolean(PREF_THERMAL_GUARD, true)
                + ", throttled=" + thermalThrottled + ")");
    }

    public static void onGameResume(Activity activity, int guestRootPid) {
        if (activity == null) return;
        if (guestRootPid > 0) currentGuestPid = guestRootPid;
        isSessionActive = true;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);

        configureThermalGuard(activity, prefs);
        if (prefs.getBoolean(PREF_GAME_MODE_SIGNAL, true)) {
            GameModeSignal.enterGameplay(activity);
        }
        applyOptionalBoosts(activity, prefs, true);
    }

    public static void onGamePause(Activity activity) {
        if (activity == null) return;
        isSessionActive = false;
        GameModeSignal.exitGameplay(activity);
        setSustainedPerformance(activity, false);
        PerfPriority.restore();
        if (samsungDriver != null && samsungDriver.isSupported()) samsungDriver.stopBoost();
    }

    public static void onGameStop(Activity activity) {
        if (activity == null) return;
        isSessionActive = false;
        unregisterThermalGuard();
        GameModeSignal.exitGameplay(activity);
        setSustainedPerformance(activity, false);
        PerfPriority.restore();
        if (samsungDriver != null && samsungDriver.isSupported()) samsungDriver.stopBoost();
        currentGuestPid = -1;
        thermalThrottled = false;
        Log.d(TAG, "Performance hints restored on game stop");
    }

    public static void applySettingsLive(Activity activity, SharedPreferences prefs) {
        if (activity == null || !isSessionActive) return;
        if (prefs == null) prefs = PreferenceManager.getDefaultSharedPreferences(activity);

        configureThermalGuard(activity, prefs);
        if (prefs.getBoolean(PREF_GAME_MODE_SIGNAL, true)) {
            GameModeSignal.enterGameplay(activity);
        } else {
            GameModeSignal.exitGameplay(activity);
        }
        applyOptionalBoosts(activity, prefs, false);
    }

    public static void updateGuestPid(Activity activity, int pid) {
        if (pid <= 0) return;
        currentGuestPid = pid;
        if (activity != null && isSessionActive) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
            boolean leanMode = prefs.getBoolean(PREF_LEAN_MODE, false);
            if (!leanMode && !thermalThrottled && prefs.getBoolean(PREF_THREAD_PRIORITY_BOOST, true)) {
                PerfPriority.boost(pid);
            }
            if (!leanMode && !thermalThrottled && prefs.getBoolean(PREF_PREFER_BIG_CORES, false)) {
                CpuTopology.applyBigCoreAffinity(pid);
            }
        }
    }

    public static boolean isSamsungSupported() {
        return samsungDriver != null && samsungDriver.isSupported();
    }

    public static boolean isThermalGuardSupported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q;
    }

    private static void applyOptionalBoosts(Activity activity, SharedPreferences prefs, boolean applyAffinity) {
        boolean leanMode = prefs.getBoolean(PREF_LEAN_MODE, false);
        if (PerformancePolicy.shouldSkipOptionalBoosts(leanMode, thermalThrottled)) {
            // Avoid /proc thread/process walks and all app-controlled optional boost hooks.
            suspendOptionalBoosts(activity);
            return;
        }

        // Defer OEM performance SDK initialization until the corresponding profile is actually active.
        init(activity);

        if (prefs.getBoolean(PREF_THREAD_PRIORITY_BOOST, true) && currentGuestPid > 0) {
            PerfPriority.boost(currentGuestPid);
        } else {
            PerfPriority.restore();
        }

        // Affinity is opt-in. CpuTopology now refuses to infer cluster layout when sysfs is unavailable.
        if (applyAffinity && prefs.getBoolean(PREF_PREFER_BIG_CORES, false) && currentGuestPid > 0) {
            CpuTopology.applyBigCoreAffinity(currentGuestPid);
        }

        setSustainedPerformance(activity, prefs.getBoolean(PREF_SUSTAINED_PERFORMANCE, false));
        if (samsungDriver != null && samsungDriver.isSupported()) {
            if (prefs.getBoolean(PREF_SAMSUNG_PERF_BOOST, true)) {
                samsungDriver.startBoost();
            } else {
                samsungDriver.stopBoost();
            }
        }
    }

    private static void suspendOptionalBoosts(Activity activity) {
        // Android/OEM thermal policy remains authoritative; only Winlator's optional hints stop here.
        PerfPriority.restore();
        setSustainedPerformance(activity, false);
        if (samsungDriver != null && samsungDriver.isSupported()) samsungDriver.stopBoost();
    }

    private static void configureThermalGuard(Activity activity, SharedPreferences prefs) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            thermalThrottled = false;
            unregisterThermalGuard();
            return;
        }

        if (!prefs.getBoolean(PREF_THERMAL_GUARD, true)) {
            thermalThrottled = false;
            unregisterThermalGuard();
            return;
        }

        // Bind the callback to the current Activity and avoid retaining an old screen instance.
        unregisterThermalGuard();
        PowerManager manager = (PowerManager) activity.getSystemService(Context.POWER_SERVICE);
        if (manager == null) {
            thermalThrottled = false;
            return;
        }

        thermalPowerManager = manager;
        WeakReference<Activity> activityReference = new WeakReference<>(activity);
        thermalStatusListener = status -> {
            Activity current = activityReference.get();
            if (current != null) handleThermalStatusChanged(current, status);
        };

        try {
            manager.addThermalStatusListener(command -> THERMAL_HANDLER.post(command), thermalStatusListener);
            updateThermalState(activity, manager.getCurrentThermalStatus());
        } catch (Throwable error) {
            Log.w(TAG, "Thermal status listener unavailable: " + error.getMessage());
            unregisterThermalGuard();
            thermalThrottled = false;
        }
    }

    private static void handleThermalStatusChanged(Activity activity, int status) {
        if (activity != null) updateThermalState(activity, status);
    }

    private static void updateThermalState(Activity activity, int status) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        if (!prefs.getBoolean(PREF_THERMAL_GUARD, true)) {
            thermalThrottled = false;
            return;
        }

        boolean wasThrottled = thermalThrottled;
        thermalThrottled = ThermalGuardState.update(thermalThrottled, status);

        if (wasThrottled != thermalThrottled) {
            Log.i(TAG, "Thermal guard changed: status=" + status
                    + ", optionalBoostsPaused=" + thermalThrottled);
            if (isSessionActive) {
                if (thermalThrottled) suspendOptionalBoosts(activity);
                else applyOptionalBoosts(activity, prefs, false);
            }
        }
    }

    private static void unregisterThermalGuard() {
        if (thermalPowerManager != null && thermalStatusListener != null) {
            try {
                thermalPowerManager.removeThermalStatusListener(thermalStatusListener);
            } catch (Throwable error) {
                Log.w(TAG, "Unable to remove thermal listener: " + error.getMessage());
            }
        }
        thermalStatusListener = null;
        thermalPowerManager = null;
    }

    private static void setSustainedPerformance(Activity activity, boolean enable) {
        if (activity == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return;
        try {
            PowerManager pm = (PowerManager) activity.getSystemService(Context.POWER_SERVICE);
            if (pm != null && pm.isSustainedPerformanceModeSupported()) {
                activity.getWindow().setSustainedPerformanceMode(enable);
                Log.d(TAG, "Sustained performance mode set to " + enable);
            }
        } catch (Throwable error) {
            Log.w(TAG, "Failed to set sustained performance mode: " + error.getMessage());
        }
    }
}
