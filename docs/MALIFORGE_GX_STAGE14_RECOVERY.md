# MaliForge GX Stage 14 — Crash/Failure Recovery Model

## Implemented
- Failure categories for initialization, backend, configuration and unknown failures.
- Recovery states NORMAL, SAFE_MODE and RECOVERY_REQUIRED.
- Backend/initialization failures disable registered experimental flags in this controller.
- Recommends restore-last-known-good when available, otherwise diagnostics-first retry.
- Safe mode disables experimental flags and validated recovery can exit safe mode.
- Error summaries are sanitized and bounded; reports expose immutable flag snapshots.

## Limits
- Failure sources and persistent state are not wired into actual app lifecycle/backend initialization.
- Controller does not perform rollback, restart game processes or mutate driver/container configuration; it only reports safe recommendations.
- Tests/build/CI pending.

Status: PARTIAL — recovery state/controller implemented; persistent failure integration and UI actions remain unimplemented.
