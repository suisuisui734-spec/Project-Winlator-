# Phase 2 Benchmark Baseline

## Test availability

- Helio G100 physical device: NOT AVAILABLE to this execution.
- Original repository build: NOT BENCHMARKED on device.
- Phase 1 optimized build: CI artifact exists, but no game benchmark has been run.
- Phase 2 optimized build: pending Phase 2 CI.
- Emulator gaming benchmark: NOT RUN; an emulator is not a substitute for Helio G100 Mali behavior.

## Required three-build comparison

| Metric | Original repository | Phase 1 | Phase 2 |
|---|---|---|---|
| Average FPS | NOT TESTED | NOT TESTED | NOT TESTED |
| Minimum FPS | NOT TESTED | NOT TESTED | NOT TESTED |
| Frame-time average / p95 / p99 | NOT TESTED | NOT TESTED | NOT TESTED |
| Frame-time variance | NOT TESTED | NOT TESTED | NOT TESTED |
| Stutter events | NOT TESTED | NOT TESTED | NOT TESTED |
| CPU utilization | NOT TESTED | NOT TESTED | NOT TESTED |
| GPU utilization | NOT TESTED | NOT TESTED | NOT TESTED |
| RAM during game | NOT TESTED | NOT TESTED | NOT TESTED |
| Container startup | NOT TESTED | NOT TESTED | NOT TESTED |
| Game loading time | NOT TESTED | NOT TESTED | NOT TESTED |
| Thermal status / degradation | NOT TESTED | NOT TESTED | NOT TESTED |

No improvement is inferred from static changes or successful compilation.

## Reproducible protocol

1. Use the same physical Helio G100 phone, OS build, game version and save.
2. Reset device to the same thermal starting condition. Use the same battery range, charging state, ambient temperature and background apps.
3. Use the same container image, prefix, Wine version, Box64/FEX version, graphics driver/wrapper, DXVK/VKD3D settings, resolution, refresh rate and frame cap.
4. Select one repeatable scene and scripted path. Do not change scenes between builds.
5. Capture at least three 180-second runs per build after a warm-up run. Save native HUD frame-time samples where available.
6. Record average FPS, 1%/0.1% lows where enough samples exist, frame-time p50/p95/p99, frame-time standard deviation, stutter count, app/guest memory, CPU/GPU metrics if exposed, startup times and thermal status.
7. Compare the median of the three runs and retain every raw CSV. Reject changes that improve average FPS while materially worsening p99 frame time, crash rate or thermal degradation.
8. Repeat the same protocol for original, Phase 1 and Phase 2 builds. Never compare different scenes/configurations.

## Definitions

- Average FPS: game-rendered frames divided by elapsed seconds; generated frames must be reported separately.
- Frame time: milliseconds between consecutive game frames, not HUD refresh intervals.
- Stutter event: choose a threshold before testing (recommended: frame time above twice the target interval) and keep it identical for all builds.
- RAM: report process PSS/RSS and total system memory separately.
- CPU/GPU usage: report only values from an identified readable source; unavailable values must remain unavailable.
