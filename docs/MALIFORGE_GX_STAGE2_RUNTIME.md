# MaliForge GX Stage 2 — Native Runtime Foundation

## Base and preservation
Stage 2 is based on the unmerged MaliForge GX Foundation commit
`ffa3ae7196baa5c300fd0ce45e8ea0ecbd19b13e`, not `main`. PR #5 remains open;
this branch carries those foundation files forward.

## Implemented
- Additive C++ ARM64 bridge compiled into Winlator's existing `libwinlator.so`.
- JNI entry points for initialize, state, error, ABI, Android API, diagnostics and shutdown.
- Java wrapper reports missing native library/JNI linkage as explicit failure.
- Native init validates AArch64 compilation and 64-bit pointer size.
- Thread-safe native state; no long-lived GPU resources.
- Diagnostics combine native bridge state with the existing Vulkan capability detector.
- Existing driver archives, Vulkan loader, selector, renderer, native render loop and caches are untouched.

## Runtime semantics
Native state `READY` means only that the ARM64 support bridge initialized. It is not a
Vulkan ICD/layer, GPU renderer, OpenGL ES backend, or game render hook. Graphics backend
availability is therefore always false in this stage.

## Existing graphics API reuse
Winlator already loads `libwinlator.so`, exposes GPUInformation through JNI and has a
native EGL/GLES renderer. GX reuses the existing Vulkan capability detector instead of
creating a competing Vulkan instance or altering the renderer's global EGL context.

## Not implemented
- MaliForge Vulkan ICD/layer or replacement GPU driver.
- GX-owned Vulkan device creation or GLES renderer interception.
- Application of per-game settings to the live Wine process.
- Frame-time hook to the native renderer.
- Graphics Driver selector entry; no entry can be safely registered until a valid runtime package exists.
- Physical Helio G100 validation or FPS claims.

The source is added to the existing `winlator` CMake target. Gradle's existing
`arm64-v8a` ABI filter is retained. Android/NDK CI must compile the JNI bridge and APK.
