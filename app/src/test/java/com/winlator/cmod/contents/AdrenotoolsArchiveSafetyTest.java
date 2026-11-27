package com.winlator.cmod.contents;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.IOException;

public class AdrenotoolsArchiveSafetyTest {
    @Test public void acceptsNormalDriverArchivePaths() throws Exception {
        assertEquals("meta.json", AdrenotoolsArchiveSafety.validateEntryName("meta.json"));
        assertEquals("libvulkan_panfrost.so",
                AdrenotoolsArchiveSafety.validateEntryName("libvulkan_panfrost.so"));
        assertEquals("lib/", AdrenotoolsArchiveSafety.validateEntryName("lib/"));
        assertEquals("lib/driver.so",
                AdrenotoolsArchiveSafety.validateEntryName("lib/driver.so"));
    }

    @Test public void rejectsTraversalAndPlatformAbsolutePaths() {
        assertUnsafe("../outside");
        assertUnsafe("lib/../../outside");
        assertUnsafe("/system/lib64/evil.so");
        assertUnsafe("C:/Windows/evil.dll");
        assertUnsafe("lib\\..\\outside");
        assertUnsafe("lib//driver.so");
        assertUnsafe("./meta.json");
        assertUnsafe("lib/./driver.so");
    }

    @Test public void rejectsUnsafeDriverFolderNames() {
        assertTrue(AdrenotoolsArchiveSafety.isSafeDriverDirectoryName("Mali PanVK r10"));
        assertTrue(AdrenotoolsArchiveSafety.isSafeDriverDirectoryName("driver-v1.2"));
        assertFalse(AdrenotoolsArchiveSafety.isSafeDriverDirectoryName("../escape"));
        assertFalse(AdrenotoolsArchiveSafety.isSafeDriverDirectoryName("Bifrost/Valhall"));
        assertFalse(AdrenotoolsArchiveSafety.isSafeDriverDirectoryName("tmp"));
        assertFalse(AdrenotoolsArchiveSafety.isSafeDriverDirectoryName("System"));
    }

    @Test public void validatesLibraryMetadataPath() {
        assertTrue(AdrenotoolsArchiveSafety.isSafeLibraryRelativePath("libvulkan_panfrost.so"));
        assertTrue(AdrenotoolsArchiveSafety.isSafeLibraryRelativePath("vulkan/libdriver.so"));
        assertFalse(AdrenotoolsArchiveSafety.isSafeLibraryRelativePath("../libdriver.so"));
        assertFalse(AdrenotoolsArchiveSafety.isSafeLibraryRelativePath("/libdriver.so"));
        assertFalse(AdrenotoolsArchiveSafety.isSafeLibraryRelativePath("driver.txt"));
    }

    @Test public void recognisesOnlyArm64LittleEndianElf() {
        byte[] header = new byte[20];
        header[0] = 0x7f;
        header[1] = 'E';
        header[2] = 'L';
        header[3] = 'F';
        header[4] = 2;
        header[5] = 1;
        header[18] = (byte) 0xb7;
        header[19] = 0;
        assertTrue(AdrenotoolsArchiveSafety.isElf64Aarch64(header));

        header[18] = 62; // x86_64
        assertFalse(AdrenotoolsArchiveSafety.isElf64Aarch64(header));
        header[18] = (byte) 0xb7;
        header[4] = 1; // ELF32
        assertFalse(AdrenotoolsArchiveSafety.isElf64Aarch64(header));
    }

    private static void assertUnsafe(String path) {
        try {
            AdrenotoolsArchiveSafety.validateEntryName(path);
            fail("Expected unsafe path to be rejected: " + path);
        } catch (IOException expected) {
            // Expected.
        }
    }
}
