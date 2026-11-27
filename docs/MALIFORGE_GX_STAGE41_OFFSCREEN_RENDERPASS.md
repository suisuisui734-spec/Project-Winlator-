# MaliForge GX Stage 41 — Android-host offscreen render-pass attachment test

## Purpose

Stage 41 adds an opt-in diagnostic test that exercises a real Vulkan graphics render pass on the Android host device. It is a deeper graphics-resource test than the Stage 39 transfer-image clear test.

## Execution

The test:

1. Queries support for an optimal-tiled VK_FORMAT_R8G8B8A8_UNORM image with color-attachment and transfer-source usage.
2. Allocates and binds the image and host-visible read-back buffer using memory types reported by the physical device.
3. Creates an image view, one-subpass graphics render pass, and framebuffer.
4. Records vkCmdBeginRenderPass, vkCmdClearAttachments, and vkCmdEndRenderPass.
5. Copies the final attachment image to a host buffer, submits the command buffer, waits on a fence, and verifies all 16 RGBA8 pixels.
6. Reports unsupported format/memory capabilities separately from execution or read-back failures.

The test performs no container configuration writes. All Vulkan objects are owned by the isolated Android-host diagnostic device and are cleaned up on every exit path.

## What a PASS proves

A PASS proves that the Android host Vulkan implementation accepted and executed this offscreen color-attachment render pass and that the expected pixels were read back.

## What a PASS does not prove

- It does not execute a vertex or fragment shader, create a graphics pipeline, or issue a draw call.
- It does not create an Android window surface or swapchain.
- It does not execute inside a Wine guest process.
- It does not prove a MaliForge GX Vulkan ICD or game renderer is installed.
- It does not prove game compatibility, FPS gains, frame pacing, or thermal improvements.

MaliForge GX remains unavailable as a selectable game renderer until a real renderer package, guest/runtime initialization path, and end-to-end game-rendering verification exist. Existing Wrapper, DXVK, VKD3D, WineD3D, and imported drivers remain unchanged.
