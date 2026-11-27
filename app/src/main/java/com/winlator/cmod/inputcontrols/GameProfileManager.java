package com.winlator.cmod.inputcontrols;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;
import com.winlator.cmod.container.Shortcut;
import java.util.Locale;

/** Stores a controller profile choice by shortcut identity without rewriting user .icp files. */
public final class GameProfileManager {
    private static final String KEY_PREFIX = "game_controls_profile_";
    private GameProfileManager() {}

    public static String preferenceKeyForIdentity(String identity) {
        if (identity == null || identity.trim().isEmpty()) return null;
        String normalized = identity.trim().replace('\\', '/').toLowerCase(Locale.ROOT);
        while (normalized.contains("//")) normalized = normalized.replace("//", "/");
        return KEY_PREFIX + normalized.replaceAll("[^a-z0-9._/-]", "_");
    }

    public static ControlsProfile resolve(Context context, InputControlsManager manager, Shortcut shortcut) {
        if (context == null || manager == null || shortcut == null) return null;
        String identity = !isEmpty(shortcut.path) ? shortcut.path : shortcut.getExecutable();
        String key = preferenceKeyForIdentity(identity);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if (key != null && prefs.contains(key)) {
            int id = prefs.getInt(key, 0);
            return id > 0 ? manager.getProfile(id) : null;
        }
        String explicit = shortcut.getExtra("controlsProfile");
        if (!explicit.isEmpty()) {
            try { return manager.getProfile(Integer.parseInt(explicit)); }
            catch (NumberFormatException ignored) { return null; }
        }
        return null;
    }

    /** Profile null is saved as zero to remember an intentional per-game disable. */
    public static void remember(Context context, Shortcut shortcut, ControlsProfile profile) {
        if (context == null || shortcut == null) return;
        String identity = !isEmpty(shortcut.path) ? shortcut.path : shortcut.getExecutable();
        String key = preferenceKeyForIdentity(identity);
        if (key == null) return;
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putInt(key, profile != null ? profile.id : 0).apply();
    }

    private static boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }
}
