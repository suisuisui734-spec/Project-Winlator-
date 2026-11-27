package com.winlator.cmod.maliforge.gx;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

public class GXStorageCleanupTest {
    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test public void previewRequiresConfirmationAndBackupCanBeRestored() throws Exception {
        File root = new File(temporaryFolder.getRoot(), "maliforge-gx/shader-cache");
        GXShaderLaboratory laboratory = new GXShaderLaboratory(root);
        assertTrue(laboratory.writeArtifact("game-a", "balanced", "pipeline.bin",
                "winlator-dxvk", "unknown", "1", "shader-cache".getBytes(StandardCharsets.UTF_8)));

        GXStorageCleanup cleanup = new GXStorageCleanup(root);
        GXStorageCleanup.Inventory inventory = cleanup.inventoryProfile("game-a", "balanced");
        assertTrue(inventory.valid);
        assertEquals(1, inventory.artifactCount);
        assertTrue(inventory.totalBytes > 0);

        GXStorageCleanup.CleanupPlan plan = cleanup.previewProfileCache("game-a", "balanced");
        assertEquals(GXStorageCleanup.PlanStatus.READY, plan.status);
        assertEquals(GXStorageCleanup.ExecutionStatus.CONFIRMATION_REQUIRED,
                cleanup.execute(plan.id, false).status);

        GXStorageCleanup.Execution result = cleanup.execute(plan.id, true);
        assertTrue(result.succeeded());
        assertFalse(cleanup.inventoryProfile("game-a", "balanced").totalBytes > 0);
        assertTrue(cleanup.restore("game-a", "balanced", result.backupId));
        assertEquals(1, cleanup.inventoryProfile("game-a", "balanced").artifactCount);
    }

    @Test public void changedCacheInvalidatesOldPreview() {
        File root = new File(temporaryFolder.getRoot(), "cache");
        GXShaderLaboratory laboratory = new GXShaderLaboratory(root);
        assertTrue(laboratory.writeArtifact("game-b", "profile", "one.bin",
                "backend", "driver", "1", new byte[]{1, 2, 3}));
        GXStorageCleanup cleanup = new GXStorageCleanup(root);
        GXStorageCleanup.CleanupPlan plan = cleanup.previewProfileCache("game-b", "profile");

        assertTrue(laboratory.writeArtifact("game-b", "profile", "two.bin",
                "backend", "driver", "1", new byte[]{4, 5, 6}));
        assertEquals(GXStorageCleanup.ExecutionStatus.STALE_PREVIEW,
                cleanup.execute(plan.id, true).status);
    }

    @Test public void externalUserDataIsNeverTouched() throws Exception {
        File outside = new File(temporaryFolder.getRoot(), "important-save.dat");
        try (FileOutputStream output = new FileOutputStream(outside)) {
            output.write(new byte[]{9, 8, 7});
        }
        File root = new File(temporaryFolder.getRoot(), "private/gx/shader-cache");
        GXShaderLaboratory laboratory = new GXShaderLaboratory(root);
        assertTrue(laboratory.writeArtifact("game-c", "profile", "cache.bin",
                "backend", "driver", "1", new byte[]{1}));
        GXStorageCleanup cleanup = new GXStorageCleanup(root);
        GXStorageCleanup.CleanupPlan plan = cleanup.previewProfileCache("game-c", "profile");
        assertTrue(cleanup.execute(plan.id, true).succeeded());

        assertTrue(outside.isFile());
        assertEquals(3L, outside.length());
    }

    @Test public void rejectsInvalidIdentifiersAndEmptyCache() {
        File root = new File(temporaryFolder.getRoot(), "cache");
        GXStorageCleanup cleanup = new GXStorageCleanup(root);
        assertEquals(GXStorageCleanup.PlanStatus.INVALID, cleanup.previewProfileCache(" ", "profile").status);
        assertEquals(GXStorageCleanup.PlanStatus.EMPTY, cleanup.previewProfileCache("game", "profile").status);
    }
}
