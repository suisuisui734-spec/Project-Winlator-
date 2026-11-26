# Phase 2 Performance Audit

## Scope

- Original main: 79d178f94b71addbdb3ce43dfaa1c78ca22f595f
- Phase 1 source base: acd97ce71b17dd7c8b646c66db4e8bf163e3d02a
- Latest verified Phase 2 source: 96401a1f4c9d44b780b060a501f953114167f14d
- Target: MediaTek Helio G100 / Mali / Android / ARM64
- Physical target device: NOT AVAILABLE during this execution.

Static source findings and a successful build do not establish a real-world FPS improvement. No FPS, frame-time, GPU-load, CPU-load, thermal, or game-RAM result was measured on the target device.

## Confirmed source findings and fixes

| Finding | Evidence | Files | Change | Expected impact | Risk | Device-free verification |
|---|---|---|---|---|---|---|
| CPU affinity mask truncated to eight bits | ProcessHelper.setLinuxAffinity formatted mask & 0xff although CpuTopology builds a 32-bit mask | ProcessHelper.java, CpuTopology.java | Pass full hexadecimal mask and add mask-format tests | Correctly passes selected core indices above 7; no guaranteed FPS gain | Low; taskset still checks actual allowed CPUs | PASS: unit-test task and build |
| Repeated one-off executors | New single-thread executors constructed for process stream readers/waiters, container create/duplicate/remove, and XServer work without shutdown | ProcessHelper.java, ContainerManager.java, XServerDisplayActivity.java | Reuse shared background and serial-IO executors with idle expiry | Less repeated executor/thread creation and idle thread retention | Low–medium; runtime concurrency needs device regression | PASS: unit-test task and build |
| Shortcut artwork uses a permanent fixed worker pool | Static fixed pool has no idle worker expiry | Shortcut.java | Reuse shared asset executor with core worker timeout | Releases idle asset threads after inactivity | Low | PASS: build/lint |
| HUD telemetry continues while hidden | disableByUser stopped redraw but did not pause HudDataSource, which polls every 1.5 seconds | WinlatorHUD.java, HudDataSource.java | Lazy worker creation plus explicit pause/resume/stop | Avoids periodic proc/sysfs/Mali reads while HUD is hidden | Low–medium; runtime lifecycle still needs testing | PASS: build/lint; physical overlay test NOT TESTED |
| Thermal state logic was embedded in manager | Hysteresis logic was a pure state transition embedded in PerformanceManager | PerformanceManager.java | Extract ThermalGuardState and test invalid/unavailable status | Enables repeatable tests and avoids treating missing data as cool | Low | PASS: unit-test task |
| Vulkan custom-driver path used potentially uninitialized pointers | Clang reported -Wsometimes-uninitialized for native_libdir, driver_path and library_name; init_vulkan also passed uninitialized path values to adrenotools if setup failed | vulkan.c | Initialize pointers; validate custom paths/strings; fall back to Android system Vulkan loader if custom setup fails | Avoids undefined pointer use and preserves system graphics fallback | Medium; actual custom-driver fallback must be checked on device | PASS: latest native build; device behavior NOT TESTED |

## Potential bottlenecks (not confirmed)

| Area | Evidence / uncertainty | Files | Potential impact | Risk | Device-free test |
|---|---|---|---|---|---|
| Shader compilation stalls | GLSL is compiled on use; PostFX variants are cached in memory after compile. No hitch trace exists | renderer/shader.cpp, postfx.cpp, egl.cpp | First-use frame-time spikes are possible; not quantified | High if changing sync/cache behavior | Static/native build only |
| GL/Vulkan wait path | Frame generation has a glFinish fallback when sync-file interoperability is unavailable; target path is unknown | renderer/frame_gen.cpp | Could add GPU/CPU wait overhead if active | High; fence changes risk corruption or hangs | Static/native build only |
| Mali counter access | Native counters and sysfs fallbacks depend on SELinux/OEM access | mali_counters.cpp, MaliGpuCounters.java, HudDataSource.java | Affects metric availability, not proven game performance | High for device-specific ioctl changes | NO: target device needed |
| Container/game startup I/O | Rootfs and prefix archives are large compressed assets | app assets, ImageFs installer, ContainerManager | Could affect first-run time, not necessarily steady FPS | Medium–high if packaging changes | Asset size measurable; target storage timing needed |
| Telemetry process scans | HUD CPU sampling reads process information and temporary maps/lists | HudDataSource.java | Monitoring overhead is possible but unmeasured | Low–medium | Static inspection only |

## Graphics audit

- Default graphics driver remains Wrapper.
- Adreno Turnip is not forced onto Mali.
- No Vulkan extension is forced or assumed.
- Existing OpenGL/Vulkan detection, fallbacks and BCn configuration are preserved.
- Existing PostFX shader in-memory caching is reused; no disk shader cache was added.
- Frame generation remains optional; no custom frame pacing was introduced.
- Vulkan change only validates custom-driver initialization and adds system-loader fallback on incomplete custom setup.

## Memory and package findings

Latest successful APK inspection from run 45905407533:

- APK path: app/build/outputs/apk/standard/debug/app-standard-debug.apk
- APK size: 447,763,096 bytes (about 427 MiB)
- Package: com.winlator.mali
- Version: 1.2.1 Bionic, versionCode 23
- ZIP entries: 1,291
- Native ABI: arm64-v8a only
- Native libraries: 22, total 11,663,720 bytes uncompressed
- Asset entries: 110
- Asset bytes: 433,937,395 uncompressed; 432,187,879 compressed in APK

Largest entries include imagefs.tzst (199,788,880 bytes), container_pattern_common.tzst (84,522,663 bytes), wincomponents/direct3d.tzst (31,207,067 bytes), classes.dex (11,046,824 uncompressed), and adrenotools-v819.tzst (9,881,991 bytes).

The package size is dominated by runtime assets rather than native ARM64 libraries. No runtime, driver, wrapper, game data, or source component was removed.

## Verified build

- Workflow: 45905407533
- Source commit: 96401a1f4c9d44b780b060a501f953114167f14d
- Overall: SUCCESS
- Source validation: PASS
- Unit-test task: PASS
- Android lint: PASS
- APK assemble: PASS
- Native ARM64 compilation: PASS
- APK metadata/ABI inspection: PASS
- Artifact upload: PASS
- Artifact: project-winlator-standard-debug-phase2, ID 15158325113
- Artifact ZIP size: 446,628,332 bytes
- Expiry: 2026-12-10 UTC
- Run page: https://github.com/suisuisui-spec/Project-Winlator-/actions/runs/45905407533

The native compiler no longer reports the sometimes-uninitialized Vulkan pointer warnings after the latest fix. Other upstream warnings remain, including unused JNI parameters and signed/unsigned comparisons.

## Remaining risks

- Actual Mali custom-driver fallback needs Helio G100 runtime testing.
- Thermal callbacks and telemetry permissions are OEM/Android-version dependent.
- CPU topology reads may be restricted; affinity remains opt-in.
- The app is ARM64-only by Gradle ABI filter.
- No controlled game benchmark was available; NO MEASURED IMPROVEMENT is claimed.
