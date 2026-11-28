# MaliForge GX Stage 42 — Vulkan physical-device capability snapshot

## Purpose

Expose physical-device information queried by the existing Android-host Vulkan bootstrap. This improves diagnostics between host VkDevice creation and later capability-gated rendering work.

## Native queries

After selecting a host physical device with a graphics-capable queue family, the runtime records:

- Physical-device type and raw vendor driver version
- Device extension enumeration result and extension names
- Supported core VkPhysicalDeviceFeatures flags
- Memory heap/type counts, aggregate device-local heap capacity, and host-visible memory-type count
- Selected limits: maximum 2D image dimension, descriptor-set count, push-constant size, and graphics/compute timestamp support

The result is returned through JNI and shown in the existing Control Center device-bootstrap diagnostic section.

## Capability semantics

- Extension names mean **advertised by the physical device**, not enabled on the created logical device.
- Core feature flags mean **reported as supported**, not enabled in VkDeviceCreateInfo.
- Device-local heap capacity is not current allocation usage and does not establish dedicated VRAM.
- The raw driver version is vendor-defined and is not normalized to a marketing version.
- If extension enumeration fails, the host VkDevice bootstrap may still be ready while extension availability is explicitly reported as unavailable. Missing data is not converted into an unsupported result.

## Scope boundary

This is an Android-host capability snapshot. It does not create a surface or swapchain, enable optional Vulkan extensions/features, create a shader module or graphics pipeline, issue a draw call, connect the host VkDevice to Wine, replace the guest ICD, or change the selected Winlator driver.

MaliForge GX therefore remains unavailable as a selectable game renderer. Existing Wrapper, DXVK, VKD3D, WineD3D and imported driver paths remain unchanged.

## Validation

The Android NDK build and JVM parser tests are covered by the repository GitHub Actions workflow. This source change does not claim a local build, physical Mali hardware test, surface presentation test, DXVK initialization, or Windows game rendering. Hardware status remains **NOT TESTED ON REAL HARDWARE** until a target-device run records its model, GPU, Vulkan driver and actual results.
