package com.winlator.cmod.maliforge.gx;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Real Android-host Vulkan buffer-copy command smoke test; not a game renderer. */
public final class GXVulkanCommandTransferTest {
    private static final int EXPECTED_BYTES = 64;

    private GXVulkanCommandTransferTest() {}

    public static Result run() {
        if (!GXNativeBridge.isLoaded()) {
            return unavailable("native_library_unavailable",
                    "The Winlator native library is not loaded.");
        }
        try {
            return parse(GXNativeBridge.runVulkanCommandTransferTest());
        } catch (RuntimeException | LinkageError error) {
            return unavailable("native_call_failed",
                    error.getClass().getSimpleName() + ": "
                            + (error.getMessage() == null ? "No details" : error.getMessage()));
        }
    }

    static Result parse(String payload) {
        if (payload == null || payload.trim().isEmpty()) {
            return unavailable("empty_response",
                    "The native Vulkan command test returned no response.");
        }
        try {
            JsonElement element = JsonParser.parseString(payload);
            if (!element.isJsonObject()) {
                return unavailable("invalid_response",
                        "The native Vulkan command-test response is not a JSON object.");
            }
            JsonObject object = element.getAsJsonObject();
            String status = stringValue(object, "status", "unknown");
            String stage = stringValue(object, "stage", "unknown");
            int vkResult = intValue(object, "vkResult", -1);
            int bytesCopied = intValue(object, "bytesCopied", 0);
            boolean submitted = booleanValue(object, "commandBufferSubmitted", false);
            boolean fenceSignaled = booleanValue(object, "fenceSignaled", false);
            boolean dataVerified = booleanValue(object, "dataVerified", false);
            String detail = stringValue(object, "detail",
                    "No Vulkan command-test details were returned.");

            boolean ready = "passed".equals(status) && "data_verified".equals(stage)
                    && vkResult == 0 && bytesCopied == EXPECTED_BYTES
                    && submitted && fenceSignaled && dataVerified;
            if ("passed".equals(status) && !ready) {
                return new Result(false, "invalid_response", stage, vkResult, bytesCopied,
                        submitted, fenceSignaled, dataVerified,
                        "Native response claimed PASS without all required execution and data checks.");
            }
            return new Result(ready, status, stage, vkResult, bytesCopied,
                    submitted, fenceSignaled, dataVerified, detail);
        } catch (RuntimeException error) {
            return unavailable("invalid_response",
                    "Unable to parse native Vulkan command-test response: "
                            + error.getClass().getSimpleName());
        }
    }

    private static Result unavailable(String stage, String detail) {
        return new Result(false, "unavailable", stage, -1, 0,
                false, false, false, detail);
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
        public final int bytesCopied;
        public final boolean commandBufferSubmitted;
        public final boolean fenceSignaled;
        public final boolean dataVerified;
        public final String detail;

        private Result(boolean ready, String status, String stage, int vkResult, int bytesCopied,
                       boolean commandBufferSubmitted, boolean fenceSignaled,
                       boolean dataVerified, String detail) {
            this.ready = ready;
            this.status = status == null ? "unknown" : status;
            this.stage = stage == null ? "unknown" : stage;
            this.vkResult = vkResult;
            this.bytesCopied = bytesCopied;
            this.commandBufferSubmitted = commandBufferSubmitted;
            this.fenceSignaled = fenceSignaled;
            this.dataVerified = dataVerified;
            this.detail = detail == null ? "" : detail;
        }

        public String toDiagnosticText() {
            return "Status: " + (ready ? "PASS" : status.toUpperCase(java.util.Locale.ROOT))
                    + "\nStage: " + stage
                    + "\nVkResult: " + vkResult
                    + "\nBytes copied and verified: " + bytesCopied
                    + "\nCommand buffer submitted: " + commandBufferSubmitted
                    + "\nCompletion fence signaled: " + fenceSignaled
                    + "\nDestination data verified: " + dataVerified
                    + "\nDetail: " + detail;
        }
    }
}
