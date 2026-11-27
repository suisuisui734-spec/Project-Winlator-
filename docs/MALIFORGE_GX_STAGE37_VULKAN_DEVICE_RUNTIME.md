# MaliForge GX Stage 37 — Vulkan Logical-Device Runtime

**Status:** implementation submitted for CI validation  
**Scope:** Android host Vulkan only

## What this stage adds

- A separate native ARM64 source file creates a Vulkan 1.0 instance.
- It enumerates host physical devices and selects a queue family with graphics capability.
- It creates a logical `VkDevice` with one graphics queue and enables no optional device features or extensions.
- The instance, logical device, physical-device handle, and graphics queue are retained in process memory for later backend work.
- A JNI shutdown function waits for the device to become idle, destroys the device, then destroys the instance.
- The GX Control Center reports the selected device, API version, queue-family index, queue-family capacity, and whether an existing device was reused.
- Defensive Java parsing and unit tests reject malformed or internally inconsistent responses.

## Explicit non-goals

This is a device-runtime milestone, not a game renderer or a Vulkan ICD. It does not:

- register or replace an Android Vulkan driver or system ICD;
- intercept or translate Wine guest Vulkan calls;
- create an Android surface, swapchain, image, framebuffer, shader module, pipeline, command pool, or command buffer;
- submit rendering commands or present a frame;
- alter the existing EGL renderer, Winlator display path, container driver selection, environment variables, or graphics wrappers;
- make the MaliForge GX graphics-driver entry selectable.

The existing EGL/GLES Winlator display renderer and all existing graphics-driver choices remain unchanged. A successful `VkDevice` creation proves only that the Android host Vulkan runtime accepted a minimal logical device and returned a graphics queue.

## Validation required

CI must pass Java unit tests/lint, native CMake/NDK compilation, ARM64 APK packaging, and existing guest-layer checks. A physical Android device test is still needed to confirm the runtime call succeeds with the device's actual Vulkan loader and kernel driver.

## Next renderer work

A later stage must add resource allocation, command submission, a minimal offscreen rendering test, explicit capability negotiation, and eventually a tested guest-to-host integration path. Only after those parts are implemented and validated can MaliForge GX be described as rendering game frames.