# Phase 2 Benchmark Baseline

## Test availability

- Helio G100 physical device: NOT AVAILABLE.
- Original repository build: NOT BENCHMARKED on device.
- Phase 1 optimized build: CI APK exists, but no game benchmark was run.
- Phase 2 optimized build: APK/native CI build passes, but no game benchmark was run.
- Emulator gaming comparison: NOT RUN; an emulator does not reproduce Helio G100 Mali GPU behavior.

## Three-build comparison

| Metric | Original repository | Phase 1 | Phase 2 |
|---|---|---|---|
| Average FPS | NOT TESTED | NOT TESTED | NOT TESTED |
| Minimum FPS | NOT TESTED | NOT TESTED | NOT TESTED |
| Frame-time average / p50 / p95 / p99 | NOT TESTED | NOT TESTED | NOT TESTED |
| Frame-time variance | NOT TESTED | NOT TESTED | NOT TESTED |
| Stutter events | NOT TESTED | NOT TESTED | NOT TESTED |
| CPU utilization | NOT TESTED | NOT TESTED | NOT TESTED |
| GPU utilization | NOT TESTED | NOT TESTED | NOT TESTED |
| App/guest RAM | NOT TESTED | NOT TESTED | NOT TESTED |
| Container startup | NOT TESTED | NOT TESTED | NOT TESTED |
| Game loading time | NOT TESTED | NOT TESTED | NOT TESTED |
| Thermal status / degradation | NOT TESTED | NOT TESTED | NOT TESTED |

No performance gain is inferred from source changes or a successful CI build.

## Reproducible protocol

1. Use the same physical Helio G100 phone, OS build, game binary/version, save and container image.
2. Start from the same thermal state; keep battery range, charging state, ambient temperature, refresh rate and background apps consistent.
3. Keep Wine, Box64/FEX version and preset, graphics driver/wrapper, DXVK/VKD3D, BCn settings, resolution, frame cap and game settings identical.
4. Choose one repeatable scene and scripted route. Do not change scenes between builds.
5. Run one warm-up followed by at least three 180-second runs for each build.
6. Record average FPS, reliable low percentiles, frame-time p50/p95/p99, standard deviation, stutter count, process PSS/RSS, CPU/GPU metrics if exposed, startup times and thermal status.
7. Compare the median of three runs. Reject a change if average FPS rises but p99 frame time, crash rate or thermal degradation materially worsens.
8. Repeat the same procedure for original, Phase 1 and Phase 2 builds.

## Definitions

- Average FPS: game-rendered frames divided by elapsed seconds; generated frames must be reported separately.
- Frame time: time between consecutive game frames, not HUD sampling intervals.
- Stutter event: define a threshold before testing (for example, more than twice the target frame interval) and keep it identical across builds.
- RAM: report process PSS/RSS and whole-device memory separately.
- GPU/CPU load: report only a named, readable metric source. Use NOT AVAILABLE if OEM permissions block counters.

## Current result

- FPS/frame-time/stutter improvement: NO MEASURED IMPROVEMENT.
- Thermal stability on target phone: NOT TESTED.
- Native and configuration build checks: PASS in CI.
