# MaliForge GX Stage 40 — Guest Vulkan Queue Telemetry

**Status:** implementation submitted for CI validation  
**Scope:** opt-in Linux x86_64 guest Vulkan pass-through diagnostics. Not a renderer or performance optimizer.

## Runtime coverage

The existing guest Vulkan layer now wraps queue retrieval and selected queue calls:

- `vkGetDeviceQueue` and `vkGetDeviceQueue2` associate returned dispatchable queues with their creating device.
- `vkQueueSubmit`, `vkQueueSubmit2`, and `vkQueueSubmit2KHR` forward unchanged to the next layer/driver and record aggregate CPU-side call duration and result.
- `vkQueuePresentKHR`, when exposed by the next device dispatch table, forwards unchanged and records aggregate present-call CPU duration plus intervals between successful present-call entries.
- Queue handles are removed from the layer's bookkeeping when their device is destroyed.
- Metrics are aggregated per device and written on the first sample and periodically (every 256 submit calls / 60 successful present calls), not as a log line for every command.

## Metric interpretation

`avg_cpu_ns` and `max_cpu_ns` measure time spent inside the guest-side call to the next Vulkan implementation. They are not GPU execution times.

`avg_call_interval_ns` and `max_call_interval_ns` measure intervals between successful `vkQueuePresentKHR` call entries seen by the layer. They are not display refresh intervals, GPU frame times, or FPS. Multiple swapchains/queues may be aggregated per device, so these values must not be treated as a benchmark without further instrumentation and device testing.

No rendering arguments, command buffers, wait semaphores, signals, swapchains, or present information are changed by the layer. The telemetry does not force synchronization or add GPU work.

## Activation and fallback

The layer remains opt-in through `MALIFORGE_GX_VULKAN_LAYER=1` for an x86_64 guest. It is loaded through the existing ImageFS implicit layer manifest and forwards to the currently selected Vulkan implementation. If the layer is not requested or cannot be loaded, the original renderer path remains unchanged.

## Validation

The Linux Mesa lavapipe smoke test now retrieves a queue, records an empty command buffer, submits it with a fence, waits for completion, and requires a `QUEUE_SUBMIT_METRICS` event in the layer log. This exercises the queue-dispatch mapping and pass-through submit wrapper in CI.

The smoke test does not create a surface/swapchain, so `vkQueuePresentKHR` telemetry and Android/Wine game behavior remain unverified until tested on a suitable target device.
