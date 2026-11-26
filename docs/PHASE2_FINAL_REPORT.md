# Project Winlator — Phase 2 Final Report

## Repository

- Repository: suisuisui-spec/Project-Winlator-
- Original main baseline: 79d178f94b71addbdb3ce43dfaa1c78ca22f595f
- Phase 1 source base: acd97ce71b17dd7c8b646c66db4e8bf163e3d02a
- Phase 2 branch: feature/helio-g100-performance-phase2
- Verified Phase 2 source commit: ddd75ccb66c3682e28d3aeed0e9a3f134325930d
- main remains unchanged.

## Performance findings

### Confirmed source issues corrected

- CPU affinity mask was truncated to 8 bits despite CpuTopology using a 32-bit mask.
- Repeated one-off executor services were created for process stream/wait operations, container create/duplicate/remove and XServer background tasks without shutdown.
- Shortcut artwork used a permanent fixed worker pool.
- HUD stopped redraw when hidden but telemetry kept polling.
- Vulkan custom-driver initialization could pass uninitialized path pointers to adrenotools when path/string lookup failed.

Changes correct these code paths and make Vulkan fall back to Android's system loader when custom driver setup is incomplete. These are source/stability fixes, not measured FPS gains.

### GPU, frame time and memory

- No Helio G100 shader hitch, GPU saturation, GL/Vulkan synchronization stall or memory pressure was measured.
- PostFX shader variants remain cached in memory after compile; no additional shader cache was added.
- Frame generation has a conditional glFinish fallback if sync-file handoff is unavailable; whether the target phone enters this path remains NOT TESTED.
- No driver, wrapper, runtime archive, native library or game/container data was removed.
- APK size is dominated by bundled runtime assets, especially imagefs and the common container pattern archive.

## Changes

### Source files modified or added

- app/src/main/cpp/winlator/vulkan.c
- app/src/main/java/com/winlator/cmod/core/ProcessHelper.java
- app/src/main/java/com/winlator/cmod/core/AppExecutors.java (new)
- app/src/main/java/com/winlator/cmod/container/ContainerManager.java
- app/src/main/java/com/winlator/cmod/container/Shortcut.java
- app/src/main/java/com/winlator/cmod/XServerDisplayActivity.java
- app/src/main/java/com/winlator/cmod/widget/HudDataSource.java
- app/src/main/java/com/winlator/cmod/widget/WinlatorHUD.java
- app/src/main/java/com/winlator/cmod/perf/CpuTopology.java
- app/src/main/java/com/winlator/cmod/perf/PerformanceManager.java
- app/src/main/java/com/winlator/cmod/perf/ThermalGuardState.java (new)
- app/build.gradle
- .github/workflows/build-apk.yml
- scripts/inspect-apk.py (new)
- Three JUnit test files under app/src/test/java.

### Behavior changes

- Shared idle-expiring background workers; serial I/O for container directory mutations.
- Lazy/pauseable HUD telemetry.
- Full-width CPU affinity formatting and unit tests.
- Pure, unit-tested thermal hysteresis; unavailable status preserves prior state.
- Vulkan custom-driver path checks and system-loader fallback.
- APK metadata/ABI/asset inspection in CI.

Existing Box64/FEX presets, per-container/per-game settings, Wrapper/graphics settings and BCn cache mechanisms are reused. No undocumented Box64/FEX variables or forced Mali driver were added.

## Build

- GitHub Actions run: 45905943515
- Overall result: SUCCESS
- Source validation: PASS
- :app:testStandardDebugUnitTest: PASS
- :app:lintStandardDebug: PASS
- Standard debug APK: PASS
- Native ARM64 compilation: PASS
- APK metadata/ABI inspection: PASS
- Artifact upload: PASS

### APK and artifact

- Package: com.winlator.mali
- Version: 1.2.1 Bionic, versionCode 23
- APK size: 447,763,096 bytes (about 427 MiB)
- Native ABI: arm64-v8a only
- Native libraries: 22, 11,663,800 bytes uncompressed
- Assets: 110 entries, 433,937,395 bytes uncompressed
- Artifact name: project-winlator-standard-debug-phase2
- Artifact ID: 15157618638
- Artifact ZIP size: 446,628,327 bytes
- Artifact expires: 2026-12-10 UTC
- Download page: https://github.com/suisuisui-spec/Project-Winlator-/actions/runs/45905943515

The artifact ZIP is larger than the APK because it includes the APK and inspection outputs. The APK size is measured separately above.

## Testing and benchmark

- 10 added project-authored unit test methods: PASS.
- Android lint: PASS.
- ARM64 native compilation: PASS.
- APK package metadata/ABI inspection: PASS.
- Physical Helio G100 install/runtime test: NOT TESTED.
- Real device thermal events: NOT TESTED.
- Mali hardware counters/sysfs permissions: NOT TESTED.
- Container launch/stop and game graphics regression: NOT TESTED.
- FPS, frame time, stutter, RAM and thermal-degradation benchmark: NOT TESTED.
- Performance result: NO MEASURED IMPROVEMENT because no controlled device benchmark exists.

## Native warnings

The latest native build no longer reports sometimes-uninitialized warnings for the Vulkan custom-driver pointers fixed in Phase 2. Other upstream warnings remain, primarily unused JNI parameters and signed/unsigned comparisons. These warnings do not fail the verified build.

## Stability and compatibility

- Adreno Turnip is not forced onto Mali.
- No Vulkan extension is forced.
- Android thermal protections, CPU/GPU frequencies, voltage and kernel settings are untouched.
- System Vulkan fallback is preserved if custom driver preparation/loading fails.
- All Phase 2 changes remain on the feature branch. No merge or release was created.

## Rollback

Use feature/helio-g100-mali-optimization to return to Phase 1 source. Keep Phase 2 separate until target-device installation, runtime, graphics and benchmark regression checks are completed.
