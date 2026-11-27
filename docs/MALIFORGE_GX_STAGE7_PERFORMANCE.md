# MaliForge GX Stage 7 — Performance Advisor

## Implemented

- Adds an evidence-gated performance advisor driven by caller-supplied frame samples, workload classification, memory telemetry, thermal state and GPU capability confidence.
- GPU render-scale recommendations require a measured GPU-bound classification and at least 75/100 capability confidence.
- CPU-bound workloads do not receive render-resolution reduction recommendations.
- Memory pressure and severe thermal conditions produce conservative advice; nothing changes device clocks, voltage, thermal policy, drivers or profiles automatically.
- Adds before/after session comparison for measured game-renderer or software-GPU sessions only.
- Comparisons require the same source, device and workload identity and at least 30 measured intervals. Backend, resolution, profile and thermal changes are reported as comparison-condition differences.
- Synthetic sessions are rejected rather than represented as hardware benchmark results.

## Measurement boundary

The advisor itself does not collect hardware metrics. It only evaluates values supplied by a caller. No game renderer hook or Helio G100/Mali-G57 live telemetry source is verified in this repository. Missing data must be represented as unknown; a recommendation is not proof that a setting improves performance.

## Status

PARTIAL — recommendation and comparison logic implemented; live telemetry sources and device validation remain unimplemented/NOT TESTED.
