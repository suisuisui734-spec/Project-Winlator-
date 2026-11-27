# MaliForge GX — Phase 1 Foundation

## Scope

This change introduces an isolated MaliForge GX user-space control and diagnostics foundation. It does not replace an existing Wrapper, Vulkan ICD, Wine renderer, DXVK, VKD3D, or system GPU driver.

## Implemented in this phase

- Read-only GPU/Vulkan capability report built on the existing GraphicsCapabilityDetector.
- Conservative backend recommendation built on the existing GraphicsBackendSelector.
- A per-game profile model with ECO, BALANCED, PERFORMANCE, COMPATIBILITY, and CUSTOM presets.
- A separate SharedPreferences store for MaliForge profiles.
- A separate last-known-good profile store for recovery planning.
- A bounded frame timestamp sampler for future renderer instrumentation.
- A MaliForge-owned cache directory manager. It never deletes or modifies caches belonging to existing wrappers, DXVK, VKD3D, Wine, or games.
- Unit tests for profile validation/serialization, frame pacing statistics, and capability-gated backend planning.

## Deliberate integration boundary

The current Winlator graphics-driver selector is not a free-form label list. Its entries map to Container.WRAPPER_DRIVERS and packaged assets at app/src/main/assets/graphics_driver/<id>.tzst. Those archives contain the runtime files loaded for the selected driver.

There is no MaliForge GX Vulkan ICD/runtime archive in this change. Adding a MaliForge GX label to graphics_driver_entries or WRAPPER_DRIVERS without a working runtime package would create a selectable option that cannot initialize correctly and could prevent games from starting. Therefore this phase does not add a fake dropdown entry.

The next runtime milestone must provide a real, license-compatible Vulkan integration package or a validated Vulkan layer/runtime with the required loader wiring, ARM64 build, and device tests. Only then should the driver be registered in the selector.

## Truthful capability limits

- MaliForgeGXEngine.inspect(...) is read-only and returns diagnostics/recommendations.
- Backend plans require manual confirmation and are not applied to a running container.
- GXFramePacingMonitor needs timestamps from an actual rendering hook; it is not yet connected to a game's render loop or HUD.
- GXGameProfile values are stored separately but are not yet applied to Wine/renderer environment variables.
- GXShaderCacheStore manages only the future MaliForge-owned cache directory. It is not yet connected to Vulkan pipeline-cache creation.
- No FPS uplift, 1% low improvement, crash reduction, or physical Helio G100 result is claimed by this foundation.

## Source protection

This phase adds new files only under app/src/main/java/com/winlator/cmod/maliforge/gx, its matching unit-test package, and this document. It does not edit existing driver packages, the driver selector, Container, EmulationService, existing renderer sources, existing workflows, or previous optimization modules.
