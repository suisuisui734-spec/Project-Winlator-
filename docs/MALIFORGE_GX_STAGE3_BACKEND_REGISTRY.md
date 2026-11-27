# MaliForge GX Stage 3 — Capability-Gated Backend Registry

## Branch and dependency

Stage 3 is based on Stage 2 head `c4669cc687988560d17bef3d9c01c5a2483d5602`, not on `main`.
It depends on PR #5 (Foundation) and PR #6 (ARM64 JNI bridge), which must be reviewed and
merged in order before this branch can be integrated. Stage 3 does not merge either dependency.

## Repository architecture observed

The current graphics flow is distributed across these existing components:

```text
Winlator UI (ContainerDetailFragment)
  ↓ per-container settings
Container model / JSON
  - graphicsDriver
  - graphicsDriverConfig
  - dxwrapper
  - dxwrapperConfig
  ↓ existing package and loader path
graphics_driver/<id>.tzst + adrenotools / Vulkan loader
and Wine DX wrapper packages
  ↓ Windows API translation
DXVK / VKD3D / WineD3D
  ↓ Vulkan / Wine's existing OpenGL-compatible path
Android system graphics stack / selected Vulkan driver
```

The exact runtime paths differ by backend and are not represented by a single MaliForge
renderer interface today. The app has a separate native `EGLRenderer` used by the X-server
window compositor. That compositor creates its own EGL display/context and swaps the
composited desktop surface. It is not the game's Wine renderer context; Stage 3 does not
reuse it, create a competing context, or add hooks to its lifecycle.

## Implemented

- Registry descriptors with stable IDs, type, version, required API/version/extensions,
  package metadata, initializer availability, selectability, fallback capability and notes.
- Three-state capability facts: SUPPORTED, UNSUPPORTED, UNKNOWN.
- Vulkan version and extension preflight using the existing read-only GPUInformation probe.
- Unknown extension lists remain UNKNOWN instead of being treated as a complete empty list.
- Game-context OpenGL ES capability remains UNKNOWN; compositor GLES is not substituted.
- Backend validator distinguishes validation/preflight from actual initialization.
- Initializer contract requires an explicit successful result before a backend can be READY.
- Failed or rejected initialization calls cleanup and records failure.
- Selection planner recommends the existing configuration as fallback without mutating
  Container JSON or applying driver changes.
- Unit tests cover registry duplicates/missing entries, Vulkan requirements, UNKNOWN
  capability behavior, fallback recommendations and initialization cleanup.
- Diagnostics state selected/requested backend, failure reason and whether fallback was
  applied. Fallback application remains false because no live runtime switch exists.

## Native MaliForge status

`maliforge-gx-native` is registered as experimental metadata but reports UNAVAILABLE:
there is no packaged MaliForge game renderer / ICD and no verified safe initialization hook.
The Stage 2 JNI support bridge is not a graphics backend. Its native READY state means
only that the support library initialized; it does not mean a GPU renderer is READY.

## Not changed intentionally

- No Graphics Driver selector entry was added.
- No Container schema or per-container driver field was changed.
- No existing graphics driver, wrapper, DXVK, VKD3D, WineD3D, adrenotools loader,
  Vulkan loader, compositor renderer, or shader cache was replaced or deleted.
- Existing MaliForge frame timestamp monitor remains an interface; no game-renderer hook
  feeds it, so no game FPS/frame-time claim is made.
- Existing GX shader-cache directory remains isolated bookkeeping; no Vulkan pipeline
  cache object is created or claimed.
- No FPS uplift, Helio G100 benchmark, or physical-device validation is claimed.

## Build and CI note

The current `.github/workflows/build-apk.yml` pull-request trigger targets `main`, and
its push trigger does not list this Stage 3 branch. Since this PR intentionally targets the
unmerged Stage 2 branch to avoid flattening dependencies, the current workflow will not
automatically run for this PR. Build/test status must therefore be reported as unverified
unless a compatible CI run is explicitly triggered.
