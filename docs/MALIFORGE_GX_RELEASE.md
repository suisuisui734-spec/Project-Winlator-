# MaliForge GX — Release and Operations Guide

## Release identity

- Version: `0.1.0-experimental`
- Channel: EXPERIMENTAL
- Scope: additive diagnostics, configuration models, software-testable support logic, and Winlator integration experiments.
- This release metadata does not mean a GPU backend or game renderer is production-ready.

## Architecture and ABI boundaries

The Android application is built for ARM64 (`arm64-v8a`) in the current project configuration. The Stage 4 Vulkan diagnostic layer is a separate Linux guest-side `x86_64` ELF asset intended for an x86_64 Wine/Winlator guest environment. These are different execution environments:
- Android host process and native app libraries: ARM64.
- The opt-in guest Vulkan layer: Linux x86_64 guest userspace.
- Mali-G57 MC2 is the physical Android GPU behind the existing system driver path; it is not replaced by this x86_64 layer.

The layer is a diagnostic pass-through POC, not an ICD, standalone renderer, Mali driver, or replacement for DXVK/VKD3D/WineD3D. It must remain opt-in. Do not manually force it for a game unless the runtime and architecture match the documented POC.

## PR dependency chain

The experimental implementation is submitted as chained PRs:
- #5 Stage 1 → #6 Stage 2 → #7 Stage 3 → #8 Stage 4
- #9 Stage 5 → #10 Stage 6 → #11 Stage 7 → #12 Stage 8 → #13 Stage 9 → #14 Stage 10
- #15 Stage 11 → #16 Stage 12 → #17 Stage 13 → #18 Stage 14 → #19 Stage 15 → #20 Stage 16 → #21 Stage 17 → #22 Stage 18
- #23 Stage 19 → #24 Stage 20 → #26 Stage 21 → #27 Stage 22 → #28 Stage 23

These PRs are not automatically merged. Check that each base dependency has been reviewed and accepted before integrating its child. PR #25 is a corrective review PR removing three mistakenly committed Stage 21 files from main; it is not part of the feature chain. Review that PR separately before considering main clean.

## CI and artifacts

The Stage 22 workflow is designed to validate:
1. Repository source structure.
2. MaliForge-specific stage/CI coverage.
3. Linux build and software Vulkan smoke test for the x86_64 guest diagnostic layer.
4. Android unit tests and lint.
5. Android APK assembly and APK metadata inspection.
6. ARM64 APK native ABI and guest-layer x86_64 ELF asset checks.
7. Artifact upload with a Stage 23 validation label.

A hosted Linux software Vulkan test is not a Mali-G57 MC2 hardware test. An APK artifact does not prove real-game compatibility or performance.

## Installation guidance

Do not distribute this experimental PR chain as a production release APK. For developer validation only:
1. Review and merge dependency PRs in order after their CI and source audits pass.
2. Obtain the APK only from the intended CI run and verify its commit SHA and artifact metadata.
3. Install only on a test device/container with a backup of important profiles and data.
4. Keep the existing Winlator driver and wrapper selection unchanged initially.
5. Leave experimental Vulkan layer activation disabled unless the target guest is x86_64 and the Stage 4 diagnostics are explicitly being evaluated.
6. Record logs and reproduce with the existing GX feature flags disabled before reporting a regression.

No APK is attached to this PR unless the matching CI run successfully generates one.

## Rollback

- Disable the opt-in MaliForge GX feature or guest layer before changing existing Winlator settings.
- Restore the GX last-known-good profile using the recovery model.
- For GX shader cache maintenance, use preview/confirm; Stage 20 moves data into a recovery backup rather than permanently deleting it.
- Restore a backup only when the destination profile cache is absent; never overwrite a newer cache.
- Re-select the previously working Winlator graphics driver/DX wrapper using the existing Winlator UI if the user intentionally changed it. MaliForge GX does not own or replace those components.
- If the application itself is unstable, return to the last known working APK and retain diagnostics for review. Do not delete game saves, driver archives, wrappers, or container data.

## Validation status at this version

- Stage 4 Linux software Vulkan smoke-test and APK x86_64 asset validation: PASS in hosted CI.
- Stage 3 and Stage 5 hosted CI jobs: PASS.
- Stage 11 hosted CI: initial attempt hit Maven Central HTTP 429; rerun completed successfully, including unit tests/lint, APK build, APK inspection, guest-layer ELF validation, and artifact upload (run ID 45988103760).
- Stage 22/23 workflow changes: PR review is open; no successful run for the extended branch-coverage workflow has been confirmed yet.
- Other stage-specific CI runs: verify from their individual PR/commit checks; do not infer a pass from the existence of a PR.
- Real Android host tests on Helio G100/Mali-G57 MC2: NOT TESTED.
- Real game FPS, frame-time, stutter, thermal, and memory benchmarks: NOT MEASURED.
- Full live UI/gamepad mapping integration and native MaliForge renderer: PARTIAL / NOT IMPLEMENTED.

Overall: PARTIALLY COMPLETE — experimental PR chain requires review, full CI reruns, and physical device/game validation.
