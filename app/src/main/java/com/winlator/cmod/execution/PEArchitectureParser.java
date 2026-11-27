package com.winlator.cmod.execution;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Bounded, read-only PE/COFF metadata parser; it never maps or executes the selected file. */
public final class PEArchitectureParser {
    public enum Architecture { X86, X86_64, ARM64, ARM64EC, UNKNOWN }
    private static final int MAX_SECTIONS = 96;
    private static final int MAX_IMPORT_DESCRIPTORS = 256;
    private static final int MAX_IMPORT_NAME_BYTES = 256;

    public static final class Result {
        public final int machine;
        public final Architecture architecture;
        public final int subsystem;
        public final boolean dynamicLibrary;
        public final List<String> importedLibraries;

        private Result(int machine, Architecture architecture, int subsystem,
                       boolean dynamicLibrary, List<String> importedLibraries) {
            this.machine = machine;
            this.architecture = architecture;
            this.subsystem = subsystem;
            this.dynamicLibrary = dynamicLibrary;
            this.importedLibraries = Collections.unmodifiableList(new ArrayList<>(importedLibraries));
        }
    }

    private static final class Section {
        final long virtualAddress, virtualSize, rawOffset, rawSize;
        Section(long virtualAddress, long virtualSize, long rawOffset, long rawSize) {
            this.virtualAddress = virtualAddress;
            this.virtualSize = virtualSize;
            this.rawOffset = rawOffset;
            this.rawSize = rawSize;
        }
    }

    private PEArchitectureParser() {}

    public static Architecture architectureForMachine(int machine) {
        switch (machine & 0xffff) {
            case 0x014c: return Architecture.X86;
            case 0x8664: return Architecture.X86_64;
            case 0xaa64: return Architecture.ARM64;
            case 0xa641: return Architecture.ARM64EC;
            default: return Architecture.UNKNOWN;
        }
    }

    public static Result parse(File file) throws IOException {
        if (file == null || !file.isFile() || !file.canRead()) {
            throw new IOException("PE file is missing or unreadable");
        }
        try (RandomAccessFile input = new RandomAccessFile(file, "r")) {
            long length = input.length();
            if (length < 64) throw new IOException("File is too small to contain a DOS header");
            byte[] dos = readAt(input, 0, 64);
            if (dos[0] != 'M' || dos[1] != 'Z') throw new IOException("Missing MZ signature");
            long peOffset = uint32(dos, 0x3c);
            if (peOffset < 64 || peOffset > length - 24) throw new IOException("PE header offset is outside the file");

            byte[] coff = readAt(input, peOffset, 24);
            if (coff[0] != 'P' || coff[1] != 'E' || coff[2] != 0 || coff[3] != 0) {
                throw new IOException("Missing PE signature");
            }
            int machine = uint16(coff, 4);
            int sectionCount = uint16(coff, 6);
            int optionalSize = uint16(coff, 20);
            int characteristics = uint16(coff, 22);
            if (sectionCount > MAX_SECTIONS) throw new IOException("Unreasonable PE section count");

            long optionalOffset = peOffset + 24;
            long sectionOffset = optionalOffset + optionalSize;
            if (sectionOffset > length) throw new IOException("Optional header is truncated");

            int subsystem = 0;
            long sizeOfHeaders = 0, importRva = 0, importSize = 0;
            if (optionalSize >= 70 && optionalOffset + optionalSize <= length) {
                byte[] optional = readAt(input, optionalOffset, optionalSize);
                int magic = uint16(optional, 0);
                int directoriesOffset = magic == 0x10b ? 96 : magic == 0x20b ? 112 : -1;
                int directoryCountOffset = magic == 0x10b ? 92 : magic == 0x20b ? 108 : -1;
                subsystem = uint16(optional, 68);
                sizeOfHeaders = uint32(optional, 60);
                if (directoriesOffset >= 0 && optional.length >= directoriesOffset + 16
                        && uint32(optional, directoryCountOffset) > 1) {
                    importRva = uint32(optional, directoriesOffset + 8);
                    importSize = uint32(optional, directoriesOffset + 12);
                }
            }

            ArrayList<Section> sections = new ArrayList<>();
            long tableBytes = (long) sectionCount * 40L;
            if (sectionOffset + tableBytes > length) throw new IOException("PE section table is truncated");
            if (sectionCount > 0) {
                byte[] table = readAt(input, sectionOffset, (int) tableBytes);
                for (int i = 0; i < sectionCount; i++) {
                    int at = i * 40;
                    sections.add(new Section(uint32(table, at + 12), uint32(table, at + 8),
                            uint32(table, at + 20), uint32(table, at + 16)));
                }
            }

            ArrayList<String> imports = new ArrayList<>();
            if (importRva > 0 && !sections.isEmpty()) {
                readImportNames(input, length, importRva, importSize, sizeOfHeaders, sections, imports);
            }
            return new Result(machine, architectureForMachine(machine), subsystem,
                    (characteristics & 0x2000) != 0, imports);
        }
    }

    private static void readImportNames(RandomAccessFile input, long length, long importRva,
                                        long importSize, long sizeOfHeaders, List<Section> sections,
                                        List<String> imports) throws IOException {
        long limit = importSize > 0 ? Math.min(importSize / 20L, MAX_IMPORT_DESCRIPTORS)
                : MAX_IMPORT_DESCRIPTORS;
        for (long i = 0; i < limit; i++) {
            long at = mapRva(importRva + i * 20L, length, sizeOfHeaders, sections);
            if (at < 0 || at > length - 20) break;
            byte[] descriptor = readAt(input, at, 20);
            long nonzero = uint32(descriptor, 0) | uint32(descriptor, 4) | uint32(descriptor, 8)
                    | uint32(descriptor, 12) | uint32(descriptor, 16);
            if (nonzero == 0) break;
            long nameRva = uint32(descriptor, 12);
            if (nameRva == 0) continue;
            long nameOffset = mapRva(nameRva, length, sizeOfHeaders, sections);
            if (nameOffset < 0 || nameOffset >= length) continue;
            int count = (int) Math.min(MAX_IMPORT_NAME_BYTES, length - nameOffset);
            String name = readAsciiCString(readAt(input, nameOffset, count));
            if (!name.isEmpty() && !imports.contains(name)) imports.add(name);
        }
    }

    private static long mapRva(long rva, long length, long sizeOfHeaders, List<Section> sections) {
        if (rva < 0) return -1;
        if (rva < sizeOfHeaders && rva < length) return rva;
        for (Section s : sections) {
            long span = Math.max(s.virtualSize, s.rawSize);
            if (rva >= s.virtualAddress && rva - s.virtualAddress < span) {
                long delta = rva - s.virtualAddress;
                if (delta >= s.rawSize) return -1;
                long offset = s.rawOffset + delta;
                return offset < length ? offset : -1;
            }
        }
        return -1;
    }

    private static String readAsciiCString(byte[] bytes) {
        int count = 0;
        while (count < bytes.length && bytes[count] != 0) count++;
        return new String(bytes, 0, count, java.nio.charset.StandardCharsets.US_ASCII).trim();
    }

    private static byte[] readAt(RandomAccessFile input, long offset, int size) throws IOException {
        if (offset < 0 || size < 0 || offset > input.length() - size) {
            throw new IOException("PE data range is outside the file");
        }
        byte[] result = new byte[size];
        input.seek(offset);
        input.readFully(result);
        return result;
    }

    private static int uint16(byte[] b, int p) {
        return (b[p] & 0xff) | ((b[p + 1] & 0xff) << 8);
    }

    private static long uint32(byte[] b, int p) {
        return ((long) b[p] & 0xffL) | (((long) b[p + 1] & 0xffL) << 8)
                | (((long) b[p + 2] & 0xffL) << 16) | (((long) b[p + 3] & 0xffL) << 24);
    }
}
