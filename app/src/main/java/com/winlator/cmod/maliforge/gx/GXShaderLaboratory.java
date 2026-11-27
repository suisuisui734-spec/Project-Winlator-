package com.winlator.cmod.maliforge.gx;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import android.content.Context;

/**
 * Integrity and recovery tools for MaliForge-owned shader/cache artifacts.
 *
 * This is not connected to DXVK/VKD3D/Wine pipeline-cache creation. It only manages files
 * explicitly written through this class below the dedicated GX cache root.
 */
public final class GXShaderLaboratory {
    private static final String METADATA_SUFFIX = ".gxmeta";
    private static final String METADATA_VERSION = "1";
    private static final int MAX_ARTIFACT_BYTES = 64 * 1024 * 1024;

    public enum ArtifactStatus { VALID, UNVERIFIED, CORRUPT, MISSING, INVALID_PATH }

    private final File rootDirectory;
    private final File recoveryDirectory;

    public GXShaderLaboratory(Context context) {
        this(context == null ? null : new File(context.getFilesDir(), "maliforge-gx/shader-cache"));
    }

    public GXShaderLaboratory(File rootDirectory) {
        if (rootDirectory == null) throw new IllegalArgumentException("rootDirectory is required");
        this.rootDirectory = rootDirectory;
        this.recoveryDirectory = new File(rootDirectory, "recovery");
    }

    public File getProfileCacheDirectory(String gameId, String profileId) throws IOException {
        File directory = profileDirectory(gameId, profileId, true);
        return directory;
    }

    /**
     * Writes a GX-owned cache artifact and a SHA-256 sidecar atomically where supported.
     * artifactName must be a single safe filename; arbitrary paths are never accepted.
     */
    public boolean writeArtifact(String gameId, String profileId, String artifactName,
                                 String backendId, String driverVersion, String formatVersion,
                                 byte[] content) {
        if (!isSafeName(artifactName) || content == null || content.length > MAX_ARTIFACT_BYTES) return false;
        try {
            File directory = profileDirectory(gameId, profileId, true);
            if (directory == null) return false;
            File artifact = new File(directory, artifactName);
            File metadata = new File(directory, artifactName + METADATA_SUFFIX);
            if (!isChildOf(directory, artifact) || !isChildOf(directory, metadata)) return false;

            File artifactTemp = new File(directory, artifactName + ".tmp");
            File metadataTemp = new File(directory, artifactName + METADATA_SUFFIX + ".tmp");
            writeBytesSynced(artifactTemp, content);

            Properties properties = new Properties();
            properties.setProperty("version", METADATA_VERSION);
            properties.setProperty("size", String.valueOf(content.length));
            properties.setProperty("sha256", sha256(content));
            properties.setProperty("backend", safeMetadataValue(backendId));
            properties.setProperty("driverVersion", safeMetadataValue(driverVersion));
            properties.setProperty("formatVersion", safeMetadataValue(formatVersion));
            properties.setProperty("createdAtMillis", String.valueOf(System.currentTimeMillis()));
            try (FileOutputStream output = new FileOutputStream(metadataTemp, false)) {
                properties.store(output, "MaliForge GX cache artifact metadata");
                output.flush();
                output.getFD().sync();
            }

            replaceFile(artifactTemp, artifact);
            replaceFile(metadataTemp, metadata);
            return inspectArtifact(gameId, profileId, artifactName).status == ArtifactStatus.VALID;
        } catch (IOException exception) {
            return false;
        }
    }

    public ArtifactReport inspectArtifact(String gameId, String profileId, String artifactName) {
        if (!isSafeName(artifactName)) return ArtifactReport.invalid(artifactName);
        try {
            File directory = profileDirectory(gameId, profileId, false);
            if (directory == null) return ArtifactReport.missing(artifactName);
            File artifact = new File(directory, artifactName);
            File metadata = new File(directory, artifactName + METADATA_SUFFIX);
            if (!isChildOf(directory, artifact) || !isChildOf(directory, metadata)
                    || Files.isSymbolicLink(artifact.toPath()) || Files.isSymbolicLink(metadata.toPath())) {
                return ArtifactReport.invalid(artifactName);
            }
            if (!artifact.isFile()) return ArtifactReport.missing(artifactName);
            if (!metadata.isFile()) return ArtifactReport.unverified(artifactName, artifact.length());

            Properties properties = new Properties();
            try (InputStream input = new FileInputStream(metadata)) {
                properties.load(input);
            }
            if (!METADATA_VERSION.equals(properties.getProperty("version"))) {
                return ArtifactReport.corrupt(artifactName, artifact.length(), "Unsupported metadata version.");
            }
            long recordedSize = Long.parseLong(properties.getProperty("size", "-1"));
            String recordedHash = properties.getProperty("sha256", "");
            String actualHash = sha256(artifact);
            if (recordedSize != artifact.length() || !recordedHash.equalsIgnoreCase(actualHash)) {
                return ArtifactReport.corrupt(artifactName, artifact.length(), "Size or SHA-256 mismatch.");
            }
            return ArtifactReport.valid(artifactName, artifact.length(), recordedHash,
                    properties.getProperty("backend", "unknown"),
                    properties.getProperty("driverVersion", "unknown"),
                    properties.getProperty("formatVersion", "unknown"));
        } catch (Exception exception) {
            return ArtifactReport.corrupt(artifactName, 0L,
                    exception.getClass().getSimpleName() + ": " + safeMetadataValue(exception.getMessage()));
        }
    }

    /** Inventory counts only regular GX-owned artifacts; metadata sidecars are excluded. */
    public CacheInventory inventory(String gameId, String profileId) {
        try {
            File directory = profileDirectory(gameId, profileId, false);
            if (directory == null) return CacheInventory.empty();
            File[] children = directory.listFiles();
            if (children == null) return CacheInventory.empty();
            long totalBytes = 0L;
            int valid = 0, unverified = 0, corrupt = 0, invalid = 0;
            List<String> artifactNames = new ArrayList<>();
            for (File child : children) {
                if (child.getName().endsWith(METADATA_SUFFIX) || child.getName().endsWith(".tmp")) continue;
                if (Files.isSymbolicLink(child.toPath()) || !child.isFile()) {
                    invalid++;
                    continue;
                }
                artifactNames.add(child.getName());
                ArtifactReport report = inspectArtifact(gameId, profileId, child.getName());
                totalBytes += Math.max(0L, child.length());
                switch (report.status) {
                    case VALID: valid++; break;
                    case UNVERIFIED: unverified++; break;
                    case CORRUPT: corrupt++; break;
                    default: invalid++; break;
                }
            }
            return new CacheInventory(artifactNames.size(), totalBytes, valid, unverified, corrupt, invalid);
        } catch (IOException exception) {
            return CacheInventory.empty();
        }
    }

    /**
     * Moves the selected GX profile cache into a recovery directory instead of deleting it.
     * Callers should expose this as a user-confirmed cleanup action.
     */
    public CleanupResult backupAndClearProfileCache(String gameId, String profileId) {
        try {
            File directory = profileDirectory(gameId, profileId, false);
            if (directory == null && (gameId == null || gameId.trim().isEmpty()
                    || profileId == null || profileId.trim().isEmpty())) {
                return CleanupResult.failure("Game and profile identifiers are required.");
            }
            if (directory == null || !directory.exists()) return CleanupResult.success("");
            if (Files.isSymbolicLink(directory.toPath()) || !isChildOf(rootDirectory, directory)) {
                return CleanupResult.failure("Cache path is not a GX-owned directory.");
            }
            if (!recoveryDirectory.exists() && !recoveryDirectory.mkdirs()) {
                return CleanupResult.failure("Unable to create the GX recovery directory.");
            }
            String backupId = UUID.randomUUID().toString();
            File backup = new File(recoveryDirectory, backupId);
            if (!isChildOf(recoveryDirectory, backup)) {
                return CleanupResult.failure("Recovery path validation failed.");
            }
            replaceDirectory(directory, backup);
            // Leave the profile path absent. A future write recreates it; restore can move
            // the backup directly without risking an overwrite of a newly created cache.
            return CleanupResult.success(backupId);
        } catch (IOException exception) {
            return CleanupResult.failure(exception.getClass().getSimpleName() + ": "
                    + safeMetadataValue(exception.getMessage()));
        }
    }

    /** Restores a backup only when the destination profile cache is absent; never overwrites. */
    public boolean restoreBackup(String gameId, String profileId, String backupId) {
        if (backupId == null || !backupId.matches("[0-9a-fA-F-]{36}")) return false;
        try {
            File backup = new File(recoveryDirectory, backupId);
            File destination = profileDirectory(gameId, profileId, false);
            if (destination != null && destination.exists()) return false;
            if (!backup.isDirectory() || Files.isSymbolicLink(backup.toPath())
                    || !isChildOf(recoveryDirectory, backup)) return false;
            File parent = destination == null ? profileParentDirectory(gameId, true) : destination.getParentFile();
            if (parent == null || !isChildOf(rootDirectory, parent)) return false;
            if (!parent.exists() && !parent.mkdirs()) return false;
            File target = new File(parent, profileHash(profileId));
            if (target.exists()) return false;
            replaceDirectory(backup, target);
            return target.isDirectory();
        } catch (IOException exception) {
            return false;
        }
    }

    private File profileDirectory(String gameId, String profileId, boolean create) throws IOException {
        if (profileId == null || profileId.trim().isEmpty()) return null;
        File parent = profileParentDirectory(gameId, create);
        if (parent == null) return null;
        File directory = new File(parent, profileHash(profileId));
        if (!isChildOf(rootDirectory, directory) || Files.isSymbolicLink(directory.toPath())) return null;
        if (create && !directory.exists() && !directory.mkdirs()) return null;
        return directory.exists() && directory.isDirectory() ? directory : null;
    }

    private File profileParentDirectory(String gameId, boolean create) throws IOException {
        if (gameId == null || gameId.trim().isEmpty()) return null;
        if (create && !rootDirectory.exists() && !rootDirectory.mkdirs()) return null;
        if (!rootDirectory.exists() || Files.isSymbolicLink(rootDirectory.toPath())) return null;
        File parent = new File(rootDirectory, sha256(gameId.trim()));
        if (!isChildOf(rootDirectory, parent) || Files.isSymbolicLink(parent.toPath())) return null;
        if (create && !parent.exists() && !parent.mkdirs()) return null;
        return parent.exists() && parent.isDirectory() ? parent : null;
    }

    private static String profileHash(String value) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException("profileId is required");
        return sha256(value.trim());
    }

    private static boolean isSafeName(String name) {
        return name != null && name.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")
                && !name.endsWith(METADATA_SUFFIX) && !name.endsWith(".tmp");
    }

    private static boolean isChildOf(File parent, File child) throws IOException {
        String parentPath = parent.getCanonicalPath();
        String childPath = child.getCanonicalPath();
        return childPath.startsWith(parentPath + File.separator);
    }

    private static String safeMetadataValue(String value) {
        if (value == null) return "unknown";
        String normalized = value.replace('\n', ' ').replace('\r', ' ').trim();
        return normalized.length() > 256 ? normalized.substring(0, 256) : normalized;
    }

    private static void writeBytesSynced(File file, byte[] bytes) throws IOException {
        try (FileOutputStream output = new FileOutputStream(file, false)) {
            output.write(bytes);
            output.flush();
            output.getFD().sync();
        }
    }

    private static void replaceFile(File source, File destination) throws IOException {
        try {
            Files.move(source.toPath(), destination.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void replaceDirectory(File source, File destination) throws IOException {
        try {
            Files.move(source.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source.toPath(), destination.toPath());
        }
    }

    private static String sha256(byte[] content) {
        return hexDigest(content);
    }

    private static String sha256(File file) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[16 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        return toHex(digest.digest());
    }

    private static String hexDigest(byte[] content) {
        try {
            return toHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String sha256(String value) {
        return hexDigest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static String toHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format(java.util.Locale.ROOT, "%02x", value & 0xff));
        return result.toString();
    }

    public static final class ArtifactReport {
        public final String name;
        public final ArtifactStatus status;
        public final long sizeBytes;
        public final String sha256;
        public final String backend;
        public final String driverVersion;
        public final String formatVersion;
        public final String detail;

        private ArtifactReport(String name, ArtifactStatus status, long sizeBytes, String hash,
                               String backend, String driverVersion, String formatVersion, String detail) {
            this.name = name;
            this.status = status;
            this.sizeBytes = sizeBytes;
            this.sha256 = hash;
            this.backend = backend;
            this.driverVersion = driverVersion;
            this.formatVersion = formatVersion;
            this.detail = detail;
        }
        private static ArtifactReport valid(String n,long s,String h,String b,String d,String f) {
            return new ArtifactReport(n,ArtifactStatus.VALID,s,h,b,d,f,"Integrity metadata matches.");
        }
        private static ArtifactReport unverified(String n,long s) {
            return new ArtifactReport(n,ArtifactStatus.UNVERIFIED,s,"","","","","No GX integrity sidecar exists.");
        }
        private static ArtifactReport corrupt(String n,long s,String d) {
            return new ArtifactReport(n,ArtifactStatus.CORRUPT,s,"","","","",d);
        }
        private static ArtifactReport missing(String n) {
            return new ArtifactReport(n,ArtifactStatus.MISSING,0L,"","","","","Artifact is missing.");
        }
        private static ArtifactReport invalid(String n) {
            return new ArtifactReport(n,ArtifactStatus.INVALID_PATH,0L,"","","","","Unsafe or invalid artifact path.");
        }
    }

    public static final class CacheInventory {
        public final int artifactCount;
        public final long totalBytes;
        public final int validCount;
        public final int unverifiedCount;
        public final int corruptCount;
        public final int invalidEntryCount;

        private CacheInventory(int count,long bytes,int valid,int unverified,int corrupt,int invalid) {
            artifactCount=count; totalBytes=bytes; validCount=valid; unverifiedCount=unverified;
            corruptCount=corrupt; invalidEntryCount=invalid;
        }
        private static CacheInventory empty() { return new CacheInventory(0,0L,0,0,0,0); }
    }

    public static final class CleanupResult {
        public final boolean success;
        public final String backupId;
        public final String detail;
        private CleanupResult(boolean success,String backupId,String detail) {
            this.success=success; this.backupId=backupId; this.detail=detail;
        }
        private static CleanupResult success(String id) { return new CleanupResult(true,id,"Cache moved to recovery backup."); }
        private static CleanupResult failure(String reason) { return new CleanupResult(false,"",reason); }
    }
}
