package com.winlator.cmod.maliforge.gx;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Result of creating an Android host VkDevice and acquiring one graphics queue. */
public final class GXVulkanDeviceBootstrap {
    private GXVulkanDeviceBootstrap() {}

    public static Result parse(String payload) {
        if (payload == null || payload.trim().isEmpty()) {
            return unavailable("empty_response", "Native Vulkan device bootstrap returned no response.", -1);
        }
        try {
            JsonElement rootElement = JsonParser.parseString(payload);
            if (!rootElement.isJsonObject()) {
                return unavailable("invalid_response", "Native Vulkan device response is not an object.", -1);
            }
            JsonObject root = rootElement.getAsJsonObject();
            String status = stringValue(root, "status", "unknown");
            String stage = stringValue(root, "stage", "unknown");
            int vkResult = intValue(root, "vkResult", -1);
            String deviceName = stringValue(root, "deviceName", "Unknown device");
            int vendorId = intValue(root, "vendorId", 0);
            int deviceId = intValue(root, "deviceId", 0);
            String apiVersion = stringValue(root, "apiVersion", "Unknown");
            String deviceType = stringValue(root, "deviceType", "UNKNOWN");
            long driverVersion = longValue(root, "driverVersion", 0L);
            int queueFamilyIndex = intValue(root, "queueFamilyIndex", -1);
            int queueCount = intValue(root, "queueCount", 0);
            boolean graphicsQueue = booleanValue(root, "graphicsQueue", false);
            boolean retainedInProcess = booleanValue(root, "retainedInProcess", false);
            boolean reused = booleanValue(root, "reused", false);
            boolean extensionsEnumerated = booleanValue(root, "extensionsEnumerated", false);
            List<String> deviceExtensions = stringArray(root, "deviceExtensions");
            List<String> availableFeatures = stringArray(root, "availableFeatures");
            int memoryHeapCount = intValue(root, "memoryHeapCount", 0);
            int memoryTypeCount = intValue(root, "memoryTypeCount", 0);
            long deviceLocalHeapBytes = longValue(root, "deviceLocalHeapBytes", 0L);
            int hostVisibleMemoryTypeCount = intValue(root, "hostVisibleMemoryTypeCount", 0);
            int maxImageDimension2D = intValue(root, "maxImageDimension2D", 0);
            int maxBoundDescriptorSets = intValue(root, "maxBoundDescriptorSets", 0);
            int maxPushConstantsSize = intValue(root, "maxPushConstantsSize", 0);
            boolean timestampComputeAndGraphics = booleanValue(root, "timestampComputeAndGraphics", false);
            String detail = stringValue(root, "detail", "No device-bootstrap details were returned.");

            boolean ready = "ready".equals(status) && "logical_device_created".equals(stage)
                    && !deviceName.trim().isEmpty() && queueFamilyIndex >= 0
                    && queueCount > 0 && graphicsQueue && retainedInProcess;
            if ("ready".equals(status) && !ready) {
                return unavailable("invalid_response",
                        "Native probe reported ready without a valid retained VkDevice and graphics queue.", vkResult);
            }
            return new Result(ready, status, stage, vkResult, deviceName, vendorId, deviceId,
                    apiVersion, deviceType, driverVersion, queueFamilyIndex, queueCount,
                    graphicsQueue, retainedInProcess, reused, extensionsEnumerated,
                    deviceExtensions, availableFeatures, memoryHeapCount, memoryTypeCount,
                    deviceLocalHeapBytes, hostVisibleMemoryTypeCount, maxImageDimension2D,
                    maxBoundDescriptorSets, maxPushConstantsSize, timestampComputeAndGraphics, detail);
        } catch (RuntimeException exception) {
            return unavailable("invalid_response",
                    "Unable to parse Vulkan device response: " + exception.getClass().getSimpleName(), -1);
        }
    }

    public static Result unavailable(String detail) {
        return unavailable("not_run", detail, -1);
    }

    private static Result unavailable(String status, String detail, int vkResult) {
        return new Result(false, status, "not_run", vkResult, "Unavailable", 0, 0, "Unknown",
                "UNKNOWN", 0L, -1, 0, false, false, false, false,
                Collections.<String>emptyList(), Collections.<String>emptyList(), 0, 0, 0L, 0,
                0, 0, 0, false, detail);
    }

    private static String stringValue(JsonObject object, String key, String fallback) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? fallback : value.getAsString();
    }

    private static int intValue(JsonObject object, String key, int fallback) {
        try {
            JsonElement value = object.get(key);
            return value == null || value.isJsonNull() ? fallback : value.getAsInt();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static long longValue(JsonObject object, String key, long fallback) {
        try {
            JsonElement value = object.get(key);
            return value == null || value.isJsonNull() ? fallback : value.getAsLong();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static boolean booleanValue(JsonObject object, String key, boolean fallback) {
        try {
            JsonElement value = object.get(key);
            return value == null || value.isJsonNull() ? fallback : value.getAsBoolean();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static List<String> stringArray(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonArray()) return Collections.emptyList();
        JsonArray array = value.getAsJsonArray();
        ArrayList<String> values = new ArrayList<>(array.size());
        for (JsonElement item : array) {
            if (item != null && item.isJsonPrimitive() && item.getAsJsonPrimitive().isString()) {
                values.add(item.getAsString());
            }
        }
        return Collections.unmodifiableList(values);
    }

    public static final class Result {
        public final boolean ready;
        public final String status;
        public final String stage;
        public final int vkResult;
        public final String deviceName;
        public final int vendorId;
        public final int deviceId;
        public final String apiVersion;
        public final String deviceType;
        public final long driverVersion;
        public final int queueFamilyIndex;
        public final int queueCount;
        public final boolean graphicsQueue;
        public final boolean retainedInProcess;
        public final boolean reused;
        public final boolean extensionsEnumerated;
        public final List<String> deviceExtensions;
        public final List<String> availableFeatures;
        public final int memoryHeapCount;
        public final int memoryTypeCount;
        public final long deviceLocalHeapBytes;
        public final int hostVisibleMemoryTypeCount;
        public final int maxImageDimension2D;
        public final int maxBoundDescriptorSets;
        public final int maxPushConstantsSize;
        public final boolean timestampComputeAndGraphics;
        public final String detail;

        private Result(boolean ready, String status, String stage, int vkResult,
                       String deviceName, int vendorId, int deviceId, String apiVersion,
                       String deviceType, long driverVersion, int queueFamilyIndex, int queueCount,
                       boolean graphicsQueue, boolean retainedInProcess, boolean reused,
                       boolean extensionsEnumerated, List<String> deviceExtensions,
                       List<String> availableFeatures, int memoryHeapCount, int memoryTypeCount,
                       long deviceLocalHeapBytes, int hostVisibleMemoryTypeCount,
                       int maxImageDimension2D, int maxBoundDescriptorSets,
                       int maxPushConstantsSize, boolean timestampComputeAndGraphics, String detail) {
            this.ready = ready;
            this.status = status == null ? "unknown" : status;
            this.stage = stage == null ? "unknown" : stage;
            this.vkResult = vkResult;
            this.deviceName = deviceName == null ? "Unavailable" : deviceName;
            this.vendorId = vendorId;
            this.deviceId = deviceId;
            this.apiVersion = apiVersion == null ? "Unknown" : apiVersion;
            this.deviceType = deviceType == null ? "UNKNOWN" : deviceType;
            this.driverVersion = driverVersion;
            this.queueFamilyIndex = queueFamilyIndex;
            this.queueCount = Math.max(0, queueCount);
            this.graphicsQueue = graphicsQueue;
            this.retainedInProcess = retainedInProcess;
            this.reused = reused;
            this.extensionsEnumerated = extensionsEnumerated;
            this.deviceExtensions = deviceExtensions == null
                    ? Collections.<String>emptyList()
                    : Collections.unmodifiableList(new ArrayList<>(deviceExtensions));
            this.availableFeatures = availableFeatures == null
                    ? Collections.<String>emptyList()
                    : Collections.unmodifiableList(new ArrayList<>(availableFeatures));
            this.memoryHeapCount = Math.max(0, memoryHeapCount);
            this.memoryTypeCount = Math.max(0, memoryTypeCount);
            this.deviceLocalHeapBytes = Math.max(0L, deviceLocalHeapBytes);
            this.hostVisibleMemoryTypeCount = Math.max(0, hostVisibleMemoryTypeCount);
            this.maxImageDimension2D = Math.max(0, maxImageDimension2D);
            this.maxBoundDescriptorSets = Math.max(0, maxBoundDescriptorSets);
            this.maxPushConstantsSize = Math.max(0, maxPushConstantsSize);
            this.timestampComputeAndGraphics = timestampComputeAndGraphics;
            this.detail = detail == null ? "" : detail;
        }

        public boolean hasExtension(String extensionName) {
            return extensionName != null && deviceExtensions.contains(extensionName);
        }

        public boolean hasFeature(String featureName) {
            return featureName != null && availableFeatures.contains(featureName);
        }

        public String toDiagnosticText() {
            StringBuilder text = new StringBuilder();
            text.append("Status: ").append(status).append('\n');
            text.append("Stage: ").append(stage).append('\n');
            text.append("VkResult: ").append(vkResult).append('\n');
            text.append("Device: ").append(deviceName).append('\n');
            text.append("Device type: ").append(deviceType).append('\n');
            text.append("Vendor / device ID: 0x")
                    .append(String.format(java.util.Locale.ROOT, "%04X", vendorId))
                    .append(" / 0x")
                    .append(String.format(java.util.Locale.ROOT, "%04X", deviceId)).append('\n');
            text.append("Device API: ").append(apiVersion).append('\n');
            text.append("Driver version (raw): ").append(driverVersion).append('\n');
            text.append("Graphics queue family: ").append(queueFamilyIndex).append('\n');
            text.append("Queue family capacity: ").append(queueCount).append('\n');
            text.append("Graphics queue acquired: ").append(graphicsQueue).append('\n');
            text.append("VkDevice retained in process: ").append(retainedInProcess).append('\n');
            text.append("Memory heaps / types: ").append(memoryHeapCount)
                    .append(" / ").append(memoryTypeCount).append('\n');
            text.append("Device-local heap capacity (not usage/VRAM): ")
                    .append(deviceLocalHeapBytes).append(" bytes").append('\n');
            text.append("Host-visible memory types: ").append(hostVisibleMemoryTypeCount).append('\n');
            text.append("Limits: max 2D image ").append(maxImageDimension2D)
                    .append(", descriptor sets ").append(maxBoundDescriptorSets)
                    .append(", push constants ").append(maxPushConstantsSize).append(" bytes").append('\n');
            text.append("Graphics/compute timestamps: ").append(timestampComputeAndGraphics).append('\n');
            text.append("Device extensions enumerated: ").append(extensionsEnumerated).append('\n');
            text.append("Advertised device extensions (not enabled): ");
            appendList(text, deviceExtensions, extensionsEnumerated ? "none" : "unavailable");
            text.append('\n');
            text.append("Available core features (not enabled): ");
            appendList(text, availableFeatures, "none reported");
            text.append('\n');
            text.append("Reused existing device: ").append(reused).append('\n');
            text.append("Detail: ").append(detail);
            return text.toString();
        }

        private static void appendList(StringBuilder target, List<String> values, String emptyLabel) {
            if (values == null || values.isEmpty()) {
                target.append(emptyLabel);
                return;
            }
            for (int index = 0; index < values.size(); ++index) {
                if (index > 0) target.append(", ");
                target.append(values.get(index));
            }
        }
    }
}
