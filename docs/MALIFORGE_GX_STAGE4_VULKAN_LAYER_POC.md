# MaliForge GX Stage 4 — Guest Vulkan Diagnostics Layer

## Status and scope

**Experimental — host software-Vulkan smoke test required; Android/Wine game runtime is not yet device-verified.**

This milestone adds a pass-through Vulkan diagnostic layer for the Linux x86_64 guest process. It is not a Vulkan ICD, graphics driver, renderer, DXVK replacement, or GPU optimization. It forwards calls to the next Vulkan layer/driver and records selected instance/device lifecycle information.

## Architecture and ABI boundary

- Android application/native libraries remain ARM64/AArch64.
- The packaged MaliForge diagnostic layer is a Linux ELF64 x86_64 object because this is the architecture of the supported Wine guest process.
- The x86_64 layer is bundled as an Android APK asset and installed into the guest ImageFS. It is not installed into Android's lib/arm64-v8a directory.
- The existing guest Vulkan loader and selected ICD/driver remain responsible for actual rendering.
- No graphics-driver archive, Vulkan ICD JSON, DXVK, VKD3D, WineD3D, Wrapper, EGL compositor, or Android system driver is replaced.

## Activation and recovery

Activation is opt-in through the per-container environment request MALIFORGE_GX_VULKAN_LAYER=1. The launcher consumes that request, checks that the Wine guest architecture is exactly x86_64, installs only MaliForge-owned files, and appends the manifest directory to VK_ADD_IMPLICIT_LAYER_PATH. It does not set VK_INSTANCE_LAYERS, VK_LAYER_PATH, or VK_ICD_FILENAMES in the game environment. An explicit MALIFORGE_GX_VULKAN_LAYER_DISABLE=1 prevents activation. If installation or validation fails, the request is not re-enabled and the original graphics path is left alone.

The layer manifest declares matching enable_environment and disable_environment keys so that the layer is not loaded merely because its manifest exists.

## Host validation

CI installs Vulkan loader headers and Mesa software Vulkan drivers, compiles the layer as ELF64 x86_64, validates loader entry points, and runs an actual Vulkan instance/device smoke test on Mesa lavapipe. The test requires layer log events for instance creation, physical-device enumeration, device creation, and shutdown.

This proves discovery and Vulkan calls in the CI host software-Vulkan environment. It does not prove Android ImageFS installation, Wine guest integration, a particular Windows game's Vulkan path, Mali-G57 behavior, or an FPS improvement.

## Known limitations

- Only x86_64 Wine guests are eligible; ARM64/ARM64EC/32-bit guests are not activated.
- Layer diagnostics are observational and do not modify rendering state or choose another ICD.
- Physical-device identification depends on properties exposed by the selected driver.
- No game-renderer frame-time hook or benchmark is included.
- No Helio G100/Mali-G57 hardware testing has been performed.
- Vulkan loader path environment variables may be ignored by elevated-privilege processes; in that case the layer remains unavailable and the original rendering path remains in place.

## Recovery

Disable the per-container request or set MALIFORGE_GX_VULKAN_LAYER_DISABLE=1. The launcher removes the request before normal guest startup unless installation and activation checks succeed. The installer writes only MaliForge-owned layer and manifest files under ImageFS. It does not edit driver selectors, ICD manifests, or system Vulkan paths.