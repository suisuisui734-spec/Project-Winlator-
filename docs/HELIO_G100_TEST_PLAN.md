# Helio G100 Test Plan

## Environment

- CI: GitHub Actions `ubuntu-latest`
- Java: Temurin 17
- Android compile SDK: 34
- Build Tools: 35.0.0
- NDK: 29.0.14206865
- CMake: 3.22.1
- Native target ABI: arm64-v8a
- Physical MediaTek Helio G100: NOT TESTED in CI

## Automated checks

| Check | Procedure | Expected result | Actual status |
|---|---|---|---|
| Source layout | `bash scripts/validate-source.sh` | Required Android/native files exist | PASS |
| ARM64 config | Validate `abiFilters` and NDK | arm64-v8a only, requested NDK | PASS |
| JSON assets | Parse Box64/FEX/GPU JSON assets | Valid JSON arrays | PASS |
| Native graph | Validate Mali counters and ZeroFG CMake entries | Targets registered | PASS |
| Unit test task | `:app:testStandardDebugUnitTest` | Task completes | PASS — task succeeded; repository currently contains no `src/test` or `src/androidTest` source files, so no project-authored test cases ran |
| Lint | `:app:lintStandardDebug` | Task completes | PASS |
| APK | `:app:assembleStandardDebug` | Debug APK produced | PASS |
| Native build | Built as part of APK assemble | arm64 native libraries compile | PASS |
| Artifact upload | `actions/upload-artifact@v4` | APK artifact available | PASS |

### Verified GitHub Actions result

- Workflow: Build Project Winlator APK
- Run ID: `45901386995`
- Run result: `SUCCESS`
- Tested source commit: `71804736d1f5dda868ed0b2914fdde587fee5c52`
- Variant: `standardDebug`
- Artifact: `project-winlator-standard-debug`
- Artifact archive size: `446,623,228 bytes` (about 426 MiB)
- Artifact expiry: `2026-12-10`
- Run page: https://github.com/suisuisui-spec/Project-Winlator-/actions/runs/45901386995

The reported size is the GitHub artifact archive size, not a separately measured APK file size. The APK is available from the workflow run's Artifacts section.

## Manual device regression procedure

1. Install the standard debug APK on the Helio G100 device.
2. Confirm first launch and root filesystem installation complete.
3. Open Performance settings and verify device/SoC/ABI/GPU fields. Unavailable metrics must be reported as unavailable.
4. Verify the renderer is identified as Mali where the system exposes it.
5. Create or use a test container with the existing Wrapper driver and BCn ASTC; separately test ETC2 where appropriate.
6. Launch a known-good application, record startup, FPS and frame-time behavior.
7. Verify HUD FPS/GPU/CPU/RAM/thermal fields; inaccessible GPU counters must remain N/A.
8. Enable thermal guard and observe status transitions if the device exposes them. Confirm optional thread-priority and sustained-performance hints pause at Moderate+ and resume at Light/None.
9. Pause, resume and stop the container. Confirm no stale foreground service or performance hints remain.
10. Reboot and verify saved preferences and container settings persist.

All ten device procedures above remain NOT TESTED because a physical Helio G100 test session was not available to this CI run.

## Required benchmark protocol

For a valid before/after comparison, keep the same game build, container, Box64/FEX version and preset, graphics driver, wrapper version, resolution, frame cap, scene, battery range, ambient temperature and background workload. Capture at least three runs per configuration and compare average FPS plus frame-time percentiles/1% lows. Record device and OS build. Do not substitute synthetic figures for device measurements.

## Current results

- Repository inspection: DONE.
- Feature branch created: DONE.
- Source-level changes and documentation: DONE on feature branch.
- Source validation, lint, unit-test task and standard debug APK/native compilation: PASS in workflow `45901386995`.
- Dedicated project-authored unit test cases: NOT PRESENT in repository at this revision.
- Artifact upload: PASS; artifact archive is available from the linked workflow run.
- Helio G100 physical-device test: NOT TESTED.
- Container runtime, HUD thermal transitions, and manual rendering regression: NOT TESTED.
- Performance uplift: NOT MEASURED.
