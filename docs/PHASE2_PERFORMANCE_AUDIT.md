# Phase 2 Performance Audit

## Scope and environment

- Repository: suisuisui734-spec/Project-Winlator-
- Base source revision: Phase 1 acd97ce71b17dd7c8b646c66db4e8bf163e3d02a
- Working branch: feature/helio-g100-performance-phase2
- Target: MediaTek Helio G100 / Mali / Android / ARM64
- Physical device access: NOT AVAILABLE during this source audit
- No FPS, frame-time, GPU-load, CPU-load, thermal or RAM improvement is claimed from static inspection alone.

## Confirmed source findings

| Finding | Evidence | Files | Expected impact | Risk | Device-free verification |
|---|---|---|---|---|---|
| Affinity mask is truncated to 8 bits | ProcessHelper.setLinuxAffinity formatted mask & 0xff, while CpuTopology builds a 32-bit mask | core/ProcessHelper.java, perf/CpuTopology.java | Correctly passes selected indices above 7; no guaranteed scheduling gain | Low | YES: pure formatting tests and compile |
| Repeated one-off executors are not shut down | New single-thread executor per stream/wait task, container create/duplicate/remove, and XServer work | core/ProcessHelper.java, container/ContainerManager.java, XServerDisplayActivity.java | Avoids leaving one idle executor thread per completed job; lowers thread creation churn | Low–medium | YES: code review, compile, unit tests |
| HUD telemetry keeps running after the overlay is hidden | disableByUser stops redraw but does not stop HudDataSource; the source polls every 1.5 seconds | widget/WinlatorHUD.java, widget/HudDataSource.java | Avoids background proc/sysfs reads and Mali-counter sampling while HUD is hidden | Low–medium | YES: lifecycle/source checks; physical sensor access remains NOT TESTED |
| Thermal hysteresis is embedded in a manager | Transition logic is pure state logic inside PerformanceManager | perf/PerformanceManager.java | Allows automated transition regression tests | Low | YES: pure unit tests |

## Potential bottlenecks (not confirmed)

| Area | Evidence / uncertainty | Files | Impact | Risk | Device-free test |
|---|---|---|---|---|---|
| Shader compilation stalls | Renderer compiles GLSL on first use; PostFX effect variants are cached in memory after compile. No trace or hitch measurement exists | cpp/winlator/renderer/shader.cpp, postfx.cpp, egl.cpp | Could cause first-use frame-time spikes; not quantified | High if changing renderer synchronization or persistent cache | Compile/static inspection only |
| GL/Vulkan synchronization fallback | Frame generation calls glFinish when native sync-file handoff is unavailable. Whether Helio G100 uses this path is unknown | cpp/winlator/renderer/frame_gen.cpp | Potential wait cost if fallback active | High; changing fences risks rendering corruption/hangs | Native build only; target device required to identify path |
| Mali counter availability | Native counter reader may be blocked by SELinux/OEM permission. HUD also has sysfs fallbacks | cpp/winlator/mali_counters.cpp, widget/MaliGpuCounters.java, HudDataSource.java | Measurement quality only; no direct game-performance effect established | High for device-specific ioctl changes | NO; physical device needed |
| Asset/startup I/O | Rootfs and container layers are large compressed assets; first boot and prefix extraction are disk-heavy by design | app/src/main/assets, ImageFsInstaller, ContainerManager | Could affect first-run time, not steady-state FPS | Medium–high if packaging/install behavior changes | File-size analysis possible; device storage timing needed |
| Process scans and telemetry allocation | HUD CPU sampler scans process entries every few seconds and uses temporary maps/lists for samples | widget/HudDataSource.java | Some monitoring overhead is possible; no proof it affects game pacing | Low–medium | Static inspection; runtime profiling needed |

## GPU and rendering audit

- Default graphics driver remains Wrapper; no driver is automatically selected or replaced.
- Adreno-only Turnip is not forced onto Mali.
- Vulkan/OpenGL capability queries and fallback paths remain intact.
- PostFX compiles effect shaders lazily and caches variants in memory. There is no evidence to justify adding another shader cache.
- Frame generation is optional; its GL/Vulkan fence path has conditional synchronous fallbacks. No changes were made without target measurements.
- BCn cache and format settings already exist per graphics configuration. No global cache deletion was added.

## Memory and package audit

- Phase 1 repository tree had about 236 MiB of tracked blob contents; app assets accounted for about 223.3 MiB.
- Largest tracked assets included container_pattern_common.tzst (~80.6 MiB) and wincomponents/direct3d.tzst (~29.8 MiB). These are runtime assets and were preserved.
- Phase 1 Actions artifact archive was 446,623,228 bytes. This is the artifact ZIP size, not the exact APK file size.
- Phase 2 CI adds APK inspection for actual APK bytes, native library bytes, asset compression and largest ZIP entries.
- The app is already filtered to arm64-v8a; ABI splitting is not useful while only one ABI is configured.

## Evidence-based changes

1. Replace per-operation executors with shared pools whose idle workers time out.
2. Serialize container directory create/duplicate/remove operations.
3. Pause HUD telemetry when hidden and create its worker lazily.
4. Pass full CPU affinity masks instead of discarding high bits.
5. Extract thermal hysteresis into a pure helper with unit tests.
6. Add APK package, ABI and resource inspection to CI.

No driver, runtime archive, container data or upstream feature was removed.
