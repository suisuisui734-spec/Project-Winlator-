package com.winlator.cmod.maliforge.gx;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GXVulkanImageClearTestTest {
    @Test
    public void acceptsImageClearOnlyAfterAllPixelsAreVerified() {
        String payload = "{\"status\":\"passed\",\"stage\":\"image_data_verified\","
                + "\"vkResult\":0,\"width\":4,\"height\":4,\"pixelsVerified\":16,"
                + "\"commandBufferSubmitted\":true,\"fenceSignaled\":true,"
                + "\"dataVerified\":true,\"format\":\"VK_FORMAT_R8G8B8A8_UNORM\"}";
        GXVulkanImageClearTest.Result result = GXVulkanImageClearTest.parse(payload);
        assertTrue(result.ready);
        assertEquals(16, result.pixelsVerified);
        assertTrue(result.commandBufferSubmitted);
        assertTrue(result.fenceSignaled);
        assertTrue(result.dataVerified);
    }

    @Test
    public void rejectsFalseSuccessAndPartialPixelReadback() {
        String payload = "{\"status\":\"passed\",\"stage\":\"image_data_verified\","
                + "\"vkResult\":0,\"width\":4,\"height\":4,\"pixelsVerified\":15,"
                + "\"commandBufferSubmitted\":true,\"fenceSignaled\":true,"
                + "\"dataVerified\":true,\"format\":\"VK_FORMAT_R8G8B8A8_UNORM\"}";
        GXVulkanImageClearTest.Result result = GXVulkanImageClearTest.parse(payload);
        assertFalse(result.ready);
        assertEquals("invalid_response", result.status);
    }

    @Test
    public void preservesUnsupportedAndFailedImageTests() {
        GXVulkanImageClearTest.Result unavailable = GXVulkanImageClearTest.parse(
                "{\"status\":\"unavailable\",\"stage\":\"image_format_query\","
                        + "\"vkResult\":-11}");
        assertFalse(unavailable.ready);
        assertEquals("unavailable", unavailable.status);

        GXVulkanImageClearTest.Result failed = GXVulkanImageClearTest.parse(
                "{\"status\":\"failed\",\"stage\":\"verify_image_readback\","
                        + "\"vkResult\":-13}");
        assertFalse(failed.ready);
        assertEquals("failed", failed.status);
    }

    @Test
    public void rejectsMalformedResponses() {
        assertFalse(GXVulkanImageClearTest.parse("{").ready);
        assertFalse(GXVulkanImageClearTest.parse("").ready);
        assertFalse(GXVulkanImageClearTest.parse("[]").ready);
    }
}
