package com.winlator.cmod.maliforge.gx;

import org.junit.Test;
import static org.junit.Assert.*;

public class GXGuestVulkanLayerInstallerTest {
    private static byte[] header(int elfClass, int data, int machine) {
        byte[] h = new byte[20];
        h[0] = 0x7f; h[1] = 'E'; h[2] = 'L'; h[3] = 'F';
        h[4] = (byte) elfClass; h[5] = (byte) data;
        h[18] = (byte) machine; h[19] = (byte) (machine >> 8);
        return h;
    }

    @Test public void acceptsOnlyLinuxElf64LittleEndianX8664() {
        assertTrue(GXGuestVulkanLayerInstaller.isLinuxX86_64Elf(header(2, 1, 62)));
        assertFalse(GXGuestVulkanLayerInstaller.isLinuxX86_64Elf(header(1, 1, 62)));
        assertFalse(GXGuestVulkanLayerInstaller.isLinuxX86_64Elf(header(2, 2, 62)));
        assertFalse(GXGuestVulkanLayerInstaller.isLinuxX86_64Elf(header(2, 1, 183)));
        assertFalse(GXGuestVulkanLayerInstaller.isLinuxX86_64Elf(new byte[12]));
        assertFalse(GXGuestVulkanLayerInstaller.isLinuxX86_64Elf(null));
    }
}
