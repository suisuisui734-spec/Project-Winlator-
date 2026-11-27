package com.winlator.cmod.maliforge.gx;

import android.content.Context;
import android.content.SharedPreferences;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/** Stores MaliForge profiles in its own preference file, separate from Container settings. */
public final class GXProfileStore {
    private static final String PREFERENCES = "maliforge_gx_profiles_v1";
    private final SharedPreferences preferences;

    public GXProfileStore(Context context) {
        if (context == null) throw new IllegalArgumentException("context is required");
        preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    public boolean save(int containerId, String gameId, GXGameProfile profile) {
        if (containerId < 0 || profile == null) return false;
        String key = buildKey(containerId, gameId);
        return key != null && preferences.edit().putString(key, profile.toStorageValue()).commit();
    }

    public GXGameProfile load(int containerId, String gameId) {
        if (containerId < 0) return null;
        String key = buildKey(containerId, gameId);
        return key == null ? null : GXGameProfile.fromStorageValue(preferences.getString(key, null));
    }

    public boolean delete(int containerId, String gameId) {
        if (containerId < 0) return false;
        String key = buildKey(containerId, gameId);
        return key != null && preferences.edit().remove(key).commit();
    }

    static String buildKey(int containerId, String gameId) {
        if (gameId == null || gameId.trim().isEmpty()) return null;
        return "profile.v1." + containerId + "." + sha256(gameId.trim().toLowerCase(Locale.ROOT));
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) result.append(String.format(Locale.ROOT, "%02x", item & 0xff));
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
