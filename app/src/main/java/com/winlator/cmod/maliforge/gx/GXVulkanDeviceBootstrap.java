package com.winlator.cmod.maliforge.gx;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

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
            int queueFamilyIndex = intValue(root, "queueFamilyIndex", -1);
            int queueCount = intValue(root, "queueCount", 0);
            boolean graphicsQueue = booleanValue(root, "graphicsQueue", false);
            boolean retainedInProcess = booleanValue(root, "retainedInProcess", false);
            boolean reused = booleanValue(root, "reused", false);
            String detail = stringValue(root, "detail", "No device-bootstrap details were returned.");

            boolean ready = "ready".equals(status) && "logical_device_created".equals(stage)
                    && !deviceName.trim().isEmpty() && queueFamilyIndex >= 0
                    && queueCount > 0 && graphicsQueue && retainedInProcess;
            if ("ready".equals(status) && !ready) {
                return unavailable("invalid_response",
                        "Native probe reported ready without a valid retained VkDevice and graphics queue.", vkResult);
            }
            return new Result(ready, status, stage, vkResult, deviceName, vendorId, deviceId,
                    apiVersion, queueFamilyIndex, queueCount, graphicsQueue,
                    retainedInProcess, reused, detail);
        } catch (RuntimeException exception) {
            return unavailable("invalid_response",
                    "Unable to parse Vulkan device response: " + exception.getClass().getSimpleName(), -1);
        }
    }

    public static Result unavailable(String detail) {
        return unavailable("not_run", detail, -1);
    }

    private static Result unavailable(String status, String detail, int vkResult) {
        return new Result(false, status, "not_run", vkResult, "Unavailable",
                0, 0, "Unknown", -1, 0, false, false, false, detail);
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

    private static boolean booleanValue(JsonObject object, String key, boolean fallback) {
        try {
            JsonElement value = object.get(key);
            return value == null || value.isJsonNull() ? fallback : value.getAsBoolean();
        } catch (RuntimeException ignored) {
            return fallback;
        }
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
        public final int queueFamilyIndex;
        public final int queueCount;
        public final boolean graphicsQueue;
        public final boolean retainedInProcess;
        public final boolean reused;
        public final String detail;

        private Result(boolean ready, String status, String stage, int vkResult,
                       String deviceName, int vendorId, int deviceId, String apiVersion,
                       int queueFamilyIndex, int queueCount, boolean graphicsQueue,
                       boolean retainedInProcess, boolean reused, String detail) {
            this.ready = ready;
            this.status = status == null ? "unknown" : status;
            this.stage = stage == null ? "unknown" : stage;
            this.vkResult = vkResult;
            this.deviceName = deviceName == null ? "Unavailable" : deviceName;
            this.vendorId = vendorId;
            this.deviceId = deviceId;
            this.apiVersion = apiVersion == null ? "Unknown" : apiVersion;
            this.queueFamilyIndex = queueFamilyIndex;
            this.queueCount = Math.max(0, queueCount);
            this.graphicsQueue = graphicsQueue;
            this.retainedInProcess = retainedInProcess;
            this.reused = reused;
            this.detail = detail == null ? "" : detail;
        }

        public String toDiagnosticText() {
            StringBuilder text = new StringBuilder();
            text.append("Status: ").append(status).append('\n');
            text.append("Stage: ").append(stage).append('\n');
            text.append("VkResult: ").append(vkResult).append('\n');
            text.append("Device: ").append(deviceName).append('\n');
            text.append("Vendor / device ID: 0x")
                    .append(String.format(java.util.Locale.ROOT, "%04X", vendorId))
                    .append(" / 0x")
                    .append(String.format(java.util.Locale.ROOT, "%04X", deviceId)).append('\n');
            text.append("Device API: ").append(apiVersion).append('\n');
            text.append("Graphics queue family: ").append(queueFamilyIndex).append('\n');
            text.append("Queue family capacity: ").append(queueCount).append('\n');
            text.append("Graphics queue acquired: ").append(graphicsQueue).append('\n');
            text.append("VkDevice retained in process: ").append(retainedInProcess).append('\n');
            text.append("Reused existing device: ").append(reused).append('\n');
            text.append("Detail: ").append(detail);
            return text.toString();
        }
    }
}
