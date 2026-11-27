# MaliForge GX Stage 20 — Storage Cleanup

## Implemented
- Preview-first inventory for MaliForge-owned shader profile caches only.
- Reports artifact counts, integrity states, temporary-file counts, and measured bytes.
- One-use cleanup plans with explicit confirmation and stale-preview revalidation.
- Cleanup moves the selected GX profile directory into the existing GX recovery area.
- Restore is supported and refuses to overwrite a newly-created destination.
- Temporary files are identified and retained inside recovery backups.

## Exclusions and safety
This stage never scans or deletes Winlator container files, game files, save data, Wrapper,
DXVK, VKD3D, Wine, driver packages, general Android caches, or arbitrary filesystem paths.
Only GXShaderLaboratory's dedicated maliforge-gx/shader-cache root is eligible.
A cleanup action is reversible while its recovery backup remains present.

## Tests
- Confirmation is required before an operation.
- A changed cache invalidates an earlier preview.
- Backup and restore preserve GX-owned artifacts.
- Files outside the GX cache root remain untouched.
- Invalid identifiers and empty inventories are handled.

## Limitations
- Inventory is per supplied game/profile identifier; no automatic game discovery is attempted.
- Temporary files are inventoried but not permanently deleted.
- Permanent deletion of old recovery backups is intentionally not implemented.
- Android device storage behavior has not been tested on Helio G100.

Status: IMPLEMENTED — TESTS ADDED; CI PENDING.
