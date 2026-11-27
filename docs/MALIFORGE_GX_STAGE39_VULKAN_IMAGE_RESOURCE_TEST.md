# MaliForge GX Stage 39 — Host Vulkan Image Resource and Read-back Test

**Status:** implementation submitted for CI validation  
**Scope:** Android host Vulkan only; not a shader renderer or Wine guest backend.

## What this stage adds

- A Control Center action named **Run Vulkan image clear/readback test**.
- Queries whether the selected physical device supports a 4×4 optimal-tiled `VK_FORMAT_R8G8B8A8_UNORM` image with transfer-source and transfer-destination usage.
- Allocates an optimal-tiled image and selects a compatible memory type, preferring device-local memory without assuming that every GPU has the same memory layout.
- Allocates a host-visible read-back buffer, preferring coherent memory and invalidating it when the selected type is non-coherent.
- Records explicit image layout transitions from undefined to transfer-destination and then transfer-source.
- Executes `vkCmdClearColorImage`, copies the image to a host buffer, and inserts a transfer-to-host buffer memory barrier.
- Submits the command buffer to the retained Android host graphics queue with a fence, waits up to 10 seconds, and verifies all 16 RGBA pixels.
- Reports PASS only when the submit, fence, layout/transfer operation, and pixel read-back checks all succeed. Unsupported image format or memory requirements return unavailable.

## Resource lifetime

The test is serialized against the existing host-device shutdown path. Submitted work is waited before the test image, staging buffer, command pool, fence, and memory allocations are destroyed. Mapped read-back memory is unmapped before freeing its allocation.

The test enables no optional device features or extensions. It does not modify containers, selected drivers, wrappers, DXVK, VKD3D, WineD3D, game data, or existing caches.

## Scope boundary

This is an image-resource and transfer-clear smoke test. `vkCmdClearColorImage` is a Vulkan transfer command; this stage does **not** create a render pass, shader module, descriptor set, graphics pipeline, surface, swapchain, or presentable frame. It does not intercept Wine guest Vulkan calls and does not render Windows games.

Physical Android/Mali execution remains required. Hosted CI can compile and package the code but cannot prove that a target device's Vulkan driver executes the image test successfully.
