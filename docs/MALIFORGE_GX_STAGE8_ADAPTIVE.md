# MaliForge GX Stage 8 — Adaptive Profile Controller

## Implemented

- Adds adaptive profile candidate selection for ECO, BALANCED and COMPATIBILITY outcomes.
- Uses frame-pacing status, thermal state, memory-pressure information, backend failure and safe-mode signals.
- Normal transitions require repeated matching observations and a configurable cooldown.
- Manual override blocks adaptive suggestions.
- High-priority thermal, memory or backend-failure warnings may produce an immediate recommendation, but the controller never applies it automatically.
- No frequency, voltage, power, governor or Android thermal controls are accessed.
- Unit tests cover hysteresis, cooldown, emergency recommendation, manual override, unknown metrics and monotonic time.

## Limits

This is a recommendation controller, not an autonomous hardware tuner. It does not read thermal/memory sensors itself, and no game renderer telemetry hook is verified. The calling application must collect trustworthy inputs and ask the user before persisting a changed profile.

Status: PARTIAL — controller logic and tests implemented; live sensors, profile-application UI and hardware validation remain NOT TESTED.
