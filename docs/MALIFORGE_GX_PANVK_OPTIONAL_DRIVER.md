# Optional PanVK Vulkan Driver for Mali

**Reviewed:** 2026-11-27

## What this adds

The Adrenotools screen now includes a link to the upstream PanVK release page. Users can download an upstream ZIP themselves and import it through the existing **Install Drivers** action.

This is an optional third-party Vulkan driver package, not the MaliForge GX native renderer. It is not bundled into the APK, it does not replace any bundled Winlator wrapper, and it does not make the **MaliForge GX (Unavailable)** entry selectable.

Imported Adrenotools drivers remain stored independently under the app's private `contents/adrenotools` directory. They are selected as a custom Vulkan driver version for an individual container through Graphics Driver Configuration. The container's existing System option and other driver choices remain available.

## Upstream project

- Project: https://github.com/JimVulkan/mali-panvk
- Official releases: https://github.com/JimVulkan/mali-panvk/releases
- Release reviewed: v1.6.0 / r10, based on Mesa 27.0.0-devel
- Release archive: `mali-panvk-27.0.0-devel-r10.zip`
- Published SHA-256: `7e97db4d79d81ec9461d1e0676b62a865561cb56e21c151693aa25ffbe749f1c`
- Requirements stated upstream: Android 12 (API 31) or newer and 64-bit ARM.

The upstream support table lists Mali-G57 among its supported Valhall GPUs. The previous r9 release notes list a Galaxy A15 with Mali-G57 MC2 and MediaTek Helio G99 in its tested-device table; the r10 release notes say the r9 device list remains the reference set, while r10-specific changes were checked on a Galaxy S10e (Mali-G76) and Galaxy Tab S10 Ultra (Immortalis-G720). This is upstream-reported compatibility, not a test of every Mali-G57 device or kernel.

## User installation

1. Open **Adrenotools GPU Drivers** and use **View official PanVK releases** to inspect the upstream release and its compatibility notes.
2. Download the official ARM64 ZIP from the upstream release page.
3. Return to Winlator, choose **Install Drivers**, and select that ZIP.
4. Open the target container's **Graphics Driver Configuration** and select the imported custom driver version.
5. Keep **System** or another known-working driver available as a fallback. Remove an older PanVK entry before importing a release that uses the same driver name.

The importer validates ZIP paths, limits the number of entries and expanded size, checks the root metadata, prevents folder-name collisions, and requires the selected library to be a 64-bit little-endian AArch64 ELF shared library. This validates package structure and architecture; it does not prove that the driver initializes correctly on the current device.

## Important limitations

- PanVK is experimental third-party software. The upstream project is not affiliated with this Winlator fork, Arm, MediaTek, or MaliForge GX.
- A supported GPU name does not guarantee compatibility with a particular phone's kernel, Android build, window system, Vulkan wrapper, or game.
- The upstream release documents Direct3D 12 limits on Mali-G57 (feature level up to 11_1), no mesh shaders on Bifrost/Valhall hardware, and other feature limitations.
- No FPS increase, crash-free operation, or game compatibility is guaranteed. Compare behavior with the System driver and revert the container setting if a game fails to start or renders incorrectly.
- The current MaliForge GX native renderer remains unavailable until this project supplies and validates its own renderer/ICD and runtime initialization path.

## Licensing

No PanVK binary is bundled or redistributed by this change. The upstream project states that its changes are MIT-licensed, Mesa source files retain their own licenses, and the distributed package includes a `NOTICE.txt`. Keep upstream notices with any separately downloaded package.
