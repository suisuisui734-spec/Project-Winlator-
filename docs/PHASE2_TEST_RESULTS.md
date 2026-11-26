# Phase 2 Automated Test Results

## Test scope

Phase 2 adds pure unit tests for CPU affinity-mask formatting and thermal hysteresis. They do not require a physical Helio G100 sensor or GPU.

## Test cases added

- CpuTopologyTest: core indices 0 and 8, index 31, null/negative/out-of-range indices, empty input.
- ProcessHelperTest: verifies affinity formatting preserves bits above bit 7 and the highest int bit.
- ThermalGuardStateTest: Moderate+ pauses optional hints; Light/None resumes; intermediate status preserves state; repeated transitions do not oscillate inside the warning band.

## Results

PENDING until the Phase 2 GitHub Actions workflow finishes. CI is configured to run:
- bash scripts/validate-source.sh
- :app:testStandardDebugUnitTest
- :app:lintStandardDebug
- :app:assembleStandardDebug (includes native arm64 compilation)
- APK package/ABI/resource inspection and artifact upload

## Device-only tests

- Thermal callbacks against actual device state: NOT TESTED.
- Mali telemetry permissions/counters: NOT TESTED.
- Container launch/stop on Helio G100: NOT TESTED.
- Game graphics regression: NOT TESTED.
- FPS/frame-time/thermal benchmark: NOT TESTED.
