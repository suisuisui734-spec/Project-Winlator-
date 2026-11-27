package com.winlator.cmod.maliforge.gx;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import static org.junit.Assert.*;

public class GXShaderLaboratoryTest {
    private File root;
    private GXShaderLaboratory lab;

    @Before public void setUp() throws Exception {
        root = Files.createTempDirectory("gx-shader-lab").toFile();
        lab = new GXShaderLaboratory(root);
    }

    @After public void tearDown() {
        delete(root);
    }

    @Test public void writesAndValidatesArtifactMetadata() throws Exception {
        byte[] bytes = new byte[] {1, 2, 3, 4, 5};
        assertTrue(lab.writeArtifact("game-a", "profile-1", "pipeline.bin",
                "dxvk", "driver-1", "format-1", bytes));
        GXShaderLaboratory.ArtifactReport report =
                lab.inspectArtifact("game-a", "profile-1", "pipeline.bin");
        assertEquals(GXShaderLaboratory.ArtifactStatus.VALID, report.status);
        assertEquals(5L, report.sizeBytes);
        assertEquals("dxvk", report.backend);
        assertEquals("driver-1", report.driverVersion);
    }

    @Test public void detectsContentCorruption() throws Exception {
        assertTrue(lab.writeArtifact("game-a", "profile-1", "pipeline.bin",
                "dxvk", "driver-1", "format-1", new byte[] {1, 2, 3}));
        File artifact = new File(lab.getProfileCacheDirectory("game-a", "profile-1"), "pipeline.bin");
        try (FileOutputStream output = new FileOutputStream(artifact, false)) {
            output.write(new byte[] {9, 9, 9});
        }
        assertEquals(GXShaderLaboratory.ArtifactStatus.CORRUPT,
                lab.inspectArtifact("game-a", "profile-1", "pipeline.bin").status);
    }

    @Test public void marksUnknownExternalFilesUnverifiedRatherThanCorrupt() throws Exception {
        File directory = lab.getProfileCacheDirectory("game-a", "profile-1");
        Files.write(new File(directory, "unknown.cache").toPath(), new byte[] {4, 2});
        assertEquals(GXShaderLaboratory.ArtifactStatus.UNVERIFIED,
                lab.inspectArtifact("game-a", "profile-1", "unknown.cache").status);
    }

    @Test public void rejectsTraversalAndUnsafeNames() throws Exception {
        assertFalse(lab.writeArtifact("game-a", "profile-1", "../outside",
                "dxvk", "driver", "1", new byte[] {1}));
        assertEquals(GXShaderLaboratory.ArtifactStatus.INVALID_PATH,
                lab.inspectArtifact("game-a", "profile-1", "../outside").status);
    }

    @Test public void inventorySeparatesVerifiedUnverifiedAndCorruptFiles() throws Exception {
        lab.writeArtifact("game-a", "profile-1", "valid.bin",
                "dxvk", "driver", "1", new byte[] {1, 2});
        File directory = lab.getProfileCacheDirectory("game-a", "profile-1");
        Files.write(new File(directory, "unknown.bin").toPath(), new byte[] {3});
        lab.writeArtifact("game-a", "profile-1", "corrupt.bin",
                "dxvk", "driver", "1", new byte[] {4, 5});
        File corrupt = new File(directory, "corrupt.bin");
        Files.write(corrupt.toPath(), new byte[] {0});
        GXShaderLaboratory.CacheInventory inventory = lab.inventory("game-a", "profile-1");
        assertEquals(3, inventory.artifactCount);
        assertEquals(1, inventory.validCount);
        assertEquals(1, inventory.unverifiedCount);
        assertEquals(1, inventory.corruptCount);
        assertEquals(4L, inventory.totalBytes);
    }

    @Test public void cleanupCreatesBackupAndRestoreNeverOverwrites() throws Exception {
        lab.writeArtifact("game-a", "profile-1", "pipeline.bin",
                "dxvk", "driver", "1", new byte[] {7});
        GXShaderLaboratory.CleanupResult cleanup =
                lab.backupAndClearProfileCache("game-a", "profile-1");
        assertTrue(cleanup.success);
        assertFalse(cleanup.backupId.isEmpty());
        assertEquals(0, lab.inventory("game-a", "profile-1").artifactCount);
        assertTrue(lab.restoreBackup("game-a", "profile-1", cleanup.backupId));
        assertEquals(GXShaderLaboratory.ArtifactStatus.VALID,
                lab.inspectArtifact("game-a", "profile-1", "pipeline.bin").status);
        assertFalse(lab.restoreBackup("game-a", "profile-1", cleanup.backupId));
    }

    private static void delete(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) delete(child);
        file.delete();
    }
}
