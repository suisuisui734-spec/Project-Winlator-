# Project Winlator Functional Feature Roadmap

This roadmap prioritizes user-facing functionality that can be verified, rather than decorative settings or claims of performance.

## Feature principles
- Additive changes only: preserve existing driver wrappers, graphics settings, container files, game saves, and shader caches.
- Settings must have a real consumer and a safe default; unsupported device APIs must degrade gracefully.
- Do not promise FPS gains without reproducible device/game measurements.
- Every feature should include a test, diagnostic state, and a rollback path where it changes configuration.

## Proposed feature batches

### F1 — Diagnostics & support (started)
- [x] MaliForge GX report copy/share actions; user reviews device information before sharing.
- [ ] Structured diagnostic export with app/build/ABI, Android API, driver selections, and sanitized log excerpt.
- [ ] Support bundle preview and redaction toggles before export.
- [ ] One-tap copy of concise environment summary from the main app.

### F2 — Container profiles & recovery
- [ ] Per-container compatibility profiles with explicit change preview.
- [ ] Backup/restore container settings metadata (never silently overwrite Wine prefix or saves).
- [ ] Snapshot/restore selected settings and wrapper choices.
- [ ] Profile validation and import/export with schema versioning and checksums.

### F3 — Launch & failure feedback
- [ ] Recent launch history and exit status, with opt-in logging.
- [ ] Error explanations mapped from common Wine/Box64/FEX/runtime failures.
- [ ] Retry using a user-confirmed compatibility fallback; do not automatically change the default driver.
- [ ] Launch checklist for missing dependencies and unsupported configurations.

### F4 — Performance and thermal usability
- [ ] Real measured FPS/frame-time telemetry only when a verified provider is available.
- [ ] Thermal status, battery state, and Android performance mode visibility.
- [ ] User-selectable balanced/compatibility presets, each with clear explanation and reversible changes.
- [ ] Session summary comparing observed frame-time/thermal metrics; no fabricated GPU load or clock data.

### F5 — Storage and maintenance
- [ ] Storage usage by container, prefix, cache, and imported content.
- [ ] Safe cache-cleaning preview that excludes saves, prefixes, and user files by default.
- [ ] Duplicate/import detection for game shortcuts and configs.
- [ ] Background maintenance with cancel/progress and clear failure reporting.

### F6 — Controls and accessibility
- [ ] Per-game control layouts with backup and restore.
- [ ] Controller mapping test, dead-zone preview, and profile sharing.
- [ ] Touch-layout scaling and opacity controls with preview.
- [ ] Accessibility improvements: readable labels, keyboard navigation, larger touch targets.

### F7 — Graphics runtime & MaliForge GX
- [ ] Host Vulkan probe and logical-device bootstrap (Stages 36–37; diagnostics only).
- [ ] Resource/memory allocation tests, limits, and cleanup.
- [ ] Command-buffer/offscreen test harness.
- [ ] Shader/pipeline test harness.
- [ ] Renderer integration only after prerequisites and device validation.
- [ ] Game compatibility tests across Android devices and GPUs before availability claims.

## Delivery tracking
Work should be split into small pull requests, run CI on each branch, review diffs, and merge only passing changes. Device validation is separately tracked from CI and must not be inferred from a successful APK build.
