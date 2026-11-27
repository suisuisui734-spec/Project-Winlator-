# Native-Hybrid Execution — Repository Audit

Audit date: 2026-11-27 (UTC)

## Repository state observed

- Repository: `suisuisui734-spec/Project-Winlator-`
- Default branch: `main`
- Main before this recovery proposal: `5cecfa1e9da87b640e8da80c7d4435861125e384`
- Previous verified source snapshot: `4ff6362327ea953bdc6c4e03fdfd4ea5bd0de124`

Pull requests #1, #2, and #3 were all reported by GitHub as merged into `main`. PR #3 was merged at 2026-11-27 01:20:51 UTC.

## Regression found

The scheduled workflow `.github/workflows/mirror-winlator.yml` ran at 2026-11-27 06:13:01 UTC and created commit `5cecfa1`. It cloned the upstream `bionic-mali-1.2` source and ran `rsync -a --delete` directly against the checked-out `main` worktree.

Comparing `4ff6362` with `5cecfa1` shows that this import removed project changes which had already been merged, including:

- The `execution` package: PE architecture parser, dependency detector, runtime selector, smart executable detector, and execution manager.
- The `graphics` package: graphics capability detection, Mali backend selection, and conservative fallback components.
- The per-game input profile manager.
- Shared app executors and thermal guard state.
- Project-authored unit tests for PE parsing, dependency selection, graphics fallback, profiles, CPU topology, and thermal behavior.
- The `scripts/validate-source.sh` and `scripts/inspect-apk.py` files.
- Project-specific audit, build, benchmark, and test reports.

The same import also reverted several modifications in existing Java/C++ files, including Vulkan fallback handling and performance/input integrations.

## CI impact

The current `.github/workflows/build-apk.yml` references both:

- `scripts/validate-source.sh`
- `scripts/inspect-apk.py`

Those paths were absent from the post-sync `main` tree. Therefore, the latest scheduled sync's green status is not evidence that the APK workflow passes after the sync.

The last separately verified APK build found during this audit was workflow run [45931085522](https://github.com/suisuisui-spec/Project-Winlator-/actions/runs/45931085522), on commit `4ff6362`. It completed successfully for the AnTuTu release flavor and uploaded artifact `project-winlator-antutu-release-phase2` (artifact ZIP: 444,025,100 bytes). This is an artifact package size, not a measured installed APK size.

The later run [45950434999](https://github.com/suisuisui-spec/Project-Winlator-/actions/runs/45950434999) was the scheduled source-import workflow, not an APK build.

## Recovery and workflow protection

This recovery proposal restores the repository tree from the previously verified `4ff6362` snapshot, preserving the merged project changes, and adds this audit.

It also replaces the destructive scheduled mirror workflow with a manual-only upstream proposal workflow:

- No scheduled write to `main`.
- No direct upstream commit to `main`.
- No `rsync --delete`.
- Each manual upstream import is isolated on a unique branch and proposed as a pull request.
- Shared source-file changes can be reviewed before merge.

The proposal does not merge itself into `main`.

## Native-Hybrid status and limits

The recovered source work provides a foundation, not a fully native Windows execution engine:

- Android host components can run natively as ARM64.
- Windows x86/x64 binaries still require a suitable compatibility/translation path unless an ARM-native build exists.
- The PE preflight/parser work is read-only and does not prove that every launch path is automatically selected; container/DOS-path integration remains a separate task.
- Vulkan capability screening is a conservative baseline, not proof that DXVK/VKD3D works for every game or Mali driver.
- No physical Helio G100 game benchmark was found in the verified reports. FPS, 1% lows, frame-time, GPU utilization, thermals, and sustained performance remain unmeasured on the target device.

## Next engineering priorities

1. Revalidate the restored source tree with CI and ensure the build scripts are present.
2. Integrate PE/runtime preflight safely into all relevant launch paths, including container path resolution.
3. Profile CPU translation overhead before modifying Box64/FEX behavior.
4. Measure Mali renderer performance and Vulkan compatibility on real hardware.
5. Add repeatable frame-time, memory, thermal, and regression benchmarks.
6. Implement adaptive policies only where the runtime exposes controls that can be applied safely and measured.
