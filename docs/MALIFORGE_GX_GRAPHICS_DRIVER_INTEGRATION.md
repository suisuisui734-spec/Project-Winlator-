# MaliForge GX Graphics Driver entry

## Current state

The Winlator Graphics Driver selector exposes a diagnostic entry named **MaliForge GX (Unavailable)**. The entry is intentionally disabled and cannot be persisted as a container graphics driver.

The current registered backend identifier is `maliforge-gx-native`, version `0.3.0-stage3-experimental`. Its descriptor reports that no MaliForge game-renderer package is bundled and no safe Wine renderer initialization hook is available. A positive Android host Vulkan probe does not override either missing requirement.

## What this integration does

- Adds the backend's status to the existing per-container Graphics Driver dropdown as a disabled informational item.
- Uses `GXBackendRegistry` and `GXBackendValidator` as the source of truth for availability.
- Displays the backend version and exact preflight reason beneath the selector.
- Keeps the existing Wrapper, Wrapper-1.1, Wrapper-v2, Wrapper-leegao, and Wrapper-gamenative choices unchanged.
- Preserves the existing `Container.isSupportedGraphicsDriver` allow-list. The unavailable GX item is not saved to `graphicsDriver`, does not alter migration behavior, and does not replace a user's selected wrapper.
- Keeps the Control Center distinction between Android host Vulkan capabilities and a Wine game's actual graphics context.

## What is not implemented

This is not a Vulkan ICD, Vulkan layer used as a renderer, OpenGL driver, DXVK replacement, VKD3D replacement, or Wine renderer. It does not initialize a graphics backend or change container runtime libraries. It therefore makes no FPS or game-compatibility claims.

## Requirements before enabling selection

Selection must remain disabled until a redistributable renderer/backend package is integrated, its ABI and dependency licensing are reviewed, a Wine/container initialization hook is implemented, resource/surface/synchronization lifecycle is tested, and initialization can return a verified READY result. The real backend must then pass Android build/ABI checks, guest runtime smoke tests, compatibility tests, and physical-device testing. A host GPU name or extension list alone is insufficient evidence.

## Safety and fallback

Existing driver IDs and archives are not changed. Existing per-container configuration is retained. Until a real backend is available, the existing Winlator driver and DX wrapper selection remains authoritative. MaliForge GX does not silently switch a container to another driver.
