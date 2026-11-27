# Project Winlator

<p align="center">
  <strong>Android Windows Gaming Environment · ARM64</strong><br/>
  <em>Independent Project Winlator development with MaliForge GX graphics diagnostics and runtime work.</em>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white" alt="Android"/>
  <img src="https://img.shields.io/badge/Architecture-ARM64--v8a-8B5CF6" alt="ARM64"/>
  <img src="https://img.shields.io/badge/Graphics-Vulkan%20diagnostics-00B8D9" alt="Vulkan diagnostics"/>
  <img src="https://img.shields.io/badge/Project-MaliForge%20GX-F59E0B" alt="MaliForge GX"/>
</p>

## About

Project Winlator is an Android ARM64 project based on the Winlator Mali (Bionic Edition) source tree. It is evolving with independent branding, Android UI improvements, diagnostics, and additive MaliForge GX graphics/runtime development.

The goal is to provide tools and runtime integration for running Windows software and games on Android. Compatibility and performance vary by device, game, driver, and container configuration.

> **Development status:** CI/build success is not equivalent to physical-device testing or proof that a game runs correctly.

## Project identity

The app is adopting a distinct navy, cyan, and amber visual identity. Launcher icon resources, drawer cover/header, palette, toolbar, and navigation styling have been refreshed. The full screen redesign is still in progress.

## Features and status

### App
- Android ARM64 source project based on Winlator Mali (Bionic Edition).
- Project Winlator branding and refreshed navigation.
- Existing drivers, wrappers, and defaults are to be preserved; new work should be additive and reversible.

### MaliForge GX
MaliForge GX is the graphics diagnostics and native-runtime integration workstream.

| Component | Status |
|---|---|
| Host Vulkan diagnostics and GPU/driver information | Implemented in code; CI validation |
| Vulkan instance creation and GPU/queue-family discovery | Implemented |
| Minimal host Vulkan logical-device and graphics-queue bootstrap | Implemented diagnostic/bootstrap path |
| Host Vulkan command-buffer buffer-copy smoke test | Implemented in native runtime; device result depends on execution |
| Diagnostics report copy/share | Implemented |
| Guest Vulkan renderer / complete game rendering path | Not complete or verified |
| Surface/swapchain, shaders, full guest ICD integration | Not yet complete |
| Physical Mali/MediaTek device testing | Pending |

These features do **not** mean a complete Vulkan renderer or guest graphics driver is ready. Device compatibility, stability, and performance require real-device validation.

## Functional roadmap

- **Diagnostics:** structured report/export, privacy-aware information handling, environment summary.
- **Profiles & recovery:** compatibility profiles, configuration snapshots, safe profile import/export.
- **Launch troubleshooting:** launch status, common error explanations, optional recovery.
- **Performance & thermal:** measured telemetry and reversible presets; no unverified FPS claims.
- **Storage:** usage visibility and safer maintenance.
- **Controls:** layout backup/restore, controller checks, accessibility.
- **Graphics runtime:** staged tests and integration after prerequisites and device validation.

See [Functional Feature Roadmap](docs/PROJECT_WINLATOR_FUNCTIONAL_FEATURE_ROADMAP.md) and the documentation in [docs](docs/).

## Installation

No verified Project Winlator release APK is linked here yet. When releases are published, use the repository **Releases** page and review release notes and compatibility details.

Do not assume an APK from an upstream Winlator Mali release is a Project Winlator build.

## Build from source

Clone this repository:

```bash
git clone https://github.com/suisuisui734-spec/Project-Winlator-.git
cd Project-Winlator-
./gradlew tasks
```

Use the Gradle tasks and CI workflow files to confirm the current Android SDK, JDK, NDK, build variants, and APK output paths. On Windows, use `gradlew.bat`.

## Compatibility and testing

- Platform: Android.
- Primary architecture: ARM64 (arm64-v8a).
- Compatibility depends on device, Android version, GPU, driver, and game.
- CI validates build integration but cannot replace physical-device tests.
- Performance gains are not guaranteed; claims require repeatable measurements.

## Development principles

Preserve upstream source modifications, drivers, wrappers, and existing defaults. Prefer additive, reversible changes and document any user-selectable option and its impact.

## Roadmap

- [x] Project Winlator branding foundation (launcher resources, palette, drawer cover/header, toolbar and navigation)
- [x] Vulkan host diagnostics/preflight
- [x] Minimal host Vulkan logical-device bootstrap
- [x] Diagnostics report copy/share
- [ ] Full dashboard and library redesign
- [ ] Profiles, recovery, troubleshooting, storage and controls features
- [x] Host Vulkan command-buffer transfer smoke test (buffer copy, queue submit, fence and data verification)
- [ ] Offscreen Vulkan image rendering, shader/pipeline test harness and game-renderer integration
- [ ] Renderer/runtime validation on physical devices
- [ ] Publish verified releases and device-specific test reports

## Credits and upstream

Project Winlator builds on Winlator Mali/Bionic and upstream Winlator components. Third-party components retain their respective licenses and attribution. Refer to source headers, bundled license files, and upstream documentation for specific credits and licensing.

---

<p align="center"><strong>Project Winlator</strong><br/>Android ARM64 Windows gaming development</p>
