package com.winlator.cmod.execution;

import org.junit.Test;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

public class PEArchitectureParserTest {
    @Test
    public void mapsKnownMachineTypes() {
        assertEquals(PEArchitectureParser.Architecture.X86, PEArchitectureParser.architectureForMachine(0x014c));
        assertEquals(PEArchitectureParser.Architecture.X86_64, PEArchitectureParser.architectureForMachine(0x8664));
        assertEquals(PEArchitectureParser.Architecture.ARM64, PEArchitectureParser.architectureForMachine(0xaa64));
        assertEquals(PEArchitectureParser.Architecture.ARM64EC, PEArchitectureParser.architectureForMachine(0xa641));
        assertEquals(PEArchitectureParser.Architecture.UNKNOWN, PEArchitectureParser.architectureForMachine(0xffff));
    }

    @Test
    public void parsesBoundedPeCoffHeader() throws Exception {
        File file = createMinimalPe(0x8664, 0x20b);
        try {
            PEArchitectureParser.Result result = PEArchitectureParser.parse(file);
            assertEquals(PEArchitectureParser.Architecture.X86_64, result.architecture);
            assertEquals(0x8664, result.machine);
            assertFalse(result.dynamicLibrary);
        } finally {
            Files.deleteIfExists(file.toPath());
        }
    }

    @Test
    public void extractsImportedDllNamesThroughSectionRvaMapping() throws Exception {
        File file = createPeWithD3d11Import();
        try {
            PEArchitectureParser.Result result = PEArchitectureParser.parse(file);
            assertEquals(java.util.Collections.singletonList("d3d11.dll"), result.importedLibraries);
        } finally {
            Files.deleteIfExists(file.toPath());
        }
    }

    @Test
    public void rejectsFilesWithoutDosSignature() throws Exception {
        File file = Files.createTempFile("not-pe", ".bin").toFile();
        try {
            try (RandomAccessFile output = new RandomAccessFile(file, "rw")) {
                output.write(new byte[128]);
            }
            PEArchitectureParser.parse(file);
            fail("Expected invalid PE to be rejected");
        } catch (IOException expected) {
            // Invalid input is never treated as a PE executable.
        } finally {
            Files.deleteIfExists(file.toPath());
        }
    }

    private static File createPeWithD3d11Import() throws Exception {
        File file = Files.createTempFile("pe-import", ".exe").toFile();
        byte[] data = new byte[0x400];
        data[0] = 'M';
        data[1] = 'Z';
        put32(data, 0x3c, 0x80);
        data[0x80] = 'P';
        data[0x81] = 'E';
        put16(data, 0x84, 0x8664);
        put16(data, 0x86, 1);
        put16(data, 0x94, 240);
        put16(data, 0x96, 0x0002);

        int optional = 0x98;
        put16(data, optional, 0x20b);
        put32(data, optional + 60, 0x200);
        put32(data, optional + 108, 16);
        put32(data, optional + 120, 0x1000);
        put32(data, optional + 124, 40);

        int section = optional + 240;
        put32(data, section + 8, 0x200);
        put32(data, section + 12, 0x1000);
        put32(data, section + 16, 0x200);
        put32(data, section + 20, 0x200);

        put32(data, 0x200, 0x1040);
        put32(data, 0x20c, 0x1050);
        put32(data, 0x210, 0x1040);
        byte[] dllName = "d3d11.dll".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        System.arraycopy(dllName, 0, data, 0x250, dllName.length);
        data[0x250 + dllName.length] = 0;
        try (RandomAccessFile output = new RandomAccessFile(file, "rw")) {
            output.write(data);
        }
        return file;
    }

    private static File createMinimalPe(int machine, int optionalMagic) throws Exception {
        File file = Files.createTempFile("minimal-pe", ".exe").toFile();
        byte[] data = new byte[256];
        data[0] = 'M';
        data[1] = 'Z';
        put32(data, 0x3c, 0x80);
        data[0x80] = 'P';
        data[0x81] = 'E';
        put16(data, 0x84, machine);
        put16(data, 0x86, 0);
        put16(data, 0x94, 70);
        put16(data, 0x96, 0x0002);
        put16(data, 0x98, optionalMagic);
        try (RandomAccessFile output = new RandomAccessFile(file, "rw")) {
            output.write(data);
        }
        return file;
    }

    private static void put16(byte[] data, int offset, int value) {
        data[offset] = (byte) (value & 0xff);
        data[offset + 1] = (byte) ((value >>> 8) & 0xff);
    }

    private static void put32(byte[] data, int offset, int value) {
        data[offset] = (byte) (value & 0xff);
        data[offset + 1] = (byte) ((value >>> 8) & 0xff);
        data[offset + 2] = (byte) ((value >>> 16) & 0xff);
        data[offset + 3] = (byte) ((value >>> 24) & 0xff);
    }
}
