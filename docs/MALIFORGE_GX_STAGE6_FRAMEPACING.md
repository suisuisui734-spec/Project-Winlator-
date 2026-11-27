# MaliForge GX Stage 6 — Frame Pacing Intelligence

## Implemented

- Preserves the existing bounded monotonic timestamp ring buffer.
- Adds minimum/maximum frame time, population variance, standard deviation, spike count and long-stall count.
- Keeps FPS and frame time as separate metrics.
- Adds a read-only analyzer with UNKNOWN / STABLE / DEGRADED / POOR states.
- Recommendations are advisory and never change backend, resolution, clocks, thermal controls or container configuration.
- Unit tests cover stable cadence, spikes, variance, long stalls, unknown samples and existing stutter metrics.

## Measurement boundary

The monitor only measures timestamps supplied by a caller. No verified game-renderer timestamp hook currently feeds it. Until a real Wine/DXVK/VKD3D/game-present hook exists, results must not be labelled as a Windows game's FPS or frame pacing. Software-generated unit-test samples are test fixtures only and are not hardware benchmark results.

## Status

PARTIAL — frame-time calculations and analysis are implemented and unit-tested by the repository test suite when CI runs. Live renderer telemetry and Helio G100/Mali-G57 measurements remain NOT TESTED.
