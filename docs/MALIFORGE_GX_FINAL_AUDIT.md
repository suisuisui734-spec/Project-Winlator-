# MaliForge GX — Full Execution / Source Audit Report

Audit date: 2026-11-27  
Repository: `suisuisui734-spec/Project-Winlator-`  
Baseline main SHA before this execution: `2cdc79f62626d769922ad6c6a7fc36e9f92ba2af`

## Overall

**PARTIALLY COMPLETE.** Source and test models exist for all 23 stage labels in the dependency chain and PRs #5–#24, #26–#28 are open. This does not mean all stages are fully integrated into the actual game renderer or Android Control Center. Stage 19–23 received additional source/doc/test work during this execution. No feature PR was merged automatically.

## Stage status matrix

| Stage | PR | Implementation evidenced | Tests / CI evidence | Honest status |
|---|---:|---|---|---|
| 1 GX Core | #5 | Isolated engine foundation, capability report, profile model/store, GX-owned cache root, frame timestamp monitor | Covered in later integrated branch CI; PR #5 itself remains open | PARTIAL — source foundation; profile application and real renderer hooks outstanding |
| 2 GPU / Native Runtime | #6 | ARM64 JNI runtime bridge, ABI/API checks, lifecycle/error diagnostics | Stage 3+ descendant workflow passed | PARTIAL — bridge READY is not a GPU renderer or Vulkan ICD |
| 3 Backend Manager | #7 | Registry, descriptors, capability preflight, UNKNOWN/UNSUPPORTED handling, safe current-backend preservation | CI run 45975310790 passed | IMPLEMENTED — TESTS PASS; no game renderer initialization |
| 4 Real Graphics Runtime | #8 | Opt-in Linux x86_64 guest implicit Vulkan diagnostic layer, dispatch/lifecycle logging | CI run 45985034746 passed; software Vulkan layer load and API lifecycle events observed | IMPLEMENTED — SOFTWARE TESTS PASS; Mali device NOT TESTED |
| 5 Shader Laboratory | #9 | GX-owned shader/cache artifact metadata, SHA-256 integrity and recovery models | CI run 45985839396 passed | PARTIAL — GX-owned cache only; not hooked into DXVK/VKD3D pipeline cache creation |
| 6 Framepacing | #10 | Frame-time history, variance, spike/stall analysis | CI run 45985877718 passed | PARTIAL — analyzer exists; not attached to actual game renderer timestamps |
| 7 Performance Engine | #11 | Evidence-based recommendations and profile comparison structures | CI run 45986149316 passed | PARTIAL — recommendations, not automatic driver tuning |
| 8 Adaptive Performance | #12 | Adaptive profile selector with hysteresis/cooldown model | CI run 45986392019 passed | PARTIAL — sensor input only where provided; no Android thermal/power bypass |
| 9 Resolution Engine | #13 | Resolution validation and render-scale advice | CI run 45986531041 passed | PARTIAL — advice model; no forced game/display setting changes |
| 10 Memory Intelligence | #14 | Android system RAM and optional Vulkan heap-budget reporting | CI run 45986697445 passed | PARTIAL — shared RAM is not mislabeled as dedicated VRAM; per-renderer allocation telemetry not wired |
| 11 Compatibility Intelligence | #15 | API/backend/capability requirements, sourced known-issue input, confidence states | CI run 45988103760 rerun passed all steps; initial attempt hit Maven Central HTTP 429 | IMPLEMENTED — TESTS PASS; game database remains caller-supplied/incomplete |
| 12 Game Profiles | #16 | Validated per-game profile catalog, serialization/import/export/recovery model | CI run 45988365600 passed | PARTIAL — profile model/store exists; complete UI/apply workflow remains |
| 13 Graphics Integrity | #17 | Read-only graphics component/configuration integrity model | Cumulative Stage16/17/18 CI runs 45991822391, 45991829787, 45991835091 passed | PARTIAL — model; no full live runtime audit |
| 14 Crash Recovery | #18 | Conservative recovery controller and last-known-good behavior | Cumulative Stage16/17/18 CI runs passed | PARTIAL — recovery model; cannot prevent all crashes |
| 15 Diagnostics | #19 | Redacted, bounded diagnostics bundle | Cumulative Stage16/17/18 CI runs passed | PARTIAL — engine/model; complete integrated export screen not confirmed |
| 16 Benchmark Center | #20 | Evidence-only benchmark session/statistics model | Stage16 CI run 45991822391 passed tests, lint, APK build and package/ELF inspection | PARTIAL — no device/game benchmark measurements |
| 17 Control Center UI | #21 | UI-ready control-center snapshot model | Stage17 CI run 45991829787 passed tests, lint, APK build and package/ELF inspection | PARTIAL — not a complete integrated Android UI |
| 18 Gamepad Integration | #22 | Isolated per-game gamepad mapping model | Stage18 CI run 45991835091 passed tests, lint, APK build and package/ELF inspection | PARTIAL — does not replace or fully wire the Winlator virtual gamepad |
| 19 Configuration Safety | #23 | GX-key allowlist, value validation, immutable snapshot/restore and diff model | Cumulative Stage22 CI run 45991870929 passed | PARTIAL — not yet wired to profile persistence/migrations/UI confirmation |
| 20 Storage Cleanup | #24 | GX-only inventory, preview, explicit confirmation, stale-preview detection, move-to-recovery and restore | Cumulative Stage22 CI run 45991870929 passed | PARTIAL — no permanent cleanup; only known GX cache profile keys |
| 21 Game Advisor | #26 | Compatibility/performance/memory/resolution composition with verified game identity gating | Cumulative Stage22 CI run 45991870929 passed | PARTIAL — no automatic game detection or complete bundled game database |
| 22 CI / Automated Testing | #27 | Branch filters through Stage 23, branch-coverage validator, transient dependency retry, preserved Vulkan/APK checks | Stage22 CI run 45991870929 passed every job step, including tests/lint, APK build and ELF validation | IMPLEMENTED — CI PASS; hosted software-test scope only |
| 23 Git / Release | #28 | Experimental version metadata, changelog, PR/issue templates, install and rollback guidance | Stage23 CI run 45991690545 passed all steps; final ancestry-sync commit run 45993000656 was running at report update | PARTIAL — release documentation exists; no production release |

### Stage 4 software Vulkan evidence

CI run `45985034746` selected Mesa `llvmpipe` (software Vulkan), not Mali. The smoke-test log explicitly recorded:
- `INSTANCE_CREATED`
- `PHYSICAL_DEVICES_ENUMERATED count=1`
- `PHYSICAL_DEVICE_GROUPS_ENUMERATED count=1`
- `DEVICE_CREATED gpu=llvmpipe`
- `DEVICE_DESTROYED`
- `INSTANCE_DESTROYED`

This demonstrates the diagnostic layer loaded and received Vulkan lifecycle/physical-device calls in the Linux software test. It does not demonstrate the layer running inside Android Winlator on Mali-G57 MC2.

## New stage branches and PRs

| Stage | Branch | PR |
|---|---|---:|
| 19 | `feature/maliforge-gx-stage19-config-safety` | #23 |
| 20 | `feature/maliforge-gx-stage20-storage` | #24 |
| 21 | `feature/maliforge-gx-stage21-game-advisor` | #26 |
| 22 | `feature/maliforge-gx-stage22-ci` | #27 |
| 23 | `feature/maliforge-gx-stage23-release` | #28 |

All these PRs are draft/open/unmerged at audit time. The earlier PRs #5–#22 are also still open and unmerged. PR #25 is a separate corrective PR described below.

## Build and test report

- CI build command: selected workflow build, defaulting to `./gradlew :app:assembleStandardDebug --no-daemon --stacktrace`.
- Verified CI integrated branches: Stage 3 (run 45975310790), Stage 4 (45985034746), Stage 5 (45985839396), Stage 6 (45985877718), Stage 7 (45986149316), Stage 8 (45986392019), Stage 9 (45986531041), Stage 10 (45986697445), and Stage 12 (45988365600) all reported success.
- Stage 11 run 45988103760 first failed dependency resolution because Maven Central returned HTTP 429 for JUnit; a rerun then passed unit tests/lint, APK build, APK inspection, guest-layer ELF validation, and artifact upload.
- Stage 4 artifact: `project-winlator-standard-debug-phase4`, 446,681,820 bytes, artifact ID `15190542994`.
- Stage 11 rerun artifact: `project-winlator-standard-debug-phase11`, 446,705,871 bytes, artifact ID `15193780705`.
- Stage 4 and 11 APKs were generated by hosted CI, not built in this local execution session.
- Follow-up CI: Stage2 run 45992087394 passed tests, lint, APK build, package inspection and artifact upload. Stage16 run 45991822391, Stage17 run 45991829787, Stage18 run 45991835091, and Stage22 run 45991870929 also passed all job steps.
- Stage23 run 45991690545 passed tests/lint, APK build, APK package metadata inspection, guest-layer ELF64 x86_64 asset validation and artifact upload. Artifact ID `15193598967` (`project-winlator-standard-debug-stage23-validation`) was 446,732,287 bytes. A follow-up run for the ancestry-synchronized branch tip was running at report update.
- APK app native ABI is ARM64 (`arm64-v8a`); the bundled guest layer asset is a separate Linux x86_64 ELF. They must not be conflated.
- Warnings were present in build logs, including existing C/C++ unused-parameter/sign-compare and third-party linker warnings; they did not fail the verified jobs.

## Performance / hardware report

- Helio G100: NOT TESTED.
- Mali-G57 MC2: NOT TESTED.
- Real Windows game compatibility on this Android device: NOT TESTED.
- Baseline FPS / after-change FPS: NOT MEASURED.
- Frame-time percentiles / stutter improvement: NOT MEASURED on device.
- Thermal and battery measurements: NOT MEASURED.
- Memory use on target hardware: NOT MEASURED.
- Hosted llvmpipe software smoke test is not a device performance benchmark.
- No FPS uplift is claimed.

## Git safety and correction notice

**An automation error occurred during Stage 21:** three Stage 21 files were inadvertently committed directly to `main` because a file-creation call omitted its branch parameter. This was an error, and it is recorded rather than hidden.

- Baseline `main`: `2cdc79f62626d769922ad6c6a7fc36e9f92ba2af`.
- Current `main` at audit: `92d18b233317f6cb224ffa7b01cf49d32b2daa83`.
- Comparing these commits shows exactly three added files and no other path changes: `GXGameAdvisor.java`, `GXGameAdvisorTest.java`, and `MALIFORGE_GX_STAGE21_GAME_ADVISOR.md`.
- Corrective PR #25 (`safety/revert-accidental-stage21-main-files`) removes exactly those three files; it is open, draft, and NOT merged.
- The same corrected Stage 21 implementation exists on the proper feature branch and PR #26.
- No reset, force-push, merge, or history rewrite was performed.
- Until PR #25 is reviewed and merged by the repository owner, `main` still contains those three accidental additions.

The Stage 19–23 feature PR diffs contain additions/modifications only; the deliberate file removals are confined to corrective PR #25. Older Stage 3 PR has line-level deletions in modified files, but no removed file path appeared in the inspected comparison.

## Remaining work / release blockers

1. Review and merge PRs in dependency order; do not merge Stage 2 before Stage 1.
2. Review corrective PR #25 separately to restore the main tree to its prior file set.
3. Verify the Stage 22/23 workflow actually runs on the new PR chain and fix any compile/test/YAML issues it reports.
4. Complete configuration safety integration with persisted GX settings and import/migration UI.
5. Connect telemetry to a real game renderer before presenting real FPS/frame-time data.
6. Connect shader laboratory to actual GX-owned pipeline cache production if such a cache is introduced; do not claim it manages DXVK's cache.
7. Implement and test the full Control Center UI and gamepad integration without replacing existing Winlator controls.
8. Perform Android host / Winlator guest validation on Helio G100/Mali-G57 MC2 with real games, record logs, FPS/frame-time, thermal and memory data.
9. Do not publish as a production release before these checks.

## Final status

**PARTIALLY COMPLETE — source modules and staged PRs exist for all 23 stages, but the PR chain is not merged, the Stage 22/23 CI outcome is not yet verified, multiple features remain model-level/partially integrated, and target hardware/game performance testing has not been performed.**

## Follow-up implementation and validation — 2026-11-27

### CI compilation defect fixed
The first Stage 23 CI attempts failed during Java compilation in
`GXBenchmarkCenter.Session.invalid()`: the call supplied 18 arguments to a constructor
requiring 19. One missing numeric default value was added. The same one-line correction
was applied consistently to Stages 16–23 branches, with the identical blob SHA
`f81102858b73cd62cabfdcab6ed46ac79704050d`.

Verified cumulative CI runs after this fix:
- Stage16: `45991822391` — passed.
- Stage17: `45991829787` — passed.
- Stage18: `45991835091` — passed.
- Stage22: `45991870929` — passed, including the expanded Stage 19–23 coverage check.
- Stage23: `45991690545` — passed the full workflow, including APK build, package inspection,
  x86_64 guest-layer validation and artifact upload.
- The Stage23 branch was then synchronized with the current Stage22 head by a two-parent
  commit `ab19153ee039e885a4b30bd51935bb328ed216db` using the existing Stage23 tree unchanged.
  CI run `45993000656` was in progress at this report update.

### Dependency and source-safety follow-up
- PR #6 base was corrected to Stage1 branch `feature/maliforge-gx-native-engine`.
- A two-parent synchronization commit `2e55e1ae3affed416c4b90ec56454cff0ab351bd`
  preserves the Stage2 tree and records Stage1 head `15e0671c362a158e4943165c8bb1dc2fa88a7b69`
  as its parent. PR #6 is mergeable; PR #7 and PR #8 remain mergeable against the resulting
  dependency chain.
- PR #28 is based on the updated Stage22 branch and is mergeable after the Stage23
  synchronization commit.
- No branch was force-pushed. The ancestry updates were fast-forward ref updates to
  commits created with the existing branch trees.
- Main remained at `92d18b233317f6cb224ffa7b01cf49d32b2daa83` during this follow-up.
  The three previously accidental Stage21 files remain a pre-existing main-branch issue
  tracked by unmerged corrective PR #25; they were not added by this follow-up.

### Remaining integration boundary
The green hosted CI proves the tested software build path and Mesa llvmpipe diagnostic
layer path. It does not verify Mali hardware, Android Winlator guest runtime, actual game
rendering hooks, live frame-time collection, completed Control Center UI, or real gamepad
event routing. Performance metrics remain NOT MEASURED.


## Repository integration update — 2026-11-27 (after PR #29 and PR #30)

The cumulative MaliForge GX implementation and the read-only Control Center are now merged into
`main`. This does **not** mean all 23 stage models are a production game renderer.

- PR #29 merged the cumulative Stage 2–23 integration at merge commit
  `86704a8fc1b5c81e4479791e02be4d8fd7287695`.
- PR #30 merged the read-only Control Center UI at commit
  `0ca14650fe6371f8387bf7b6fb8c21d504d72070`.
- The Control Center is accessible from the app navigation and displays GPU/Vulkan capability
  probes, native bridge status, advisory backend planning, and explicit renderer/telemetry
  availability. It is read-only and does not switch or replace graphics drivers.
- The guest Vulkan layer is opt-in diagnostic pass-through for Linux x86_64 guest processes.
  Software Vulkan smoke tests passed; this is not an Android Mali Vulkan ICD or game renderer.
- PR #31 (`Enable full APK CI validation on main`) adds `main` to push-trigger coverage and
  requires main in the CI coverage validator. At audit update time, run
  `46015590280` was still executing unit tests/lint; APK build and package checks had not yet
  completed. Do not mark this PR verified until its full run concludes successfully.
- Helio G100 / Mali-G57 MC2 device testing and game FPS/frame-time measurements remain
  **NOT TESTED / NOT MEASURED**. No performance increase is claimed.
- Existing Wrapper, Wrapper-1.1, Wrapper-v2, Wrapper-leegao, Wrapper-gamenative, DXVK,
  VKD3D, WineD3D, driver packages, game saves and caches are not replaced by these merges.

Next release gate: full main-branch CI pass, followed by target-device testing and evidence-based
renderer/telemetry integration. Do not expose MaliForge GX as a selectable graphics driver until
a functional backend/ICD or renderer is implemented and validated.
