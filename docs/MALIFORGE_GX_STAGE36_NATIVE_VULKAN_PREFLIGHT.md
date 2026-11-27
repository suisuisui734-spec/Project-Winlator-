# MaliForge GX Stage 36 — Native Vulkan Host Preflight

## Status

Implemented as a read-only native preflight foundation. This is not the MaliForge GX game renderer and does not make the GX graphics-driver entry selectable.

## What is added

- A separate JNI Vulkan probe compiled into the existing ARM64 libwinlator.so.
- Creation and destruction of a minimal Vulkan 1.0 instance through Android's linked Vulkan loader.
- Physical-device enumeration, including device name, vendor/device IDs, reported device API version and driver version.
- Queue-family capability inspection for graphics, compute and transfer.
- A read-only sum of device-local memory heaps.
- Detection of selected device extensions: VK_KHR_swapchain, VK_KHR_synchronization2, VK_EXT_memory_budget, and VK_KHR_driver_properties.
- A defensive Java parser and unit tests for success, failure, malformed responses and inconsistent device counts.
- Control Center output that clearly labels the result as an Android host Vulkan probe.

## What this does not do

- It does not call vkCreateDevice, acquire a queue, create a surface or swapchain, compile shaders, allocate GPU resources, render a frame, or install an ICD.
- It does not inspect the Vulkan device used by a Wine game.
- It does not change Container.graphicsDriver, environment variables, wrapper selection, Vulkan ICD paths, or any existing driver archive.
- A successful host probe must not be interpreted as proof that a Wine guest can use the same Vulkan capabilities.
- It does not claim higher FPS, lower latency, fewer crashes, or Mali-G57/Helio benchmark results.

## Backend activation gate

The existing maliforge-gx-native descriptor remains unavailable and non-selectable. The real renderer package, ABI-compatible guest/runtime integration, safe Wine initialization hook, resource lifecycle, synchronization, and physical-device validation are still required before activation.

## Validation

The Android NDK/CMake build compiles the native probe as part of libwinlator.so. JVM unit tests validate parsing and fail-closed behavior. CI build success does not replace physical Android/Mali testing.
