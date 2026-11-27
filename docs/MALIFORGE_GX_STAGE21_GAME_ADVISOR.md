# MaliForge GX Stage 21 — Game Advisor

## Implemented
- Composes the existing compatibility, performance, memory, and resolution advisors.
- Accepts an explicit game identity and a caller-supplied known-issue list; it does not guess a game or ship an unverified database.
- Requires verified identity before attaching game-specific known-issue records.
- Explanations include compatibility status, evidence quality, performance telemetry requirements, memory state, and resolution bounds.
- Overall confidence is conservative and reduced when required evidence is missing.
- Returns recommendations only; it never applies renderer, driver, resolution, thermal, or profile changes.

## Tests
- Unknown game identity and missing telemetry remain low confidence.
- Known issue records are not attached unless identity has been verified.
- Measured telemetry can produce recommendations without applying settings.

## Limitations
- Automatic executable/game detection is not integrated into this advisor.
- No bundled compatibility database is asserted as complete.
- Game compatibility is not proven by backend initialization alone.
- Hardware tests on Helio G100/Mali-G57 MC2: NOT TESTED.

Status: IMPLEMENTED — UNIT TESTS ADDED; CI PENDING.
