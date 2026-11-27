package com.winlator.cmod.maliforge.gx;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Parsed result of a read-only Android host Vulkan preflight, never a guest renderer result. */
public final class GXHostVulkanProbe {
    private GXHostVulkanProbe() {}

    public static Result parse(String payload) {
        if (payload == null || payload.trim().isEmpty()) {
            return unavailable("empty_response", "native probe returned an empty response", -1);
        }
        try {
            JsonElement rootElement = JsonParser.parseString(payload);
            if (!rootElement.isJsonObject()) {
                return unavailable("invalid_response", "native probe response is not an object", -1);
            }
            JsonObject root = rootElement.getAsJsonObject();
            String status = stringValue(root, "status", "unknown");
            String stage = stringValue(root, "stage", "unknown");
            int vkResult = intValue(root, "vkResult", -1);
            int reportedCount = Math.max(0, intValue(root, "deviceCount", 0));
            List<Device> devices = new ArrayList<>();
            JsonElement devicesElement = root.get("devices");
            if (devicesElement != null && devicesElement.isJsonArray()) {
                JsonArray array = devicesElement.getAsJsonArray();
                for (JsonElement element : array) {
                    if (element == null || !element.isJsonObject()) continue;
                    devices.add(parseDevice(element.getAsJsonObject()));
                }
            }
            boolean available = "available".equals(status) && !devices.isEmpty();
            String detail = available
                    ? "Android host Vulkan instance created; physical-device metadata enumerated."
                    : stringValue(root, "detail", "No Vulkan physical device was verified.");
            if ("available".equals(status) && devices.isEmpty()) {
                status = "invalid_response";
                detail = "Probe reported success without any device records.";
            }
            return new Result(available, status, stage, vkResult, reportedCount, detail, devices);
        } catch (RuntimeException exception) {
            return unavailable("invalid_response",
                    "Unable to parse native Vulkan probe response: "
                            + exception.getClass().getSimpleName(), -1);
        }
    }

    public static Result unavailable(String detail) {
        return unavailable("not_run", "not_run", -1, detail);
    }

    private static Result unavailable(String status, String stage, int vkResult) {
        return unavailable(status, stage, vkResult, "Vulkan host preflight was not available.");
    }

    private static Result unavailable(String status, String stage, int vkResult, String detail) {
        return new Result(false, status, stage, vkResult, 0, detail,
                Collections.<Device>emptyList());
    }

    private static Device parseDevice(JsonObject object) {
        JsonObject extensions = object.has("extensions") && object.get("extensions").isJsonObject()
                ? object.getAsJsonObject("extensions") : new JsonObject();
        return new Device(
                stringValue(object, "name", "Unknown device"),
                intValue(object, "vendorId", 0),
                intValue(object, "deviceId", 0),
                stringValue(object, "apiVersion", "Unknown"),
                longValue(object, "driverVersion", 0L),
                stringValue(object, "deviceType", "other"),
                booleanValue(object, "graphicsQueue", false),
                booleanValue(object, "computeQueue", false),
                booleanValue(object, "transferQueue", false),
                longValue(object, "deviceLocalMemoryBytes", 0L),
                booleanValue(object, "extensionsEnumerated", false),
                booleanValue(extensions, "swapchain", false),
                booleanValue(extensions, "synchronization2", false),
                booleanValue(extensions, "memoryBudget", false),
                booleanValue(extensions, "driverProperties", false));
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

    public static final class Result {
        public final boolean available;
        public final String status;
        public final String stage;
        public final int vkResult;
        public final int reportedDeviceCount;
        public final String detail;
        public final List<Device> devices;

        private Result(boolean available, String status, String stage, int vkResult,
                       int reportedDeviceCount, String detail, List<Device> devices) {
            this.available = available;
            this.status = status == null ? "unknown" : status;
            this.stage = stage == null ? "unknown" : stage;
            this.vkResult = vkResult;
            this.reportedDeviceCount = Math.max(0, reportedDeviceCount);
            this.detail = detail == null ? "" : detail;
            this.devices = Collections.unmodifiableList(new ArrayList<>(devices));
        }

        public String summary() {
            return available ? devices.size() + " Android host Vulkan device(s) enumerated"
                    : "Unavailable (" + status + ", stage=" + stage + ")";
        }

        public String toDiagnosticText() {
            StringBuilder text = new StringBuilder();
            text.append("Status: ").append(status).append('\n');
            text.append("Stage: ").append(stage).append('\n');
            text.append("VkResult: ").append(vkResult).append('\n');
            text.append("Devices reported: ").append(reportedDeviceCount).append('\n');
            text.append("Detail: ").append(detail);
            for (int index = 0; index < devices.size(); index++) {
                Device device = devices.get(index);
                text.append("\n\nDevice ").append(index + 1).append(": ")
                        .append(device.name).append('\n')
                        .append("Vendor / device ID: 0x")
                        .append(String.format(java.util.Locale.ROOT, "%04X", device.vendorId))
                        .append(" / 0x")
                        .append(String.format(java.util.Locale.ROOT, "%04X", device.deviceId)).append('\n')
                        .append("Device API: ").append(device.apiVersion).append('\n')
                        .append("Type: ").append(device.deviceType).append('\n')
                        .append("Queues (graphics / compute / transfer): ")
                        .append(device.graphicsQueue).append(" / ")
                        .append(device.computeQueue).append(" / ")
                        .append(device.transferQueue).append('\n')
                        .append("Device-local heap: ").append(device.deviceLocalMemoryBytes)
                        .append(" bytes\n")
                        .append("Extensions enumerated: ").append(device.extensionsEnumerated)
                        .append(" (swapchain=").append(device.swapchain)
                        .append(", synchronization2=").append(device.synchronization2)
                        .append(", memoryBudget=").append(device.memoryBudget)
                        .append(", driverProperties=").append(device.driverProperties).append(')');
            }
            return text.toString();
        }
    }

    public static final class Device {
        public final String name;
        public final int vendorId;
        public final int deviceId;
        public final String apiVersion;
        public final long driverVersion;
        public final String deviceType;
        public final boolean graphicsQueue;
        public final boolean computeQueue;
        public final boolean transferQueue;
        public final long deviceLocalMemoryBytes;
        public final boolean extensionsEnumerated;
        public final boolean swapchain;
        public final boolean synchronization2;
        public final boolean memoryBudget;
        public final boolean driverProperties;

        private Device(String name, int vendorId, int deviceId, String apiVersion,
                       long driverVersion, String deviceType, boolean graphicsQueue,
                       boolean computeQueue, boolean transferQueue, long deviceLocalMemoryBytes,
                       boolean extensionsEnumerated, boolean swapchain, boolean synchronization2,
                       boolean memoryBudget, boolean driverProperties) {
            this.name = name;
            this.vendorId = vendorId;
            this.deviceId = deviceId;
            this.apiVersion = apiVersion;
            this.driverVersion = driverVersion;
            this.deviceType = deviceType;
            this.graphicsQueue = graphicsQueue;
            this.computeQueue = computeQueue;
            this.transferQueue = transferQueue;
            this.deviceLocalMemoryBytes = deviceLocalMemoryBytes;
            this.extensionsEnumerated = extensionsEnumerated;
            this.swapchain = swapchain;
            this.synchronization2 = synchronization2;
            this.memoryBudget = memoryBudget;
            this.driverProperties = driverProperties;
        }
    }
}
