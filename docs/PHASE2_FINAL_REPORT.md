# Project Winlator — Phase 2 Final Report

## Repository

- Repository: suisuisui734-spec/Project-Winlator-
- Original main baseline: 79d178f94b71addbdb3ce43dfaa1c78ca22f595f
- Phase 1 base: acd97ce71b17dd7c8b646c66db4e8bf163e3d02a
- Phase 2 branch: feature/helio-g100-performance-phase2
- Phase 2 modified commit: PENDING commit creation
- main must remain unchanged.

## Performance findings

### Confirmed source issues
- ProcessHelper.setLinuxAffinity masked the supplied affinity to 8 bits even though CpuTopology builds an int CPU mask.
- New one-off executor services were created repeatedly for process stream readers/waiters, container create/duplicate/remove, and XServer background work; the executor services were not shut down.
- HUD redraw stopped when the overlay was hidden, but the data source kept sampling.

These are source-code findings, not measured FPS bottlenecks. Expected outcomes are reduced worker-thread churn, no hidden-HUD sampling, and correct propagation of affinity bits. No numeric performance uplift is claimed.

### GPU / frame-time / memory
- No Helio G100 shader hitch, GL/Vulkan synchronization stall, GPU saturation or memory pressure was measured.
- PostFX effect shader variants are cached in memory after their first compilation.
- Frame generation contains glFinish fallback paths when sync-file interoperability is unavailable. Whether the target uses those paths remains NOT TESTED.
- No graphics driver, wrapper, runtime or game data was removed or changed.

## Changes

Planned/implemented on Phase 2 branch:
- Shared background, serial-I/O and asset executors with idle timeout.
- Pauseable, lazy HUD telemetry worker.
- Full-width CPU affinity-mask formatting.
- Pure thermal hysteresis helper and device-independent unit tests.
- APK package, ABI and resource inspection in CI.

No undocumented Box64/FEX environment variable or forced Mali driver was introduced. Existing per-container/per-game Box64, FEX, resolution and graphics settings are reused.

## Build and artifact

- Source validation: PENDING CI
- Unit tests: PENDING CI
- Android lint: PENDING CI
- Standard debug APK: PENDING CI
- Native arm64-v8a build: PENDING CI
- APK metadata/ABI inspection: PENDING CI
- APK size: PENDING inspection report
- Artifact location: pending successful Phase 2 Actions run.

## Testing and benchmark

- Static source inspection: DONE.
- Physical Helio G100 game test: NOT TESTED.
- Device thermal transitions: NOT TESTED.
- Mali GPU telemetry: NOT TESTED.
- FPS/frame-time/stutter/RAM benchmark: NOT TESTED.
- Emulator gaming comparison: NOT TESTED.
- NO MEASURED IMPROVEMENT: no controlled device benchmark exists yet.

## Stability and compatibility

- Adreno Turnip is not forced on Mali.
- No Vulkan extension is forced.
- No CPU/GPU frequency, voltage, kernel or Android thermal-protection setting is modified.
- Changes are isolated to the Phase 2 feature branch.
- Container operation callbacks are posted after the task attempt; runtime behavior must still be validated on device.

## Rollback

Use feature/helio-g100-mali-optimization to return to the Phase 1 source. Do not merge Phase 2 into main until runtime and physical target-device regression checks are complete.
