# Helio G100 Optimization Notes

## Implemented in this iteration

### Safe CPU topology detection

- Big cores are identified only from readable per-core maximum-frequency data.
- If the system hides those nodes, affinity selection returns an empty set and does not guess a CPU cluster.
- The affinity mask ignores indices outside the supported integer bitmask width.
- The existing user opt-in for big-core affinity remains; no affinity is forced globally.

### Thermal-aware optional performance hints

- Added preference key: `perf_thermal_guard` (enabled by default).
- Android 10+ devices register `PowerManager.OnThermalStatusChangedListener` while an emulation session is active.
- At `THERMAL_STATUS_MODERATE` or higher, Winlator restores its optional thread-priority hints, disables its sustained-performance request and stops the optional Samsung boost.
- Hysteresis resumes those hints only after the device cools to `THERMAL_STATUS_LIGHT` or lower.
- On Android versions below 10, or when the thermal API is unavailable, the listener is not installed.
- Android thermal protections remain authoritative. No clock, voltage, kernel, thermal-daemon or power-limit controls are changed.
- The thermal guard does not rewrite an already-applied CPU affinity mask. Big-core affinity remains an explicit opt-in and should be left disabled when the OEM topology cannot be verified.

### Device compatibility information

The Performance tab now displays read-only device model, SoC (when exposed), hardware identifier, supported ABIs, ARM64 availability, the system GPU renderer, total RAM and current thermal status where supported.

For a detected Mali renderer, the summary reminds the user of the existing upstream Mali baseline: Wrapper graphics driver with BCn ASTC or ETC2. It does not change any container or driver automatically.

## Existing systems reused (not duplicated)

| Requirement | Existing implementation |
|---|---|
| Graphics choice and per-container settings | `Container.graphicsDriver`, `graphicsDriverConfig`, Graphics Driver Config dialog |
| Mali BCn texture compatibility/cache | Wrapper configuration; BCn settings in the graphics dialog |
| Box64 profiles | `Box64PresetManager`, including Stability / Compatibility / Intermediate / Performance and custom presets |
| FEX profiles | Existing FEXCore preset manager and environment variable catalog |
| Per-container resolution | `Container.screenSize`, display-resolution settings |
| Per-game overrides | `ShortcutSettingsDialog`: per-shortcut resolution, graphics/DX wrapper, Box64/FEX versions and presets, env vars and CPU list |
| Performance HUD | `WinlatorHUD`, `HudDataSource`, `MaliGpuCounters` |
| Container persistence | Existing container JSON serialization and manager |
| Game-session performance hooks | `PerformanceManager` |

## Deliberately not forced

- No Turnip/Adreno driver is forced on Mali.
- No Vulkan extension is assumed available.
- No graphics driver or wrapper is deleted.
- No global Android display resolution change, mandatory upscaling, frame generation, overclock or root-only operation is introduced.
- No duplicate Box64 preset or container manager was created because equivalent systems already exist.
- CPU big-core affinity remains opt-in and is only applied when topology can be detected from the device.
- The BCn cache setting can be toggled per graphics configuration; a general-purpose cache deletion UI was not added because cache paths/data ownership were not verified well enough to promise that game data is safe.

## CI build result

- GitHub Actions run `45901386995`: SUCCESS.
- Commit: `71804736d1f5dda868ed0b2914fdde587fee5c52`.
- Tasks passed: source validation, standard debug unit-test task, lint, debug APK and native compilation, artifact upload.
- Uploaded artifact: `project-winlator-standard-debug`, archive size 446,623,228 bytes (about 426 MiB). This is the artifact archive size, not an independently measured APK size.
- The repository currently has no `src/test` or `src/androidTest` source files, so the unit-test task completed without project-authored test cases.

## Remaining work

- Install and exercise the APK on a physical Helio G100 device.
- Test first-run rootfs extraction, container launch/stop, wrapper/BCn rendering and HUD.
- Observe thermal transitions during repeatable game workloads and confirm optional hints pause/resume without affecting game stability.
- Benchmark FPS and frame times before/after under the same game, container, resolution, driver and ambient/device conditions. No performance uplift is claimed.
- Investigate a safe way to distribute large runtime assets separately or as an on-demand download, with integrity checking and offline/retry handling, rather than stripping required drivers or runtime data.

## Rollback

Revert the commits on `feature/helio-g100-mali-optimization`, or leave the branch unused. The default `main` branch is not modified.
