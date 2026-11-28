package com.winlator.cmod.maliforge.gx;

import org.junit.Test;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

public class GXExternalPanVKCandidateTest {
    @Test public void missingDirectoryIsNotInstalled() {
        GXExternalPanVKCandidate result = GXExternalPanVKCandidate.inspect(null);
        assertEquals(GXExternalPanVKCandidate.Status.NOT_INSTALLED, result.getStatus());
        assertFalse(result.isExternalCandidate());
    }

    @Test public void packageWithUnsafeLibraryPathIsRejected() {
        assertFalse(GXExternalPanVKCandidate.isSafeRelativeLibraryPath("../libpanvk.so"));
        assertFalse(GXExternalPanVKCandidate.isSafeRelativeLibraryPath("/libpanvk.so"));
        assertFalse(GXExternalPanVKCandidate.isSafeRelativeLibraryPath("C:/libpanvk.so"));
        assertFalse(GXExternalPanVKCandidate.isSafeRelativeLibraryPath("libpanvk.txt"));
        assertTrue(GXExternalPanVKCandidate.isSafeRelativeLibraryPath("lib/libpanvk.so"));
    }

    @Test public void validMetadataAndArm64ElfAreOnlyExternalCandidate() throws Exception {
        File dir = new File(System.getProperty("java.io.tmpdir"),
                "gx-panvk-candidate-" + System.nanoTime());
        assertTrue(dir.mkdirs());
        try {
            String metadata = "{\"name\":\"PanVK test package\",\"driverVersion\":\"test-1\","
                    + "\"libraryName\":\"libpanvk.so\"}";
            try (FileOutputStream out = new FileOutputStream(new File(dir, "meta.json"))) {
                out.write(metadata.getBytes(StandardCharsets.UTF_8));
            }
            byte[] header = new byte[20];
            header[0] = 0x7f; header[1] = 'E'; header[2] = 'L'; header[3] = 'F';
            header[4] = 2; header[5] = 1; header[16] = 3; header[18] = (byte) 0xb7;
            try (FileOutputStream out = new FileOutputStream(new File(dir, "libpanvk.so"))) {
                out.write(header);
            }
            GXExternalPanVKCandidate result = GXExternalPanVKCandidate.inspect(dir);
            assertEquals(GXExternalPanVKCandidate.Status.EXTERNAL_CANDIDATE, result.getStatus());
            assertEquals("PanVK test package", result.getName());
            assertTrue(result.getDiagnostic().contains("not runtime"));
            assertFalse(result.getDiagnostic().contains("rendering verified"));
        } finally {
            delete(dir);
        }
    }

    @Test public void wrongMetadataIsRejected() throws Exception {
        File dir = new File(System.getProperty("java.io.tmpdir"),
                "gx-panvk-invalid-" + System.nanoTime());
        assertTrue(dir.mkdirs());
        try {
            try (FileOutputStream out = new FileOutputStream(new File(dir, "meta.json"))) {
                out.write("{\"name\":\"Other Vulkan driver\",\"driverVersion\":\"1\","
                        .getBytes(StandardCharsets.UTF_8));
            }
            GXExternalPanVKCandidate result = GXExternalPanVKCandidate.inspect(dir);
            assertEquals(GXExternalPanVKCandidate.Status.INVALID_PACKAGE, result.getStatus());
        } finally {
            delete(dir);
        }
    }

    private static void delete(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) delete(child);
        file.delete();
    }
}
