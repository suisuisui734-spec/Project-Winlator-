# Helio G100 Repository Audit

## Scope and revision

- Repository: `suisuisui734-spec/Project-Winlator-`
- Audited base: `main` at `79d178f94b71addbdb3ce43dfaa1c78ca22f595f`
- Source lineage: Winlator Mali Bionic branch `bionic-mali-1.2`
- Target device: MediaTek Helio G100 / Mali GPU / Android / ARM64
- Working branch: `feature/helio-g100-mali-optimization`

The repository is an Android application source tree, not a JavaScript project. This audit preserves its upstream native runtime, graphics assets, Wine/Box64/FEX packages and attribution.

## Existing architecture

- Android application module: `:app`, Java 17, namespace `com.winlator.cmod`.
- Android configuration: compile SDK 34, Build Tools 35.0.0, min SDK 26, target SDK 28.
- Native build: NDK `29.0.14206865`, CMake 3.22.1, ABI filter `arm64-v8a`.
- Native libraries include the Winlator JNI library, Fake Input, Gladio renderer and ZeroFG. The native build also references the adrenotools Git submodule.
- Container management is implemented in `container/Container.java` and `container/ContainerManager.java`. Container JSON persists resolution, graphics driver/configuration, environment variables, Box64/FEX selection, CPU lists and FPS visibility.
- Emulation is hosted by `EmulationService` and `XServerDisplayActivity`.

## Existing graphics implementation

- Default graphics driver is `wrapper`; supported wrapper variants remain in `Container.WRAPPER_DRIVERS`.
- The default graphics configuration enables BCn emulation and persistent BCn cache; the default transcode mode is ASTC.
- The native build includes Gladio EGL/GLES rendering, Mali counters, Vulkan integration and ZeroFG.
- Mali telemetry is implemented through `MaliGpuCounters` and `HudDataSource`, with sysfs fallbacks where readable.
- The README documents Wrapper + ASTC/ETC2 as the Mali starting point and Turnip as the Adreno path. This optimization does not automatically replace the driver.

## Existing optimization features

- Box64 profiles and custom presets are implemented by `Box64PresetManager`; Stability, Compatibility, Intermediate and Performance are present along with specialized presets.
- FEXCore has its own preset manager and environment-variable catalog.
- Resolution is stored per container through `Container.screenSize`; display scaling and custom display resolution controls already exist.
- Per-container graphics driver selection and BCn options are already present.
- `WinlatorHUD` and `HudDataSource` provide frame/FPS information, GPU/CPU metrics where available, battery/RAM and thermal status. Sampling is background-threaded at approximately 1.5-second intervals.
- `PerformanceManager` already provides Android Game Mode signaling, optional thread-priority hints, optional CPU affinity, sustained-performance mode and Samsung-specific hooks.

## Findings and changes applied on this branch

1. CPU big-core detection previously guessed that the upper half of logical CPUs were the big cluster when sysfs frequency data was unavailable. That assumption is unsafe across MediaTek/OEM topologies. The fallback was removed: missing topology data now means no affinity change. CPU affinity masks are also bounds-checked.
2. The performance manager now includes an Android 10+ thermal status listener with hysteresis: pause optional thread-priority and sustained-performance hints at Moderate or higher; resume only at Light or lower. The guard never changes frequencies, voltage, thermal limits or kernel settings.
3. The Performance settings page now displays read-only device, SoC, hardware, ABI, RAM, renderer and thermal API information where Android exposes it. GPU renderer detection is best-effort and failure-safe.
4. The build workflow gains feature-branch push and pull-request build triggers plus a source validation script. The default CI build produces a standard debug APK artifact.

## Potential bottlenecks

- x86/x86_64 translation through Box64/FEX and Wine, Direct3D translation, shader compilation and BCn texture conversion remain workload-dependent costs.
- GPU load/clock visibility depends on OEM sysfs permissions or access to supported Mali counters; metrics may legitimately be unavailable.
- CPU frequency sysfs nodes can be hidden by Android. Affinity therefore remains optional and no cluster layout is inferred when detection fails.
- Gradle `downloadAssets` fetches `imagefs.tzst` from an upstream release URL during pre-build. This is an external build dependency and currently has no repository-pinned checksum.
- The app is ARM64-only by its Gradle ABI filter; 32-bit builds are not configured.

## Compatibility risks

- Vulkan extensions and driver behavior vary by Mali GPU generation and OEM firmware. Do not force extensions or install Adreno Turnip on Mali.
- Thermal API availability begins at Android 10 (API 29). Older versions keep the existing behavior without the guard.
- Some thermal, GPU and frequency metrics may be blocked by SELinux or OEM policy; unavailable values must remain unavailable.
- Native behavior and real Helio G100 performance cannot be validated without a physical target device.

## Components intentionally not removed

No driver, wrapper, runtime package, native library, source directory, submodule or user container data was removed. Upstream licenses and attribution remain in place.
