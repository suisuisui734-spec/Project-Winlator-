package com.winlator.cmod.maliforge.gx;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Android-host Vulkan color-attachment render-pass execution and read-back test. */
public final class GXVulkanRenderPassTest {
    private static final int EXPECTED_WIDTH = 4;
    private static final int EXPECTED_HEIGHT = 4;
    private static final int EXPECTED_PIXELS = EXPECTED_WIDTH * EXPECTED_HEIGHT;

    private GXVulkanRenderPassTest() {}

    public static Result run() {
        if (!GXNativeBridge.isLoaded()) {
            return unavailable("native_library_unavailable",
                    "The Winlator native library is not loaded.");
        }
        try {
            return parse(GXNativeBridge.runVulkanRenderPassTest());
        } catch (RuntimeException | LinkageError error) {
            return unavailable("native_call_failed",
                    error.getClass().getSimpleName() + ": "
                            + (error.getMessage() == null ? "No details" : error.getMessage()));
        }
    }

    static Result parse(String payload) {
        if (payload == null || payload.trim().isEmpty()) {
            return unavailable("empty_response",
                    "The native Vulkan render-pass test returned no response.");
        }
        try {
            JsonElement element = JsonParser.parseString(payload);
            if (!element.isJsonObject()) {
                return unavailable("invalid_response",
                        "The native render-pass response is not a JSON object.");
            }
            JsonObject object = element.getAsJsonObject();
            String status = stringValue(object, "status", "unknown");
            String stage = stringValue(object, "stage", "unknown");
            int vkResult = intValue(object, "vkResult", -1);
            int width = intValue(object, "width", 0);
            int height = intValue(object, "height", 0);
            int pixelsVerified = intValue(object, "pixelsVerified", 0);
            boolean imageViewCreated = booleanValue(object, "imageViewCreated", false);
            boolean renderPassCreated = booleanValue(object, "renderPassCreated", false);
            boolean framebufferCreated = booleanValue(object, "framebufferCreated", false);
            boolean submitted = booleanValue(object, "commandBufferSubmitted", false);
            boolean fenceSignaled = booleanValue(object, "fenceSignaled", false);
            boolean renderPassExecuted = booleanValue(object, "renderPassExecuted", false);
            boolean dataVerified = booleanValue(object, "dataVerified", false);
            String format = stringValue(object, "format", "unknown");
            String detail = stringValue(object, "detail", "No render-pass test detail was returned.");

            boolean ready = "passed".equals(status) && "renderpass_pixels_verified".equals(stage)
                    && vkResult == 0 && width == EXPECTED_WIDTH && height == EXPECTED_HEIGHT
                    && pixelsVerified == EXPECTED_PIXELS && imageViewCreated && renderPassCreated
                    && framebufferCreated && submitted && fenceSignaled && renderPassExecuted
                    && dataVerified && "VK_FORMAT_R8G8B8A8_UNORM".equals(format);
            if ("passed".equals(status) && !ready) {
                return new Result(false, "invalid_response", stage, vkResult, width, height,
                        pixelsVerified, imageViewCreated, renderPassCreated, framebufferCreated,
                        submitted, fenceSignaled, renderPassExecuted, dataVerified, format,
                        "Native response claimed PASS without all image-view, render-pass, framebuffer, execution, and pixel checks.");
            }
            return new Result(ready, status, stage, vkResult, width, height, pixelsVerified,
                    imageViewCreated, renderPassCreated, framebufferCreated, submitted,
                    fenceSignaled, renderPassExecuted, dataVerified, format, detail);
        } catch (RuntimeException error) {
            return unavailable("invalid_response",
                    "Unable to parse native Vulkan render-pass response: "
                            + error.getClass().getSimpleName());
        }
    }

    private static Result unavailable(String stage, String detail) {
        return new Result(false, "unavailable", stage, -1, 0, 0, 0,
                false, false, false, false, false, false, false, "unknown", detail);
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
        public final boolean imageViewCreated;
        public final boolean renderPassCreated;
        public final boolean framebufferCreated;
        public final boolean commandBufferSubmitted;
        public final boolean fenceSignaled;
        public final boolean renderPassExecuted;
        public final boolean dataVerified;
        public final String format;
        public final String detail;

        private Result(boolean ready, String status, String stage, int vkResult, int width, int height,
                       int pixelsVerified, boolean imageViewCreated, boolean renderPassCreated,
                       boolean framebufferCreated, boolean commandBufferSubmitted,
                       boolean fenceSignaled, boolean renderPassExecuted, boolean dataVerified,
                       String format, String detail) {
            this.ready = ready;
            this.status = status == null ? "unknown" : status;
            this.stage = stage == null ? "unknown" : stage;
            this.vkResult = vkResult;
            this.width = width;
            this.height = height;
            this.pixelsVerified = pixelsVerified;
            this.imageViewCreated = imageViewCreated;
            this.renderPassCreated = renderPassCreated;
            this.framebufferCreated = framebufferCreated;
            this.commandBufferSubmitted = commandBufferSubmitted;
            this.fenceSignaled = fenceSignaled;
            this.renderPassExecuted = renderPassExecuted;
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
                    + "\nImage view created: " + imageViewCreated
                    + "\nRender pass created: " + renderPassCreated
                    + "\nFramebuffer created: " + framebufferCreated
                    + "\nCommand buffer submitted: " + commandBufferSubmitted
                    + "\nCompletion fence signaled: " + fenceSignaled
                    + "\nRender pass executed: " + renderPassExecuted
                    + "\nRead-back verified: " + dataVerified
                    + "\nDetail: " + detail;
        }
    }
}
