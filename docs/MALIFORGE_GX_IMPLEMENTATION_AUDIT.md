# MaliForge GX — Current Implementation Audit

**Audit date:** 2026-11-28  
**Repository:** \`suisuisui734-spec/Project-Winlator-\`  
**Audited base:** \`main\` at \`c2ead7fd104cc37edad26eee82886d1fba899b28\`  
**Active development:** PR [#42](https://github.com/suisuisui734-spec/Project-Winlator-/pull/42), branch \`feature/maliforge-gx-game-rendering-backend\`, head initially audited at \`6b7295f14949c599d24be94fd5a19ebbe515e5b1\`

## Executive result

**MaliForge GX is not a working Windows-game renderer and must remain unavailable/non-selectable.**

The repository has meaningful Vulkan work, but it is split into two different scopes:

1. An **Android-host Vulkan diagnostic runtime** in the ARM64 app process. It creates a Vulkan instance, enumerates a physical device, creates a logical device/graphics queue, and runs host-side command, image-readback, and offscreen render-pass tests.
2. An **x86_64 guest Vulkan diagnostic layer** installed into ImageFS. It forwards Vulkan calls to the existing implementation and records opt-in telemetry. It is not an ICD, not a driver, and does not translate or render guest frames.

Neither scope connects a Windows application's Vulkan/DXVK/VKD3D work to a MaliForge-owned renderer with a verified Android presentation path.

The active PR #42 adds a more complete **host device capability snapshot**. It explicitly does not add the guest renderer or make MaliForge GX selectable. PR #42 is the existing active MaliForge branch, so this audit and the identifier correction are being added there rather than creating a duplicate PR/branch.

## Repository and GitHub audit

- Default branch: \`main\`.
- Main contains the merged Stage 38–41 development and the previously merged selector/status work.
- One MaliForge PR was open at audit time: [PR #42 — Stage 42: expose native Vulkan device capabilities](https://github.com/suisuisui734-spec/Project-Winlator-/pull/42). It was a draft, based on \`main\`, and its scope was host Vulkan capability discovery.
- Main's latest observed CI run: [run 46082630612](https://github.com/suisuisui734-spec/Project-Winlator-/actions/runs/46082630612), completed successfully.
- PR #42's latest observed head CI run: [run 46088844611](https://github.com/suisuisui734-spec/Project-Winlator-/actions/runs/46088844611), completed successfully. The job reported success for source/CI validation, guest diagnostic-layer lavapipe smoke test, JVM unit tests, lint, Android native/Gradle APK build, package inspection, and artifact upload.
- The run uploaded \`project-winlator-standard-debug-stage23-validation\` (artifact ID \`15240870703\`, 446,961,051 bytes). This is a CI artifact archive; it is not evidence of a successful game-rendering test.
- CI uses Mesa lavapipe/software Vulkan for the guest-layer smoke test. It is not a Mali device test.

## Current architecture

### Android app and container configuration

- The Android application is built for \`arm64-v8a\`; the native app library is \`libwinlator.so\`.
- \`Container.DEFAULT_GRAPHICS_DRIVER\` remains \`wrapper\`.
- \`Container.WRAPPER_DRIVERS\` remains the existing wrapper allow-list: \`wrapper\`, \`wrapper-1.1\`, \`wrapper-v2\`, \`wrapper-leegao\`, and \`wrapper-gamenative\`.
- The persisted \`graphicsDriver\` setting is serialized with each container, but invalid IDs are normalized by the existing validation path. MaliForge GX is deliberately not in the allow-list and therefore is not saved as a selected graphics driver.
- \`graphicsDriverConfig.version\` is a separate setting used to select an installed custom Adrenotools Vulkan library. It must not be confused with the top-level Winlator wrapper ID.
- Existing driver options, defaults, per-container settings, and app flavors remain unchanged.

### Existing launch path

The observed launch path is:

1. \`XServerDisplayActivity\` reads the container/shortcut graphics settings.
2. \`extractGraphicsDriverFiles()\` extracts the selected existing wrapper archive into ImageFS and configures \`VK_ICD_FILENAMES\` for the existing wrapper ICD.
3. For a non-System custom driver version, it calls \`AdrenotoolsManager.setDriverById(...)\`, which configures the existing Adrenotools path/name/hooks environment variables.
4. \`GuestProgramLauncherComponent\` assembles the Wine environment and launches the guest process.
5. DXVK/VKD3D use the guest Vulkan loader and the configured wrapper/custom-driver route.

This is a real extension point for an externally imported Vulkan driver. It is not yet a MaliForge GX initialization lifecycle: the launch code does not verify that the intended GX backend was actually loaded, that the guest logical device was created by it, or that a frame was presented.

### Android-host Vulkan runtime

The native CMake target compiles the following MaliForge sources into the ARM64 \`winlator\` shared library:

- \`gx_native_runtime.cpp\`
- \`gx_vulkan_probe.cpp\`
- \`gx_vulkan_device_runtime.cpp\`

The host runtime/probe code creates and destroys Vulkan objects through Android's Vulkan loader, queries physical-device information, creates a minimal logical device and graphics queue, and exposes results through JNI. The additional Stage 42 branch queries physical-device properties/features/extensions/limits and correctly distinguishes advertised capabilities from features actually enabled on the logical device.

Stages 38–41 add host command-buffer transfer, offscreen image clear/readback, host offscreen render-pass/readback, and separate guest diagnostic-layer queue telemetry. These are useful diagnostics and tests, but none implements the complete Windows guest renderer.

### Guest Vulkan diagnostic layer

- The app packages \`assets/maliforge-gx/libmaliforge_gx_layer_x86_64.so\` as a Linux x86_64 guest asset.
- \`GXGuestVulkanLayerInstaller\` places it under the ImageFS Vulkan implicit-layer path and writes a manifest.
- It is opt-in and only enabled for the supported x86_64 guest path.
- The layer forwards calls to the next Vulkan implementation and collects limited API/queue telemetry.
- CI loads this layer against Mesa lavapipe and checks expected lifecycle/queue events.

This layer is a diagnostic pass-through. It is not a Vulkan ICD, does not supply \`vkCreateInstance\`/device functionality as a driver, does not replace the ICD selected by the guest loader, and does not own game render resources.

## MaliForge registry and selector

### Changes in this branch

The canonical backend identifier is now:

\`\`\`text
maliforge_gx
\`\`\`

The historical diagnostic identifier \`maliforge-gx-native\` is retained as a legacy constant for migration/reference purposes; it is not registered as a second backend and is not a valid container graphics-driver ID.

The registered descriptor display name is \`MaliForge GX\`. Its description is experimental and explicitly says that the real guest renderer package and verified Wine/container initialization hook are still required.

### Current availability

The selector adds an informational MaliForge GX row, but it remains disabled because:

- no MaliForge-owned game-renderer/ICD package is installed or bundled;
- the descriptor reports no runtime renderer initializer;
- the container wrapper allow-list does not include \`maliforge_gx\`;
- no verified GX initialization result exists.

A successful Android host Vulkan probe cannot override those checks. Do not change the selector to enabled merely because a Mali GPU or Vulkan 1.3 is detected.

## Third-party PanVK path — separate from MaliForge GX

The repository has a separate optional PanVK path documented in \`docs/MALIFORGE_GX_PANVK_OPTIONAL_DRIVER.md\`:

- The Adrenotools screen links to the upstream [JimVulkan/mali-panvk releases](https://github.com/JimVulkan/mali-panvk/releases).
- The existing ZIP importer validates archive paths, metadata, and the ARM64 ELF shared library before installing a custom driver.
- Installed custom driver versions are exposed in Graphics Driver Configuration and passed to the existing Adrenotools launch hook.
- The reviewed upstream release is \`v1.6.0\` / \`mali-panvk-27.0.0-devel-r10.zip\`. Its upstream release notes list Mali-G57 in the supported GPU family and document Android 12+ / 64-bit requirements.

This means the repository already has a route for users to **manually import a third-party PanVK build** and attempt to use it with the existing Winlator wrapper. It does not mean MaliForge GX bundles, owns, initializes, or has validated PanVK. No third-party PanVK binary is bundled by this audit. PanVK's upstream claims, license notices, device/kernel compatibility, and known Direct3D limitations remain applicable.

The distinction must remain visible in the UI and documentation. Do not relabel an imported third-party PanVK package as a MaliForge-owned renderer.

## Implementation status matrix

| Area | Current state | Evidence / remaining work |
|---|---|---|
| Registry/descriptor | Source implemented | Registry and validator exist; canonical ID corrected in this branch |
| Selector entry | Visible, disabled | Informational row exists; it is correctly not selectable |
| Driver persistence | GX not selectable/persisted | Existing container allow-list intentionally excludes GX |
| Android host Vulkan | Implemented diagnostic path | Host instance/device/queue and offscreen tests; no guest claim |
| Physical-device capabilities | Stage 42 source + CI | Host capability snapshot; still needs real-device execution |
| Guest Vulkan layer | Diagnostic pass-through | x86_64 layer and lavapipe smoke tests; not a driver |
| Existing custom driver path | Implemented | Adrenotools import + per-container custom driver configuration |
| Third-party PanVK | Optional/manual | Separate dependency; not owned/bundled/validated as GX |
| GX renderer package/ICD | Missing | Needs a real, legally redistributable, ABI-compatible backend |
| Wine/container GX initializer | Missing | Needs explicit initialization, verification, shutdown, and error reporting |
| Android WSI/surface/swapchain | Not implemented/verified for GX | No GX-owned surface/swapchain/present path |
| DXVK/VKD3D guest initialization | Not verified for GX | Need guest-created device and actual backend identity evidence |
| Windows application/game rendering | Not verified | No real Windows app/game frame from a GX backend |
| Real Mali hardware | Not tested by CI | Requires physical-device run and logs |

## Exact technical blockers

1. **No MaliForge-owned backend binary/source integration.** The ARM64 JNI library contains diagnostics and host Vulkan test code, not a packaged Vulkan driver/ICD or complete renderer.
2. **No verified guest-to-host renderer lifecycle.** There is no GX-owned hook that proves which driver the Wine guest loaded, selects its physical device, creates the guest device, and reports initialization/shutdown from the actual game process.
3. **No GX presentation path.** No MaliForge surface/swapchain/image acquisition/presentation lifecycle has been demonstrated through Winlator's Android display/X-server path.
4. **No end-to-end DXVK/VKD3D verification.** The project has not demonstrated a Windows guest creating a Vulkan device and rendering/presenting through a GX backend.
5. **No target-device verification.** Software CI and Android-host diagnostics do not test the user's Mali GPU, kernel UAPI, Android build, or game workload.
6. **Driver-ID and persistence boundary.** The canonical ID is now recorded in the GX registry, but \`Container.WRAPPER_DRIVERS\` intentionally excludes it. This must remain so until a concrete backend and launch integration pass verification; adding the ID to the allow-list early would create a fake selectable driver.

## Required next implementation stages

1. Select and pin a real backend implementation; audit its source provenance, licensing/notices, ABI, Android loader requirements, Mali kernel UAPI, and device support. Evaluate whether the existing optional PanVK package can serve as the underlying implementation without misrepresenting ownership.
2. Add an explicit GX runtime adapter to the existing Adrenotools/container launch path. The adapter must confirm the installed package and actual loaded library, not merely write environment variables.
3. Add backend identity and initialization telemetry from the guest Vulkan loader/device path. A host-side probe is not acceptable evidence.
4. Implement and verify the required Android window-system integration and guest surface/swapchain/present path.
5. Add device-side tests that start a real x86_64 Wine guest Vulkan workload, enumerate the guest-visible physical device, create a logical device/queue, submit a draw workload, present a frame, and verify pixels/logs.
6. Only after successful device tests should the app enable per-container GX selection and persistence. Keep the existing System/Wrapper paths and defaults intact.
7. Run a real DXVK D3D9/10/11 sample and a VKD3D D3D12 sample on physical Mali hardware; document feature-level limits and regression results before claiming game rendering.

## CI and release interpretation

The latest observed PR #42 workflow success establishes that the current source passes its configured software/host checks and produces a Standard debug APK artifact. It does not establish:

- a Vulkan renderer embedded in the APK;
- an Android surface/swapchain render;
- Wine guest Vulkan initialization through MaliForge GX;
- successful DXVK/VKD3D game rendering;
- compatibility with every Mali GPU or kernel.

Do not create a release claiming MaliForge GX renderer support on the basis of this CI job. Keep the PR draft until the missing runtime and hardware work is complete.

## Final verdict

**Highest verified level: \`DRIVER VISIBLE IN SELECTOR\`.**

The row is intentionally disabled. Host Vulkan initialization/offscreen diagnostics are implemented and CI-validated in their stated scope. Guest Vulkan rendering, surface presentation, Windows application rendering, game rendering, and real Mali hardware verification remain unverified.
