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
