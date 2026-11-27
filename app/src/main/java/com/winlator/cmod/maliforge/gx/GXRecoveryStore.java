package com.winlator.cmod.maliforge.gx;

import android.content.Context;
import android.content.SharedPreferences;

/** Persists MaliForge's own last-known-good profile without editing Container driver fields. */
public final class GXRecoveryStore {
    private static final String PREFERENCES = "maliforge_gx_recovery_v1";
    private final SharedPreferences preferences;

    public GXRecoveryStore(Context context) {
        if (context == null) throw new IllegalArgumentException("context is required");
        preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    public boolean saveLastKnownGood(int containerId, String gameId, GXGameProfile profile) {
        if (containerId < 0 || profile == null) return false;
        String key = GXProfileStore.buildKey(containerId, gameId);
        return key != null && preferences.edit().putString(key, profile.toStorageValue()).commit();
    }

    public GXGameProfile loadLastKnownGood(int containerId, String gameId) {
        if (containerId < 0) return null;
        String key = GXProfileStore.buildKey(containerId, gameId);
        return key == null ? null : GXGameProfile.fromStorageValue(preferences.getString(key, null));
    }

    public boolean clearLastKnownGood(int containerId, String gameId) {
        if (containerId < 0) return false;
        String key = GXProfileStore.buildKey(containerId, gameId);
        return key != null && preferences.edit().remove(key).commit();
    }
}
