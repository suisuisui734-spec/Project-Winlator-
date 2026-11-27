package com.winlator.cmod.contents;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/** Pure validation helpers for third-party Adrenotools driver archives. */
public final class AdrenotoolsArchiveSafety {
    private AdrenotoolsArchiveSafety() {}

    public static final int MAX_ARCHIVE_ENTRIES = 2048;
    public static final long MAX_UNCOMPRESSED_BYTES = 512L * 1024L * 1024L;
    public static final long MAX_METADATA_BYTES = 64L * 1024L;

    /**
     * Validates a ZIP path before it is resolved below the temporary extraction directory.
     * Directory entries may end in one slash; absolute paths, drive prefixes, empty segments,
     * dot segments, backslashes and NUL characters are rejected.
     */
    public static String validateEntryName(String entryName) throws IOException {
        if (entryName == null || entryName.isEmpty() || entryName.indexOf('\0') >= 0
                || entryName.indexOf('\\') >= 0 || entryName.startsWith("/")
                || entryName.matches("^[A-Za-z]:.*")) {
            throw new IOException("Unsafe ZIP entry path.");
        }

        boolean directory = entryName.endsWith("/");
        String path = directory ? entryName.substring(0, entryName.length() - 1) : entryName;
        if (path.isEmpty()) throw new IOException("Empty ZIP entry path.");

        String[] segments = path.split("/", -1);
        for (String segment : segments) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)
                    || segment.indexOf(':') >= 0) {
                throw new IOException("Unsafe ZIP path segment.");
            }
        }
        return directory ? path + "/" : path;
    }

    public static boolean isSafeDriverDirectoryName(String name) {
        if (name == null || name.isEmpty() || name.length() > 96
                || !name.matches("^[A-Za-z0-9][A-Za-z0-9 ._()+-]*$")
                || name.endsWith(".") || name.endsWith(" ")) {
            return false;
        }
        return !".".equals(name) && !"..".equals(name)
                && !"tmp".equalsIgnoreCase(name)
                && !"system".equalsIgnoreCase(name);
    }

    public static boolean isSafeLibraryRelativePath(String libraryName) {
        if (libraryName == null || libraryName.isEmpty() || libraryName.length() > 160) return false;
        try {
            String path = validateEntryName(libraryName);
            return !path.endsWith("/") && path.toLowerCase(java.util.Locale.ROOT).endsWith(".so");
        } catch (IOException ignored) {
            return false;
        }
    }

    public static boolean isElf64Aarch64(File library) throws IOException {
        if (library == null || !library.isFile() || library.length() < 20) return false;
        byte[] header = new byte[20];
        try (InputStream input = new FileInputStream(library)) {
            int offset = 0;
            while (offset < header.length) {
                int count = input.read(header, offset, header.length - offset);
                if (count < 0) return false;
                offset += count;
            }
        }
        return isElf64Aarch64(header);
    }

    static boolean isElf64Aarch64(byte[] header) {
        return header != null && header.length >= 20
                && (header[0] & 0xff) == 0x7f
                && header[1] == 'E' && header[2] == 'L' && header[3] == 'F'
                && (header[4] & 0xff) == 2
                && (header[5] & 0xff) == 1
                && (header[18] & 0xff) == 0xb7
                && (header[19] & 0xff) == 0x00;
    }
}
