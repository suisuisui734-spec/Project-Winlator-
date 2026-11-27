# MaliForge GX Stage 9 — Resolution Engine

## Implemented

- Adds per-profile render-scale calculations from a caller-supplied source resolution.
- Scaled dimensions are rounded down to even values and preserve the source aspect ratio within rounding tolerance.
- Validates proposed dimensions against supplied display bounds without assuming all Android panels share one aspect ratio.
- Recommends a small 5-point scale reduction only when measured GPU-bound frame-time evidence exists; a 10-point suggestion is possible with measured high memory pressure.
- CPU-bound or unknown workloads do not receive GPU resolution recommendations.
- No container or game resolution is written automatically; recommendations are intended for a separate per-game profile.

## Limits

The engine cannot verify whether an individual Windows game accepts a requested render size or whether it performs internal scaling. The caller must supply a known display mode and game render resolution. No live display/game resolution integration or Helio G100 test is included.

Status: PARTIAL — calculations, validation and advisory logic implemented; game-specific runtime application remains NOT TESTED.
