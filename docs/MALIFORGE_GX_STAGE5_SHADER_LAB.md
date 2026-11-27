# MaliForge GX Stage 5 — Shader Laboratory

## Implemented

- Per-game and per-profile cache directories under the existing MaliForge-only cache root.
- Artifact metadata for explicitly registered GX cache files: backend, driver version, format version, byte count, timestamp and SHA-256.
- Integrity validation detects missing, unverified, corrupt and unsafe-path states.
- Inventory reports byte usage and verified/unverified/corrupt entries.
- Cleanup moves a selected GX profile cache to a recovery backup instead of deleting it.
- Restore refuses to overwrite an existing profile cache.
- Unit tests cover metadata round-trip, checksum corruption, unknown external files, traversal protection, inventory and backup/restore.

## Integration boundary

This module does not read, modify or clear Wrapper, DXVK, VKD3D, WineD3D, driver or game-owned shader caches. Unknown cache files are reported as UNVERIFIED rather than corrupt. The repository still lacks a verified hook from the game's shader/pipeline cache creation APIs; therefore game-generated shader compilation state, cache hits/misses and real Vulkan pipeline cache statistics are NOT AVAILABLE. The new writer API records only artifacts explicitly passed to MaliForge GX.

## Safety

The API only accepts safe single-component artifact names and writes beneath hashed game/profile directories. It uses checksums for integrity, atomic replacement where supported, and recoverable directory moves for cleanup. There is no automatic deletion of backups or user game data.
