package com.winlator.cmod.maliforge.gx;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Android-host Vulkan image allocation, clear, copy, and read-back smoke test. */
public final class GXVulkanImageClearTest {
    private static final int EXPECTED_WIDTH = 4;
    private static final int EXPECTED_HEIGHT = 4;
    private static final int EXPECTED_PIXELS = EXPECTED_WIDTH * EXPECTED_HEIGHT;

    private GXVulkanImageClearTest() {}

    public static Result run() {
        if (!GXNativeBridge.isLoaded()) {
            return unavailable("native_library_unavailable",
                    "The Winlator native library is not loaded.");
        }
        try {
            return parse(GXNativeBridge.runVulkanImageClearTest());
        } catch (RuntimeException | LinkageError error) {
            return unavailable("native_call_failed",
                    error.getClass().getSimpleName() + ": "
                            + (error.getMessage() == null ? "No details" : error.getMessage()));
        }
    }

    static Result parse(String payload) {
        if (payload == null || payload.trim().isEmpty()) {
            return unavailable("empty_response", "The native Vulkan image test returned no response.");
        }
        try {
            JsonElement element = JsonParser.parseString(payload);
            if (!element.isJsonObject()) {
                return unavailable("invalid_response", "The native image-test response is not a JSON object.");
            }
            JsonObject object = element.getAsJsonObject();
            String status = stringValue(object, "status", "unknown");
            String stage = stringValue(object, "stage", "unknown");
            int vkResult = intValue(object, "vkResult", -1);
            int width = intValue(object, "width", 0);
            int height = intValue(object, "height", 0);
            int pixelsVerified = intValue(object, "pixelsVerified", 0);
            boolean submitted = booleanValue(object, "commandBufferSubmitted", false);
            boolean fenceSignaled = booleanValue(object, "fenceSignaled", false);
            boolean dataVerified = booleanValue(object, "dataVerified", false);
            String format = stringValue(object, "format", "unknown");
            String detail = stringValue(object, "detail", "No image-test detail was returned.");

            boolean ready = "passed".equals(status) && "image_data_verified".equals(stage)
                    && vkResult == 0 && width == EXPECTED_WIDTH && height == EXPECTED_HEIGHT
                    && pixelsVerified == EXPECTED_PIXELS && submitted && fenceSignaled && dataVerified
                    && "VK_FORMAT_R8G8B8A8_UNORM".equals(format);
            if ("passed".equals(status) && !ready) {
                return new Result(false, "invalid_response", stage, vkResult, width, height,
                        pixelsVerified, submitted, fenceSignaled, dataVerified, format,
                        "Native response claimed PASS without all image, command, fence, and pixel checks.");
            }
            return new Result(ready, status, stage, vkResult, width, height, pixelsVerified,
                    submitted, fenceSignaled, dataVerified, format, detail);
        } catch (RuntimeException error) {
            return unavailable("invalid_response",
                    "Unable to parse native Vulkan image-test response: "
                            + error.getClass().getSimpleName());
        }
    }

    private static Result unavailable(String stage, String detail) {
        return new Result(false, "unavailable", stage, -1, 0, 0, 0,
                false, false, false, "unknown", detail);
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
        public final int width;
        public final int height;
        public final int pixelsVerified;
        public final boolean commandBufferSubmitted;
        public final boolean fenceSignaled;
        public final boolean dataVerified;
        public final String format;
        public final String detail;

        private Result(boolean ready, String status, String stage, int vkResult, int width, int height,
                       int pixelsVerified, boolean commandBufferSubmitted, boolean fenceSignaled,
                       boolean dataVerified, String format, String detail) {
            this.ready = ready;
            this.status = status == null ? "unknown" : status;
            this.stage = stage == null ? "unknown" : stage;
            this.vkResult = vkResult;
            this.width = width;
            this.height = height;
            this.pixelsVerified = pixelsVerified;
            this.commandBufferSubmitted = commandBufferSubmitted;
            this.fenceSignaled = fenceSignaled;
            this.dataVerified = dataVerified;
            this.format = format == null ? "unknown" : format;
            this.detail = detail == null ? "" : detail;
        }

        public String toDiagnosticText() {
            return "Status: " + (ready ? "PASS" : status.toUpperCase(java.util.Locale.ROOT))
                    + "\nStage: " + stage
                    + "\nVkResult: " + vkResult
                    + "\nImage: " + width + " x " + height
                    + "\nFormat: " + format
                    + "\nPixels verified: " + pixelsVerified + " / " + EXPECTED_PIXELS
                    + "\nCommand buffer submitted: " + commandBufferSubmitted
                    + "\nCompletion fence signaled: " + fenceSignaled
                    + "\nImage read-back verified: " + dataVerified
                    + "\nDetail: " + detail;
        }
    }
}
