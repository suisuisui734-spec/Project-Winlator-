package com.winlator.cmod.maliforge.gx;

import android.content.Context;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Directory manager for MaliForge-owned cache data only.
 * It never reads or deletes existing Wrapper, DXVK, VKD3D, Wine, or game caches.
 */
public final class GXShaderCacheStore {
    private final File rootDirectory;

    public GXShaderCacheStore(Context context) {
        if (context == null) throw new IllegalArgumentException("context is required");
        rootDirectory = new File(context.getFilesDir(), "maliforge-gx/shader-cache");
    }

    public File getGameCacheDirectory(String gameId) {
        if (gameId == null || gameId.trim().isEmpty()) return null;
        if (!rootDirectory.exists() && !rootDirectory.mkdirs()) return null;
        File target = new File(rootDirectory, sha256(gameId.trim()));
        if (!target.exists() && !target.mkdirs()) return null;
        try {
            return isChildOfRoot(target) ? target : null;
        } catch (IOException exception) {
            return null;
        }
    }

    public long getGameCacheSize(String gameId) {
        File directory = getGameCacheDirectory(gameId);
        return directory == null ? 0L : directorySize(directory);
    }

    /** Deletes only the hashed MaliForge-owned game cache directory. */
    public boolean clearGameCache(String gameId) {
        if (gameId == null || gameId.trim().isEmpty()) return false;
        if (!rootDirectory.exists()) return true;
        File target = new File(rootDirectory, sha256(gameId.trim()));
        try {
            if (!target.exists()) return true;
            if (!isChildOfRoot(target)) return false;
            return deleteRecursively(target);
        } catch (IOException exception) {
            return false;
        }
    }

    private boolean isChildOfRoot(File target) throws IOException {
        String root = rootDirectory.getCanonicalPath();
        String child = target.getCanonicalPath();
        return child.startsWith(root + File.separator) && !child.equals(root);
    }

    private static long directorySize(File file) {
        if (Files.isSymbolicLink(file.toPath())) return 0L;
        if (file.isFile()) return Math.max(0L, file.length());
        File[] children = file.listFiles();
        if (children == null) return 0L;
        long total = 0L;
        for (File child : children) total += directorySize(child);
        return total;
    }

    private static boolean deleteRecursively(File file) {
        if (Files.isSymbolicLink(file.toPath()) || file.isFile()) return file.delete();
        File[] children = file.listFiles();
        if (children == null) return false;
        boolean success = true;
        for (File child : children) success &= deleteRecursively(child);
        return success && file.delete();
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
