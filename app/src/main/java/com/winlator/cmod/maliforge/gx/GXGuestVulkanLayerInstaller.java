package com.winlator.cmod.maliforge.gx;

import android.content.Context;

import com.winlator.cmod.core.FileUtils;
import com.winlator.cmod.xenvironment.ImageFs;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/** Installs only MaliForge-owned guest layer files under ImageFS; no Vulkan driver files are touched. */
public final class GXGuestVulkanLayerInstaller {
    public static final String ASSET_LIBRARY = "maliforge-gx/libmaliforge_gx_layer_x86_64.so";
    public static final String LIBRARY_NAME = "libmaliforge_gx_layer_x86_64.so";
    public static final String MANIFEST_NAME = "MaliForge-GX.json";

    public InstallResult install(Context context, ImageFs imageFs) {
        if (context == null || imageFs == null) {
            return InstallResult.failure("Android context or ImageFS is unavailable.");
        }

        File root = imageFs.getRootDir();
        File library = new File(imageFs.getLibDir(), LIBRARY_NAME);
        File manifestDirectory = new File(imageFs.getShareDir(), "vulkan/implicit_layer.d");
        File manifest = new File(manifestDirectory, MANIFEST_NAME);
        File logDirectory = new File(root, "usr/var/log/maliforge-gx");
        File log = new File(logDirectory, "vulkan-layer.log");

        File libraryTemp = new File(library.getParentFile(), LIBRARY_NAME + ".tmp");
        File manifestTemp = new File(manifestDirectory, MANIFEST_NAME + ".tmp");
        try {
            if (!imageFs.getLibDir().exists() && !imageFs.getLibDir().mkdirs()) {
                return InstallResult.failure("Unable to create the guest library directory.");
            }
            if (!manifestDirectory.exists() && !manifestDirectory.mkdirs()) {
                return InstallResult.failure("Unable to create the Vulkan implicit-layer manifest directory.");
            }
            if (!logDirectory.exists() && !logDirectory.mkdirs()) {
                return InstallResult.failure("Unable to create the MaliForge diagnostics log directory.");
            }

            copyAsset(context, libraryTemp);
            if (!isLinuxX86_64Elf(libraryTemp)) {
                libraryTemp.delete();
                return InstallResult.failure("Bundled Vulkan layer is not a Linux x86_64 ELF64 library.");
            }
            replaceFile(libraryTemp, library);
            FileUtils.chmod(library, 0755);

            String json = createManifest(library.getAbsolutePath());
            if (!manifest.isFile() || !json.equals(new String(Files.readAllBytes(manifest.toPath()),
                        java.nio.charset.StandardCharsets.UTF_8))) {
                writeSynced(manifestTemp, json);
                replaceFile(manifestTemp, manifest);
            }

            if (!library.isFile() || !manifest.isFile()) {
                return InstallResult.failure("Vulkan layer installation could not be verified.");
            }
            return InstallResult.success(manifestDirectory, log, library);
        } catch (IOException | JSONException exception) {
            libraryTemp.delete();
            manifestTemp.delete();
            return InstallResult.failure(exception.getClass().getSimpleName() + ": "
                    + (exception.getMessage() == null ? "No details" : exception.getMessage()));
        }
    }

    private static void copyAsset(Context context, File destination) throws IOException {
        try (InputStream input = context.getAssets().open(ASSET_LIBRARY);
             FileOutputStream output = new FileOutputStream(destination, false)) {
            byte[] buffer = new byte[16 * 1024];
            int length;
            while ((length = input.read(buffer)) != -1) output.write(buffer, 0, length);
            output.flush();
            output.getFD().sync();
        }
    }

    private static boolean isLinuxX86_64Elf(File file) throws IOException {
        byte[] header = new byte[20];
        try (InputStream input = Files.newInputStream(file.toPath())) {
            int offset = 0;
            while (offset < header.length) {
                int count = input.read(header, offset, header.length - offset);
                if (count < 0) return false;
                offset += count;
            }
        }
        // ELF64, little endian, e_machine = EM_X86_64 (62).
        return isLinuxX86_64Elf(header);
    }

    static boolean isLinuxX86_64Elf(byte[] header) {
        return header != null && header.length >= 20
                && (header[0] & 0xff) == 0x7f && header[1] == 'E' && header[2] == 'L'
                && header[3] == 'F' && (header[4] & 0xff) == 2
                && (header[5] & 0xff) == 1
                && (header[18] & 0xff) == 0x3e && (header[19] & 0xff) == 0x00;
    }

    private static String createManifest(String libraryPath) throws JSONException {
        JSONObject layer = new JSONObject();
        layer.put("name", GXGuestVulkanLayerConfig.LAYER_NAME);
        layer.put("type", "GLOBAL");
        layer.put("library_path", libraryPath);
        layer.put("library_arch", "64");
        layer.put("api_version", "1.3.0");
        layer.put("implementation_version", "1");
        layer.put("description", "MaliForge GX opt-in Vulkan pass-through runtime diagnostics");
        JSONObject enableEnvironment = new JSONObject();
        enableEnvironment.put(GXGuestVulkanLayerConfig.ENABLE_ENV, "1");
        layer.put("enable_environment", enableEnvironment);
        JSONObject disableEnvironment = new JSONObject();
        disableEnvironment.put(GXGuestVulkanLayerConfig.DISABLE_ENV, "1");
        layer.put("disable_environment", disableEnvironment);

        JSONObject manifest = new JSONObject();
        manifest.put("file_format_version", "1.2.0");
        manifest.put("layer", layer);
        return manifest.toString(2);
    }

    private static void writeSynced(File destination, String contents) throws IOException {
        try (FileOutputStream output = new FileOutputStream(destination, false)) {
            output.write(contents.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            output.flush();
            output.getFD().sync();
        }
    }

    private static void replaceFile(File temporary, File destination) throws IOException {
        try {
            Files.move(temporary.toPath(), destination.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static final class InstallResult {
        public final boolean available;
        public final String reason;
        public final File manifestDirectory;
        public final File logFile;
        public final File libraryFile;

        private InstallResult(boolean available, String reason, File manifestDirectory,
                              File logFile, File libraryFile) {
            this.available = available;
            this.reason = reason == null ? "" : reason;
            this.manifestDirectory = manifestDirectory;
            this.logFile = logFile;
            this.libraryFile = libraryFile;
        }

        private static InstallResult success(File manifestDirectory, File logFile, File libraryFile) {
            return new InstallResult(true, "Guest Vulkan layer installed.", manifestDirectory, logFile, libraryFile);
        }

        private static InstallResult failure(String reason) {
            return new InstallResult(false, reason, null, null, null);
        }
    }
}
