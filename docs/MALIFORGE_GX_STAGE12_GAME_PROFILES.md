# MaliForge GX Stage 12 — Game Profile Management

## Implemented
- Additive catalog of per-container/per-game profile variants using validated IDs.
- Reuses the existing `GXGameProfile` schema rather than duplicating profile data.
- CRUD and list operations, import/export, format versioning, SHA-256 integrity validation and last-known-good snapshot restore.
- Import validates length, container/game/profile identity, integrity and existing GXGameProfile payload.
- Tests cover isolation, import/export tampering, recovery and invalid identities.

## Limits
- Catalog is in-memory. Persistent profile storage is already provided by `GXProfileStore`, but this catalog's variants are not wired to it yet.
- Backend preferences remain advisory and do not automatically switch Winlator drivers.
- UI/import file picker/export file writer are not integrated.
- No user data or container configuration is changed.

Status: PARTIAL — validated profile management model implemented; persistent catalog integration, UI and CI validation pending.
