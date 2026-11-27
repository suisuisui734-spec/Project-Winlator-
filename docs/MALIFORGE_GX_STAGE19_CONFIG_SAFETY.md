# MaliForge GX Stage 19 — Configuration Safety

## Implemented
- Validates only GX-owned allowlisted keys and ranges; rejects unknown/non-GX keys.
- Immutable last-known-good snapshots, restore model and configuration diff.
- Target FPS and render-scale range checks.
- Tests for allowlist/ranges, backup immutability and diff validation.

## Limits
- This safety layer is not wired to GXProfileStore/app settings; it does not mutate Container settings.
- Version migration and import/export UI remain outstanding.
- CI pending.

Status: PARTIAL — validation/backup/restore/diff model implemented; storage migration and app wiring pending.
