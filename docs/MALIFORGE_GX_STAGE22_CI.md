# MaliForge GX Stage 22 — CI and Automated Testing

## Implemented
- Extends push and pull-request workflow filters through Stages 19–23 and adds the missing Stage 12–18 pull-request base filters.
- Adds a source-level validator that confirms the latest stage branches are covered by both push and pull-request triggers.
- Preserves existing source validation, Linux software Vulkan layer build/smoke-test, Java 17 setup, Android SDK setup, unit tests, lint, APK build, APK metadata inspection, x86_64 ELF asset validation, and artifact upload.
- Adds up to two retries only when Gradle reports a transient HTTP 429/5xx or dependency GET failure; unrelated test/build failures still fail immediately.
- Renames uploaded artifact to identify the Stage 23 validation workflow.

## What CI actually proves
- Host Linux software Vulkan smoke-test and compilation, where the job executes successfully.
- Android APK compilation and packaging, unit tests/lint, and ARM64/x86_64 asset metadata checks.
- Source/branch-filter consistency.

## What CI does not prove
- Runtime operation on an Android Mali GPU.
- Winlator guest compatibility for every Windows game.
- FPS uplift, thermal behavior, game frame pacing, or crash-free operation on Helio G100.
- A Linux x86_64 guest Vulkan layer is not an Android ARM64 Mali driver.

## Limitations
- Android device tests remain NOT TESTED.
- CI uses GitHub-hosted Linux and software Vulkan; it is not a substitute for Mali-G57 MC2 validation.

Status: IMPLEMENTED — CI WORKFLOW UPDATED; RUN RESULT PENDING.
