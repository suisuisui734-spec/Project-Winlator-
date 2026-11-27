package com.winlator.cmod.contents;

import android.content.res.AssetManager;
import android.net.Uri;

import android.content.Context;
import android.util.Log;

import com.winlator.cmod.SettingsFragment;
import com.winlator.cmod.container.Container;
import com.winlator.cmod.container.Shortcut;
import com.winlator.cmod.container.ContainerManager;
import com.winlator.cmod.core.DefaultVersion;
import com.winlator.cmod.core.EnvVars;
import com.winlator.cmod.core.FileUtils;
import com.winlator.cmod.core.GPUInformation;
import com.winlator.cmod.contentdialog.GraphicsDriverConfigDialog;
import com.winlator.cmod.core.TarCompressorUtils;
import com.winlator.cmod.xenvironment.ImageFs;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.json.JSONException;
import org.json.JSONObject;

public class AdrenotoolsManager {
    
    private File adrenotoolsContentDir;
    private Context mContext;
    
    public AdrenotoolsManager(Context context) {
        this.mContext = context;
        this.adrenotoolsContentDir = new File(mContext.getFilesDir(), "contents/adrenotools");
        if (!adrenotoolsContentDir.exists())
            adrenotoolsContentDir.mkdirs();
    }
        
    public String getLibraryName(String adrenoToolsDriverId) {
        String libraryName = "";
        File driverPath = new File(adrenotoolsContentDir, adrenoToolsDriverId);
        try {
            File metaProfile = new File(driverPath, "meta.json");
            JSONObject jsonObject = new JSONObject(FileUtils.readString(metaProfile));
            libraryName = jsonObject.getString("libraryName");
        }
        catch (JSONException e) {
        }
        return libraryName;
    }
    
    public String getDriverName(String adrenoToolsDriverId) {
        String driverName = "";
        File driverPath = new File(adrenotoolsContentDir, adrenoToolsDriverId);
        try {
            File metaProfile = new File(driverPath, "meta.json");
            JSONObject jsonObject = new JSONObject(FileUtils.readString(metaProfile));
            driverName = jsonObject.getString("name");
        }
        catch (JSONException e) {
        }
        return driverName;
    }

    public String getDriverVersion(String adrenoToolsDriverId) {
        String driverVersion = "";
        File driverPath = new File(adrenotoolsContentDir, adrenoToolsDriverId);
        try {
            File metaProfile = new File(driverPath, "meta.json");
            JSONObject jsonObject = new JSONObject(FileUtils.readString(metaProfile));
            driverVersion = jsonObject.getString("driverVersion");
        }
        catch (JSONException e) {
        }
        return driverVersion;
    }

    public String getDriverPath(String adrenotoolsDriverId) {
        return adrenotoolsContentDir.getAbsolutePath() + "/" + adrenotoolsDriverId + "/";
    }

    private void reloadContainers(String adrenoToolsDriverId) {
        ContainerManager containerManager = new ContainerManager(mContext);
        for (Container container : containerManager.getContainers()) {
            HashMap<String, String> config = GraphicsDriverConfigDialog.parseGraphicsDriverConfig(container.getGraphicsDriverConfig());
            Log.d("AdrenotoolsManager", "Checking if container driver version " + config.get("version") + " matches " + getDriverName(adrenoToolsDriverId));
            if (config.get("version").contains(getDriverName(adrenoToolsDriverId))) {
                Log.d("AdrenotoolsManager", "Found a match for container " + container.getName());
                config.put("version", GPUInformation.isDriverSupported(DefaultVersion.WRAPPER_ADRENO, mContext) ? DefaultVersion.WRAPPER_ADRENO : DefaultVersion.WRAPPER);
                container.setGraphicsDriverConfig(GraphicsDriverConfigDialog.toGraphicsDriverConfig(config));
                container.saveData();
            }     
        }
        for (Shortcut shortcut : containerManager.loadShortcuts()) {
            HashMap<String, String> config = GraphicsDriverConfigDialog.parseGraphicsDriverConfig(shortcut.getExtra("graphicsDriverConfig", shortcut.container.getGraphicsDriverConfig()));
            Log.d("AdrenotoolsManager", "Checking if shortcut driver version " + config.get("version") + " matches " + getDriverName(adrenoToolsDriverId));
            if (config.get("version").contains(getDriverName(adrenoToolsDriverId))) {
                Log.d("AdrenotoolsManager", "Found a match for shortcut " + shortcut.name);
                config.put("version", GPUInformation.isDriverSupported(DefaultVersion.WRAPPER_ADRENO, mContext) ? DefaultVersion.WRAPPER_ADRENO : DefaultVersion.WRAPPER);
                shortcut.putExtra("graphicsDriverConfig", GraphicsDriverConfigDialog.toGraphicsDriverConfig(config));
                shortcut.saveData();
            }
        }
    }
    
    public void removeDriver(String adrenoToolsDriverId) {
        Log.d("AdrenotoolsManager", "Removing driver " + adrenoToolsDriverId);
        File driverPath = new File(adrenotoolsContentDir, adrenoToolsDriverId);
        reloadContainers(adrenoToolsDriverId);
        FileUtils.delete(driverPath);
    }

    public ArrayList<String> enumarateInstalledDrivers() {
        ArrayList<String> driversList = new ArrayList<>();
        
        for (File f : adrenotoolsContentDir.listFiles()) {
            boolean fromResources = isFromResources(f.getName());
            if (!fromResources && new File(f, "meta.json").exists())
                driversList.add(f.getName());
        }
        return driversList;
    }
    
    public boolean isFromResources(String adrenotoolsDriverId) {
        String driver = "graphics_driver/adrenotools-" + adrenotoolsDriverId + ".tzst";
        AssetManager am = mContext.getResources().getAssets();
        InputStream is = null;
        boolean isFromResources = true;
        
        try {
            is = am.open(driver);
            is.close();
        }
        catch (IOException e) {
            isFromResources = false;
        }
        
        return isFromResources;
    }
        
    public boolean extractDriverFromResources(String adrenotoolsDriverId) {
        String src = "graphics_driver/adrenotools-" + adrenotoolsDriverId + ".tzst";
        boolean hasExtracted;

        File dst = new File(adrenotoolsContentDir, adrenotoolsDriverId);
        if (dst.exists())
            return true;

        dst.mkdirs();
        Log.d("AdrenotoolsManager", "Extracting " + src + " to " + dst.getAbsolutePath());
        hasExtracted = TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, mContext, src, dst);

        if (!hasExtracted)
            dst.delete();

        return hasExtracted;
    }
    
    public String installDriver(Uri driverUri) {
        if (driverUri == null) return "";
        try {
            InputStream input = mContext.getContentResolver().openInputStream(driverUri);
            return input == null ? "" : installDriver(input);
        } catch (IOException | RuntimeException exception) {
            Log.w("AdrenotoolsManager", "Failed to open selected driver archive.", exception);
            return "";
        }
    }

    /**
     * Installs a custom Vulkan driver ZIP without allowing archive paths to escape the private
     * driver directory. The archive must contain root meta.json and an ARM64 ELF shared library.
     */
    public String installDriver(InputStream input) {
        if (input == null) return "";

        File tmpDir = new File(adrenotoolsContentDir, "tmp");
        boolean installed = false;
        String installedName = "";

        try (InputStream source = input; ZipInputStream zis = new ZipInputStream(source)) {
            if (!adrenotoolsContentDir.isDirectory() && !adrenotoolsContentDir.mkdirs()) {
                throw new IOException("Unable to create the custom driver directory.");
            }
            if (tmpDir.exists()) FileUtils.delete(tmpDir);
            if (!tmpDir.isDirectory() && !tmpDir.mkdirs()) {
                throw new IOException("Unable to create the temporary driver directory.");
            }

            String canonicalRoot = tmpDir.getCanonicalPath() + File.separator;
            Set<String> extractedFiles = new HashSet<>();
            long totalBytes = 0L;
            int entryCount = 0;
            byte[] buffer = new byte[16 * 1024];
            ZipEntry entry;

            while ((entry = zis.getNextEntry()) != null) {
                if (++entryCount > AdrenotoolsArchiveSafety.MAX_ARCHIVE_ENTRIES) {
                    throw new IOException("Driver archive contains too many entries.");
                }
                if (entry.getSize() > AdrenotoolsArchiveSafety.MAX_UNCOMPRESSED_BYTES) {
                    throw new IOException("Driver archive entry exceeds the size limit.");
                }

                String relativeName = AdrenotoolsArchiveSafety.validateEntryName(entry.getName());
                File destination = new File(tmpDir, relativeName).getCanonicalFile();
                if (!destination.getPath().startsWith(canonicalRoot)) {
                    throw new IOException("Driver archive entry escapes its extraction directory.");
                }

                boolean directory = entry.isDirectory() || relativeName.endsWith("/");
                if (directory) {
                    if (!destination.isDirectory() && !destination.mkdirs()) {
                        throw new IOException("Unable to create an archive directory.");
                    }
                    zis.closeEntry();
                    continue;
                }

                File parent = destination.getParentFile();
                if (parent == null || (!parent.isDirectory() && !parent.mkdirs())) {
                    throw new IOException("Unable to create an archive entry directory.");
                }
                if (!extractedFiles.add(destination.getPath())) {
                    throw new IOException("Driver archive contains duplicate file entries.");
                }

                try (FileOutputStream output = new FileOutputStream(destination, false)) {
                    int length;
                    while ((length = zis.read(buffer)) != -1) {
                        totalBytes += length;
                        if (totalBytes > AdrenotoolsArchiveSafety.MAX_UNCOMPRESSED_BYTES) {
                            throw new IOException("Driver archive expands beyond the size limit.");
                        }
                        output.write(buffer, 0, length);
                    }
                    output.flush();
                    output.getFD().sync();
                }
                zis.closeEntry();
            }

            File metadataFile = new File(tmpDir, "meta.json");
            if (!metadataFile.isFile()
                    || metadataFile.length() <= 0
                    || metadataFile.length() > AdrenotoolsArchiveSafety.MAX_METADATA_BYTES) {
                throw new IOException("Driver archive is missing a valid root meta.json.");
            }

            byte[] metadataBytes = Files.readAllBytes(metadataFile.toPath());
            JSONObject metadata = new JSONObject(new String(metadataBytes, StandardCharsets.UTF_8));
            String driverName = metadata.optString("name", "").trim();
            String libraryName = metadata.optString("libraryName", "").trim();
            String driverVersion = metadata.optString("driverVersion", "").trim();

            if (!AdrenotoolsArchiveSafety.isSafeDriverDirectoryName(driverName)
                    || !AdrenotoolsArchiveSafety.isSafeLibraryRelativePath(libraryName)
                    || driverVersion.isEmpty() || driverVersion.length() > 160) {
                throw new IOException("Driver meta.json contains invalid name, libraryName or driverVersion fields.");
            }

            File library = new File(tmpDir, libraryName).getCanonicalFile();
            if (!library.getPath().startsWith(canonicalRoot)
                    || Files.isSymbolicLink(library.toPath())
                    || !AdrenotoolsArchiveSafety.isElf64Aarch64(library)) {
                throw new IOException("Driver library is missing or is not an ARM64 ELF64 shared library.");
            }

            File destination = new File(adrenotoolsContentDir, driverName);
            if (destination.exists()) {
                throw new IOException("A driver with this name is already installed. Remove the old entry first.");
            }

            // Move within the same private filesystem. Omitting REPLACE_EXISTING ensures
            // a concurrent or pre-existing driver directory cannot be silently overwritten.
            Files.move(tmpDir.toPath(), destination.toPath());

            installed = true;
            installedName = driverName;
            Log.i("AdrenotoolsManager", "Installed ARM64 Vulkan driver " + driverName
                    + " (" + driverVersion + ").");
        } catch (IOException | JSONException | RuntimeException exception) {
            Log.w("AdrenotoolsManager", "Rejected custom Vulkan driver archive: "
                    + exception.getMessage(), exception);
        } finally {
            if (!installed && tmpDir.exists()) FileUtils.delete(tmpDir);
        }

        return installedName;
    }

    public void setDriverById(EnvVars envVars, ImageFs imagefs, String adrenotoolsDriverId) {
        boolean isFromResources = isFromResources(adrenotoolsDriverId);

        if (isFromResources || enumarateInstalledDrivers().contains(adrenotoolsDriverId)) {
            String driverPath = getDriverPath(adrenotoolsDriverId);

            if (!getLibraryName(adrenotoolsDriverId).equals("")) {
                envVars.put("ADRENOTOOLS_DRIVER_PATH", driverPath);
                envVars.put("ADRENOTOOLS_HOOKS_PATH", imagefs.getLibDir());
                envVars.put("ADRENOTOOLS_DRIVER_NAME", getLibraryName(adrenotoolsDriverId));

                File winlatorDir = new File(SettingsFragment.DEFAULT_WINLATOR_PATH);
                File qglConfig = new File(winlatorDir, "qgl_config.txt");
                if (qglConfig.exists())
                    envVars.put("ADRENOTOOLS_REDIRECT_DIR", winlatorDir.getAbsolutePath() + "/");
            }
        }
    }
 }
