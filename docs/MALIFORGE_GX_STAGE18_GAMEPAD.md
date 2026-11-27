# MaliForge GX Stage 18 — Gamepad Profile Model

## Implemented
- Additive per-container/game mapping storage model.
- Validates input/action identifiers and rejects malformed mappings.
- Immutable profile snapshot and delete isolation.
- Tests cover identity separation, invalid mappings and snapshot safety.

## Limits
- No integration with Winlator virtual gamepad events, touchscreen overlay, focus routing, or actual game input.
- Mapping model does not override existing global or container mappings.
- No UI editor/import-export yet; CI pending.

Status: PARTIAL — mapping model implemented; actual input integration remains NOT IMPLEMENTED.
