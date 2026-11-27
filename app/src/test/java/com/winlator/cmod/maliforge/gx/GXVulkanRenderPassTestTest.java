package com.winlator.cmod.maliforge.gx;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GXVulkanRenderPassTestTest {
    @Test
    public void acceptsOnlySubmittedRenderPassWithVerifiedAttachmentPixels() {
        String payload = "{\"status\":\"passed\",\"stage\":\"renderpass_pixels_verified\","
                + "\"vkResult\":0,\"width\":4,\"height\":4,\"pixelsVerified\":16,"
                + "\"imageViewCreated\":true,\"renderPassCreated\":true,\"framebufferCreated\":true,"
                + "\"commandBufferSubmitted\":true,\"fenceSignaled\":true,\"renderPassExecuted\":true,"
                + "\"dataVerified\":true,\"format\":\"VK_FORMAT_R8G8B8A8_UNORM\"}";
        GXVulkanRenderPassTest.Result result = GXVulkanRenderPassTest.parse(payload);
        assertTrue(result.ready);
        assertEquals(16, result.pixelsVerified);
        assertTrue(result.imageViewCreated);
        assertTrue(result.renderPassCreated);
        assertTrue(result.framebufferCreated);
        assertTrue(result.renderPassExecuted);
        assertTrue(result.dataVerified);
    }

    @Test
    public void rejectsFalseSuccessWhenFramebufferOrPixelsAreNotVerified() {
        String payload = "{\"status\":\"passed\",\"stage\":\"renderpass_pixels_verified\","
                + "\"vkResult\":0,\"width\":4,\"height\":4,\"pixelsVerified\":15,"
                + "\"imageViewCreated\":true,\"renderPassCreated\":true,\"framebufferCreated\":false,"
                + "\"commandBufferSubmitted\":true,\"fenceSignaled\":true,\"renderPassExecuted\":true,"
                + "\"dataVerified\":true,\"format\":\"VK_FORMAT_R8G8B8A8_UNORM\"}";
        GXVulkanRenderPassTest.Result result = GXVulkanRenderPassTest.parse(payload);
        assertFalse(result.ready);
        assertEquals("invalid_response", result.status);
    }

    @Test
    public void preservesUnavailableAndFailedStatuses() {
        GXVulkanRenderPassTest.Result unavailable = GXVulkanRenderPassTest.parse(
                "{\"status\":\"unavailable\",\"stage\":\"color_attachment_format_query\","
                        + "\"vkResult\":-11}");
        assertFalse(unavailable.ready);
        assertEquals("unavailable", unavailable.status);

        GXVulkanRenderPassTest.Result failed = GXVulkanRenderPassTest.parse(
                "{\"status\":\"failed\",\"stage\":\"submit_render_pass\",\"vkResult\":-4}");
        assertFalse(failed.ready);
        assertEquals("failed", failed.status);
    }

    @Test
    public void rejectsMalformedResponses() {
        assertFalse(GXVulkanRenderPassTest.parse("{").ready);
        assertFalse(GXVulkanRenderPassTest.parse("").ready);
        assertFalse(GXVulkanRenderPassTest.parse("[]").ready);
    }
}
