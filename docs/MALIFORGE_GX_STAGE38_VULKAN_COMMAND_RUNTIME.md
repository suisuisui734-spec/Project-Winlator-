# MaliForge GX Stage 38 — Host Vulkan Command Execution Smoke Test

**Status:** implementation submitted for CI validation  
**Scope:** Android host Vulkan only; this is not a Wine guest renderer.

## What this stage adds

- A Control Center action named **Run Vulkan buffer-copy test**.
- The action uses the retained host `VkDevice` and graphics queue initialized by Stage 37.
- Native code allocates compatible host-visible transfer buffers, preferring coherent memory when available.
- It records a real `vkCmdCopyBuffer` command into a primary command buffer from a transient command pool.
- The command buffer is submitted to the selected graphics queue with a fence.
- The host waits up to 10 seconds for completion, maps destination memory, invalidates non-coherent memory when needed, and compares all 64 copied bytes against a deterministic source pattern.
- The Control Center reports PASS only when submission succeeded, the fence signaled, and destination bytes match. Unsupported host-visible memory or failed Vulkan calls are reported as unavailable/failed, never success.
- The operation executes on a worker thread and does not block Android's UI thread.

## Lifecycle and isolation

The operation is serialized with the existing host-device lifecycle mutex. It waits for submitted work before releasing test resources, unmaps mapped memory, destroys its fence and command pool, destroys buffers, and frees their allocations. It does not enable optional Vulkan device features or extensions.

No existing graphics driver, wrapper, DXVK, VKD3D, WineD3D, container setting, game data, or default renderer is changed. MaliForge GX remains non-selectable as a game graphics driver.

## What this does not prove

This is a host Vulkan transfer-command test, not a renderer. It does not create an image, render pass, shader module, graphics pipeline, Android surface, swapchain, or presentable frame. It does not intercept Wine guest Vulkan calls and does not render Windows games. A passing test confirms only that the Android host Vulkan implementation executed the tested buffer-copy command and made the result visible to the host.

Physical Android/Mali execution must still be performed on the target device. Hosted CI compilation and JVM tests cannot substitute for that check.

## Validation expected

The existing CI workflow should compile the JNI/NDK code for `arm64-v8a`, run Java parser tests and lint, build/inspect the APK, and preserve the separate Linux x86_64 guest diagnostics layer validation. Physical device execution remains unverified until the Control Center action is run on real hardware.
