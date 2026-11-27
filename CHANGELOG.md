# Changelog

All notable MaliForge GX changes are documented here. This is an experimental development track;
entries describe source and validation scope, not guaranteed user-visible performance improvements.

## 0.1.0-experimental — 2026-11-27

- Stage 1–3: isolated GX foundation, capability reporting, and capability-gated backend registry.
- Stage 4: opt-in guest Vulkan diagnostics layer proof-of-concept; Linux x86_64 software smoke test and APK asset inspection passed in CI.
- Stage 5–10: GX-owned shader cache integrity model, frame-time analysis, evidence-based performance recommendations, adaptive profile logic, resolution advice, and memory reports.
- Stage 11–18: compatibility evidence, per-game profile catalog, read-only graphics integrity, recovery controller, redacted diagnostics, evidence-only benchmark statistics, UI-ready control-center data model, and isolated gamepad mapping model.
- Stage 19: GX-owned configuration validation, immutable snapshots, restore model, and diff model.
- Stage 20: preview-first inventory and reversible GX shader-cache cleanup.
- Stage 21: evidence-gated game advisor.
- Stage 22: CI branch coverage and software validation gates.
- Stage 23: release metadata, source-audit templates, and installation/rollback documentation.

### Verification limits
- The Stage 4 hosted-Linux Vulkan software smoke test and debug APK packaging validation passed.
- Stage 11 CI encountered Maven Central HTTP 429 while resolving JUnit; this is a dependency service failure, not evidence that the Java tests passed.
- Android Mali-G57 MC2 / Helio G100 device runtime, real Windows game compatibility, thermal behavior, and FPS uplift are NOT TESTED / NOT MEASURED.
- No new MaliForge game renderer or Vulkan ICD is included.
- Stages 1–23 are not all merged into main; they remain a reviewable dependency chain of pull requests.
