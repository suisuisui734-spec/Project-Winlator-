package com.winlator.cmod.maliforge.gx;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Inspects an installed third-party PanVK package as an integration candidate.
 *
 * This class never loads a driver, configures a container, or promotes the package to
 * MaliForge GX. A structurally valid package is only an external candidate; runtime
 * initialization and guest rendering still require separate verification.
 */
public final class GXExternalPanVKCandidate {
    public enum Status {
        NOT_INSTALLED,
        INVALID_PACKAGE,
        EXTERNAL_CANDIDATE
    }

    private final Status status;
    private final String name;
    private final String version;
    private final String libraryPath;
    private final String diagnostic;

    private GXExternalPanVKCandidate(Status status, String name, String version,
                                    String libraryPath, String diagnostic) {
        this.status = status;
        this.name = name;
        this.version = version;
        this.libraryPath = libraryPath;
        this.diagnostic = diagnostic;
    }

    public static GXExternalPanVKCandidate inspect(File packageDirectory) {
        if (packageDirectory == null || !packageDirectory.isDirectory()) {
            return new GXExternalPanVKCandidate(Status.NOT_INSTALLED, "", "", "",
                    "No imported external PanVK package directory was found.");
        }
        File metadataFile = new File(packageDirectory, "meta.json");
        if (!metadataFile.isFile()) {
            return invalid("Package metadata meta.json is missing.");
        }
        try {
            if (metadataFile.length() <= 0 || metadataFile.length() > 64 * 1024) {
                return invalid("Package metadata size is invalid.");
            }
            byte[] bytes = new byte[(int) metadataFile.length()];
            try (InputStream input = new FileInputStream(metadataFile)) {
                int offset = 0;
                while (offset < bytes.length) {
                    int count = input.read(bytes, offset, bytes.length - offset);
                    if (count < 0) return invalid("Package metadata could not be read completely.");
                    offset += count;
                }
            }
            JSONObject metadata = new JSONObject(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
            String name = metadata.optString("name", "").trim();
            String version = metadata.optString("driverVersion", "").trim();
            String libraryName = metadata.optString("libraryName", "").trim();
            if (name.isEmpty() || version.isEmpty() || libraryName.isEmpty()) {
                return invalid("Package metadata must define name, driverVersion, and libraryName.");
            }
            if (!name.toLowerCase(java.util.Locale.ROOT).contains("panvk")
                    && !libraryName.toLowerCase(java.util.Locale.ROOT).contains("panvk")) {
                return invalid("Imported package metadata does not identify PanVK.");
            }
            if (!isSafeRelativeLibraryPath(libraryName)) {
                return invalid("The library path is unsafe or not a shared library path.");
            }
            File library = new File(packageDirectory, libraryName).getCanonicalFile();
            String root = packageDirectory.getCanonicalPath() + File.separator;
            if (!library.getPath().startsWith(root)) {
                return invalid("The library path escapes the installed package.");
            }
            if (!isElf64Aarch64(library)) {
                return invalid("The selected library is not a valid ELF64 AArch64 shared library.");
            }
            return new GXExternalPanVKCandidate(Status.EXTERNAL_CANDIDATE, name, version,
                    library.getAbsolutePath(),
                    "External PanVK package structure and ARM64 ELF header validated. This is not runtime or guest-rendering verification.");
        } catch (IOException | JSONException | RuntimeException exception) {
            return invalid("Package inspection failed: " + exception.getClass().getSimpleName()
                    + (exception.getMessage() == null ? "" : " — " + exception.getMessage()));
        }
    }

    private static GXExternalPanVKCandidate invalid(String diagnostic) {
        return new GXExternalPanVKCandidate(Status.INVALID_PACKAGE, "", "", "", diagnostic);
    }

    static boolean isSafeRelativeLibraryPath(String path) {
        if (path == null || path.isEmpty() || path.length() > 160
                || path.startsWith("/") || path.indexOf('\\') >= 0
                || path.matches("^[A-Za-z]:.*")) return false;
        for (String segment : path.split("/", -1)) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)
                    || segment.indexOf(':') >= 0) return false;
        }
        return path.toLowerCase(java.util.Locale.ROOT).endsWith(".so");
    }

    static boolean isElf64Aarch64(File file) throws IOException {
        if (file == null || !file.isFile() || file.length() < 20) return false;
        byte[] header = new byte[20];
        try (InputStream input = new FileInputStream(file)) {
            int offset = 0;
            while (offset < header.length) {
                int count = input.read(header, offset, header.length - offset);
                if (count < 0) return false;
                offset += count;
            }
        }
        return (header[0] & 0xff) == 0x7f && header[1] == 'E' && header[2] == 'L'
                && header[3] == 'F' && (header[4] & 0xff) == 2 && (header[5] & 0xff) == 1
                && (header[16] & 0xff) == 3 && (header[18] & 0xff) == 0xb7
                && (header[19] & 0xff) == 0;
    }

    public Status getStatus() { return status; }
    public String getName() { return name; }
    public String getVersion() { return version; }
    public String getLibraryPath() { return libraryPath; }
    public String getDiagnostic() { return diagnostic; }
    public boolean isExternalCandidate() { return status == Status.EXTERNAL_CANDIDATE; }
}
