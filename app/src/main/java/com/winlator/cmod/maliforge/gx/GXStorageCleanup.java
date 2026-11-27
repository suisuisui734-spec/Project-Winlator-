package com.winlator.cmod.maliforge.gx;

import android.content.Context;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Preview-first storage management for MaliForge-owned shader profile caches.
 * Cleanup moves a validated profile directory into GXShaderLaboratory's recovery area;
 * it never permanently deletes cache or user data.
 */
public final class GXStorageCleanup {
    private static final int MAX_IDENTIFIER_LENGTH = 512;
    private final File cacheRoot;
    private final GXShaderLaboratory shaderLaboratory;
    private final Map<String, CleanupPlan> pendingPlans = new HashMap<>();

    public GXStorageCleanup(Context context) {
        this(context == null ? null : new File(context.getFilesDir(), "maliforge-gx/shader-cache"));
    }

    public GXStorageCleanup(File cacheRoot) {
        if (cacheRoot == null) throw new IllegalArgumentException("cacheRoot is required");
        this.cacheRoot = cacheRoot;
        shaderLaboratory = new GXShaderLaboratory(cacheRoot);
    }

    public synchronized Inventory inventoryProfile(String gameId, String profileId) {
        if (!validIdentifier(gameId) || !validIdentifier(profileId)) {
            return Inventory.invalid("Game and profile identifiers must be non-empty and at most 512 characters.");
        }
        GXShaderLaboratory.CacheInventory cache = shaderLaboratory.inventory(gameId, profileId);
        TemporaryInventory temporary = inspectTemporaryFiles(gameId, profileId);
        long total = safeAdd(cache.totalBytes, temporary.bytes);
        return new Inventory(true, cache.artifactCount, cache.validCount, cache.unverifiedCount,
                cache.corruptCount, cache.invalidEntryCount + temporary.invalidEntries,
                temporary.count, temporary.bytes, total, "");
    }

    /** Creates a one-use preview; no file is moved or removed here. */
    public synchronized CleanupPlan previewProfileCache(String gameId, String profileId) {
        Inventory inventory = inventoryProfile(gameId, profileId);
        if (!inventory.valid) return CleanupPlan.invalid(inventory.reason);
        if (inventory.totalBytes == 0 && inventory.artifactCount == 0
                && inventory.temporaryFileCount == 0 && inventory.invalidEntryCount == 0) {
            return CleanupPlan.empty(gameId, profileId);
        }
        String id = UUID.randomUUID().toString();
        CleanupPlan plan = new CleanupPlan(id, gameId.trim(), profileId.trim(), inventory,
                PlanStatus.READY, "GX-owned profile cache only. The operation moves it to a recoverable backup.");
        pendingPlans.put(id, plan);
        return plan;
    }

    /** Requires explicit confirmation and rejects a stale preview. The operation is reversible. */
    public synchronized Execution execute(String planId, boolean userConfirmed) {
        if (planId == null || !planId.matches("[0-9a-fA-F-]{36}")) {
            return Execution.failure(ExecutionStatus.INVALID_PLAN, "Cleanup plan identifier is invalid.");
        }
        CleanupPlan plan = pendingPlans.get(planId);
        if (plan == null) {
            return Execution.failure(ExecutionStatus.INVALID_PLAN, "Cleanup plan is missing or already used.");
        }
        if (!userConfirmed) {
            return Execution.failure(ExecutionStatus.CONFIRMATION_REQUIRED, "Explicit confirmation is required.");
        }
        pendingPlans.remove(planId);

        Inventory current = inventoryProfile(plan.gameId, plan.profileId);
        if (!current.valid || !plan.matches(current)) {
            return Execution.failure(ExecutionStatus.STALE_PREVIEW,
                    "Cache contents changed after preview. Create a new preview before cleanup.");
        }
        if (current.totalBytes == 0 && current.artifactCount == 0
                && current.temporaryFileCount == 0 && current.invalidEntryCount == 0) {
            return Execution.failure(ExecutionStatus.NOTHING_TO_CLEAN, "No GX-owned cache data is present.");
        }

        GXShaderLaboratory.CleanupResult result =
                shaderLaboratory.backupAndClearProfileCache(plan.gameId, plan.profileId);
        if (!result.success || result.backupId == null || result.backupId.isEmpty()) {
            return Execution.failure(ExecutionStatus.FAILED,
                    result.detail == null ? "GX recovery backup failed." : result.detail);
        }
        return Execution.backedUp(result.backupId,
                "GX cache moved to recovery backup. Restore remains available.");
    }

    /** Restores only a GX-generated backup and never overwrites a new cache directory. */
    public synchronized boolean restore(String gameId, String profileId, String backupId) {
        if (!validIdentifier(gameId) || !validIdentifier(profileId)
                || backupId == null || !backupId.matches("[0-9a-fA-F-]{36}")) return false;
        return shaderLaboratory.restoreBackup(gameId, profileId, backupId);
    }

    private TemporaryInventory inspectTemporaryFiles(String gameId, String profileId) {
        try {
            File directory = profileDirectory(gameId, profileId);
            if (directory == null || !directory.isDirectory() || Files.isSymbolicLink(directory.toPath())) {
                return TemporaryInventory.empty();
            }
            File[] children = directory.listFiles();
            if (children == null) return TemporaryInventory.empty();
            int count = 0;
            int invalid = 0;
            long bytes = 0L;
            for (File child : children) {
                if (!child.getName().endsWith(".tmp")) continue;
                if (Files.isSymbolicLink(child.toPath()) || !child.isFile()) {
                    invalid++;
                    continue;
                }
                count++;
                bytes = safeAdd(bytes, Math.max(0L, child.length()));
            }
            return new TemporaryInventory(count, bytes, invalid);
        } catch (IOException exception) {
            return new TemporaryInventory(0, 0L, 1);
        }
    }

    private File profileDirectory(String gameId, String profileId) throws IOException {
        File gameDirectory = new File(cacheRoot, sha256(gameId.trim()));
        File profileDirectory = new File(gameDirectory, sha256(profileId.trim()));
        if (!isChildOf(cacheRoot, profileDirectory)
                || Files.isSymbolicLink(cacheRoot.toPath())
                || Files.isSymbolicLink(gameDirectory.toPath())
                || Files.isSymbolicLink(profileDirectory.toPath())) return null;
        return profileDirectory;
    }

    private static boolean isChildOf(File parent, File child) throws IOException {
        String root = parent.getCanonicalPath();
        String target = child.getCanonicalPath();
        return target.startsWith(root + File.separator) && !target.equals(root);
    }

    private static boolean validIdentifier(String value) {
        return value != null && !value.trim().isEmpty() && value.length() <= MAX_IDENTIFIER_LENGTH;
    }

    private static long safeAdd(long left, long right) {
        if (right > 0 && left > Long.MAX_VALUE - right) return Long.MAX_VALUE;
        return Math.max(0L, left) + Math.max(0L, right);
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

    private static final class TemporaryInventory {
        final int count;
        final long bytes;
        final int invalidEntries;
        TemporaryInventory(int count, long bytes, int invalidEntries) {
            this.count = count;
            this.bytes = bytes;
            this.invalidEntries = invalidEntries;
        }
        static TemporaryInventory empty() { return new TemporaryInventory(0, 0L, 0); }
    }

    public enum PlanStatus { READY, EMPTY, INVALID }
    public enum ExecutionStatus {
        BACKED_UP, CONFIRMATION_REQUIRED, STALE_PREVIEW, NOTHING_TO_CLEAN,
        INVALID_PLAN, FAILED
    }

    public static final class Inventory {
        public final boolean valid;
        public final int artifactCount;
        public final int validArtifactCount;
        public final int unverifiedArtifactCount;
        public final int corruptArtifactCount;
        public final int invalidEntryCount;
        public final int temporaryFileCount;
        public final long temporaryBytes;
        public final long totalBytes;
        public final String reason;

        private Inventory(boolean valid, int artifacts, int verified, int unverified, int corrupt,
                          int invalid, int temporaryFiles, long temporaryBytes, long totalBytes, String reason) {
            this.valid = valid;
            artifactCount = artifacts;
            validArtifactCount = verified;
            unverifiedArtifactCount = unverified;
            corruptArtifactCount = corrupt;
            invalidEntryCount = invalid;
            temporaryFileCount = temporaryFiles;
            this.temporaryBytes = temporaryBytes;
            this.totalBytes = totalBytes;
            this.reason = reason;
        }
        private static Inventory invalid(String reason) {
            return new Inventory(false, 0, 0, 0, 0, 0, 0, 0L, 0L, reason);
        }
    }

    public static final class CleanupPlan {
        public final String id;
        public final String gameId;
        public final String profileId;
        public final Inventory inventory;
        public final PlanStatus status;
        public final String detail;

        private CleanupPlan(String id, String gameId, String profileId, Inventory inventory,
                            PlanStatus status, String detail) {
            this.id = id;
            this.gameId = gameId;
            this.profileId = profileId;
            this.inventory = inventory;
            this.status = status;
            this.detail = detail;
        }
        private static CleanupPlan empty(String gameId, String profileId) {
            return new CleanupPlan("", gameId, profileId, Inventory.invalid("No cache data."),
                    PlanStatus.EMPTY, "No cleanup is needed.");
        }
        private static CleanupPlan invalid(String reason) {
            return new CleanupPlan("", "", "", Inventory.invalid(reason), PlanStatus.INVALID, reason);
        }
        private boolean matches(Inventory current) {
            return inventory.artifactCount == current.artifactCount
                    && inventory.validArtifactCount == current.validArtifactCount
                    && inventory.unverifiedArtifactCount == current.unverifiedArtifactCount
                    && inventory.corruptArtifactCount == current.corruptArtifactCount
                    && inventory.invalidEntryCount == current.invalidEntryCount
                    && inventory.temporaryFileCount == current.temporaryFileCount
                    && inventory.temporaryBytes == current.temporaryBytes
                    && inventory.totalBytes == current.totalBytes;
        }
    }

    public static final class Execution {
        public final ExecutionStatus status;
        public final String backupId;
        public final String detail;
        private Execution(ExecutionStatus status, String backupId, String detail) {
            this.status = status;
            this.backupId = backupId;
            this.detail = detail;
        }
        private static Execution failure(ExecutionStatus status, String detail) {
            return new Execution(status, "", detail);
        }
        private static Execution backedUp(String id, String detail) {
            return new Execution(ExecutionStatus.BACKED_UP, id, detail);
        }
        public boolean succeeded() { return status == ExecutionStatus.BACKED_UP; }
    }
}
