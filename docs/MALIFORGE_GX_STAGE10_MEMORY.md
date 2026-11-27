# MaliForge GX Stage 10 — Memory Intelligence

## Implemented

- Collects Android system RAM telemetry through ActivityManager.MemoryInfo when the application supplies a Context.
- Classifies pressure as UNKNOWN / NORMAL / MODERATE / HIGH / CRITICAL using total/available memory and Android's low-memory flag.
- Adds an optional Vulkan heap-budget evaluator for callers that have actually queried VK_EXT_memory_budget.
- Reports dedicated VRAM as UNKNOWN; device-local heaps and shared memory are not labelled as dedicated VRAM.
- Returns conservative recommendations only; no cache, game, save or container data is deleted.
- Unit tests cover normal/high/critical/unknown cases, Vulkan budget support and the dedicated-VRAM guard.

## Limits

The Vulkan diagnostics layer in Stage 4 does not yet collect VK_EXT_memory_budget data, so GPU budget reports remain UNSUPPORTED until a real loader/device query provider is integrated. Android system RAM is not guest-process RSS or VRAM. Memory reports are snapshots, not continuous sampling.

Status: PARTIAL — Android system RAM collector and capability-safe model implemented; Vulkan heap telemetry is not yet wired.
