# Winlator Lean — Repository Audit Snapshot (2026-11-29)

## Scope and baseline

- Repository: `suisuisui734-spec/Project-Winlator-`
- Base branch: `main`
- Base commit: `c2ead7fd104cc37edad26eee82886d1fba899b28`
- Implementation branch: `feature/winlator-lean-mode-20261129`
- Platform/build target: Android ARM64 (`arm64-v8a`)
- Inspection method: GitHub recursive Git tree and source-file inspection. No checkout/device benchmark was available in this session.

## Measured repository contents

The recursive Git tree reported 1,579 entries, including 1,444 tracked blobs, with 248,149,441 bytes of tracked file content (decimal bytes).

| Area | Measured value |
|---|---:|
| All tracked blobs | 248,149,441 bytes |
| `.tzst` archives | 230,667,315 bytes |
| Packaged Android `.so` files under `jniLibs/arm64-v8a` | 12 |
| Total size of those 12 tracked `.so` files | 3,791,288 bytes |
| Native Android ABI filters | `arm64-v8a` only |
| Product flavors | `standard`, `antutu`, `ludashi` |

Largest tracked payloads observed:

| File | Size |
|---|---:|
| `app/src/main/assets/container_pattern_common.tzst` | 84,522,663 bytes |
| `app/src/main/assets/wincomponents/direct3d.tzst` | 31,207,067 bytes |
| `app/src/main/assets/graphics_driver/adrenotools-v819.tzst` | 9,881,991 bytes |
| `app/src/main/assets/dxwrapper/dxvk-1.10.3.tzst` | 7,544,894 bytes |
| `app/src/main/assets/dxwrapper/dxvk-1.12.1-sarek.tzst` | 4,628,686 bytes |
| `app/src/main/assets/layers.tzst` | 4,447,187 bytes |

These are repository source-file sizes, **not APK sizes**. The archives are runtime payloads and may contain components selected at runtime. `container_pattern_common.tzst` is extracted during container setup, and Box64/WOWBox64/FEXCore/DXVK/driver archives are selected by existing code. They were not deleted.

## Build and packaging findings

- The app already uses `abiFilters 'arm64-v8a'`; host Android native packaging is already ARM64-only.
- This must not be confused with guest Windows x86/x64 support. Guest translation/runtime archives must remain available.
- Release currently has `minifyEnabled false`; resource shrinking is not enabled in the inspected app build configuration.
- Native CMake compilation uses `-O2`; native libraries are linked for Vulkan, EGL/GLES, audio and renderer/runtime functionality.
- JNI libraries are distributed under `arm64-v8a` only.
- Gradle `preBuild` depends on `downloadAssets`, which can fetch `imagefs.tzst` from an external release if it is absent. This external input affects reproducibility and should be checksum-pinned before build automation is treated as fully reproducible.
- Existing build flavors are application IDs/product variants, not proof of three distinct runtime optimization profiles.
- The CI workflow builds standard/AnTuTu/Ludashi variants and uploads workflow artifacts. It does not publish release assets by itself.

## Performance and resource findings

### Optional performance-management work

`PerformanceManager` coordinates optional Android performance hints. The thread priority path in `PerfPriority` scans `/proc/self/task`, enumerates visible process directories, builds a descendant process set and then scans task directories. Big-core affinity uses sysfs topology inspection and another process-tree walk. These operations are not per-frame, but they are optional launch/resume work and are candidates to avoid on a lightweight profile.

Thermal handling is callback-based through Android's thermal status listener; no continuous polling loop was found in that class. Existing restoration paths restore thread priorities, stop Samsung SPerf boosts and disable sustained-performance requests on pause/stop.

### Implemented in this branch

- Added a persisted **Winlator Lean Mode** setting under Settings → Performance, default off.
- When enabled, the performance manager bypasses optional thread-priority scans, big-core affinity application, sustained-performance requests and OEM boost hooks.
- Android Game Mode signaling remains a separate user preference.
- Thermal guard behavior remains active.
- Wine, Box64/FEX, graphics drivers, Vulkan, audio, input and container services are not removed.
- Added unit tests for the policy decision that suppresses optional hooks when Lean Mode or the thermal guard is active.

This is deliberately narrower than a complete Lite/Performance/Compatibility profile system. No unsupported Box64 defaults, thread counts, GPU clocks, or environment variables were forced.

## MaliForge GX status

The inspected README and native build files show host Vulkan diagnostics, device/queue discovery, Vulkan smoke-test paths and guest telemetry/layer integration work. The README explicitly states that a complete guest Vulkan renderer/game-rendering path is not complete or verified, and physical Mali/MediaTek device testing is pending. A menu entry, ELF packaging check, host Vulkan smoke test, or successful compile alone does not prove game rendering works.

No driver archives or MaliForge GX source files were removed or rewritten in this optimization pass.

## APK build measurements — CI verified

A Standard Debug APK was successfully built and inspected by GitHub Actions:

- Workflow run: `46182550279`
- Application source commit: `ad00d870ba733d8a1bdf787db4fc33aa3daa1998`
- APK path: `app/build/outputs/apk/standard/debug/app-standard-debug.apk`
- Package/version: `com.winlator.mali`, versionCode `23`, versionName `1.2.1 Bionic`
- APK size: **448,088,211 bytes** (448.09 MB decimal; approximately 427.3 MiB)
- ZIP entries: 1,292
- Native libraries: 22, totaling 12,271,152 uncompressed bytes
- Packaged Android native ABI: `arm64-v8a` only
- Runtime asset entries: 111
- Runtime assets: 433,967,419 bytes uncompressed and 432,197,737 bytes compressed in the APK (about 96.5% of the APK file size)

Largest APK entries from the build inspection:

| APK entry | Uncompressed bytes | Compressed bytes |
|---|---:|---:|
| `assets/imagefs.tzst` | 199,788,880 | 199,661,292 |
| `assets/container_pattern_common.tzst` | 84,522,663 | 84,500,995 |
| `assets/wincomponents/direct3d.tzst` | 31,207,067 | 31,189,835 |
| `assets/graphics_driver/adrenotools-v819.tzst` | 9,881,991 | 9,883,996 |
| `assets/dxwrapper/dxvk-1.10.3.tzst` | 7,544,894 | 7,540,313 |

The large `.tzst` runtime assets are already compressed, so APK ZIP compression saves very little on these payloads. Removing them without replacing their runtime delivery would remove functionality. The high-impact future size option is a validated Lite content package/on-demand optional payload delivery, not blindly stripping Wine or graphics components.

The uploaded workflow artifact is named `project-winlator-standard-debug-stage23-validation` and is available until **2026-12-12**. It is a CI artifact, not a published GitHub Release asset. No release APK assets were attached to the repository's Releases at audit time.

No baseline APK for the same codebase/build type was available, so APK size reduction percentage cannot be calculated. The Standard Debug result must not be described as a signed production release.

## Device measurements — not available

These values remain **not measured**:

- Install footprint and install-time behavior on device
- App startup, container startup and game launch latency
- Device RAM and CPU usage
- FPS, frame-time percentiles and stutter frequency
- Thermal behavior or sustained performance
- Physical-game compatibility and crash rate

A physical Android run is required for those hardware metrics. Do not infer device performance from a successful CI build.

## Risks and next optimization candidates

1. Make externally downloaded build inputs immutable and checksum-verified.
2. Generate and inspect all three flavor APKs independently; current CI metadata inspection selects only the first discovered APK.
3. Evaluate R8/resource shrinking in a separate branch with JNI/reflection keep rules and full flavor regression tests; it was not enabled blindly here.
4. Inspect native linker dependencies and symbol sizes from built APKs before stripping or removing any native `.so`.
5. Consider on-demand content delivery for optional driver/runtime versions only after version-selection and offline/container-restore behavior is tested.
6. Benchmark Lean Mode against the existing default on the same physical device/game, with identical resolution, driver, container and thermal starting state.

## Validation record

- Repository tree and package-source inspection completed.
- Standard Debug source wiring built successfully in GitHub Actions.
- Unit tests and lint passed on application source commit `ad00d870ba733d8a1bdf787db4fc33aa3daa1998`.
- MaliForge GX Linux host smoke test passed; APK guest-layer asset was verified as ELF64 x86_64.
- Standard Debug APK metadata and ARM64 native ABI inspection passed.
- Physical-device performance and game compatibility testing remain pending.
- No APK size reduction, FPS gain, RAM reduction, or temperature improvement is claimed because no same-condition baseline/device comparison was performed.
