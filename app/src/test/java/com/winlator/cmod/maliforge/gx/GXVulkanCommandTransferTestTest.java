package com.winlator.cmod.maliforge.gx;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GXVulkanCommandTransferTestTest {
    @Test
    public void acceptsOnlySubmittedAndVerifiedTransfer() {
        String payload = "{\"status\":\"passed\",\"stage\":\"data_verified\","
                + "\"vkResult\":0,\"bytesCopied\":64,"
                + "\"commandBufferSubmitted\":true,\"fenceSignaled\":true,"
                + "\"dataVerified\":true,\"detail\":\"copy verified\"}";
        GXVulkanCommandTransferTest.Result result = GXVulkanCommandTransferTest.parse(payload);
        assertTrue(result.ready);
        assertEquals(64, result.bytesCopied);
        assertTrue(result.commandBufferSubmitted);
        assertTrue(result.fenceSignaled);
        assertTrue(result.dataVerified);
    }

    @Test
    public void rejectsSuccessClaimWhenAnyExecutionProofIsMissing() {
        String payload = "{\"status\":\"passed\",\"stage\":\"data_verified\","
                + "\"vkResult\":0,\"bytesCopied\":64,"
                + "\"commandBufferSubmitted\":true,\"fenceSignaled\":false,"
                + "\"dataVerified\":true}";
        GXVulkanCommandTransferTest.Result result = GXVulkanCommandTransferTest.parse(payload);
        assertFalse(result.ready);
        assertEquals("invalid_response", result.status);
    }

    @Test
    public void preservesFailureAndUnavailableStatuses() {
        GXVulkanCommandTransferTest.Result failed = GXVulkanCommandTransferTest.parse(
                "{\"status\":\"failed\",\"stage\":\"submit_command_buffer\","
                        + "\"vkResult\":-4,\"detail\":\"submission failed\"}");
        assertFalse(failed.ready);
        assertEquals("failed", failed.status);
        assertEquals("submit_command_buffer", failed.stage);

        GXVulkanCommandTransferTest.Result unavailable = GXVulkanCommandTransferTest.parse(
                "{\"status\":\"unavailable\",\"stage\":\"host_visible_memory_unavailable\"}");
        assertFalse(unavailable.ready);
        assertEquals("unavailable", unavailable.status);
    }

    @Test
    public void rejectsMalformedOrEmptyPayloads() {
        assertFalse(GXVulkanCommandTransferTest.parse("{").ready);
        assertFalse(GXVulkanCommandTransferTest.parse("").ready);
        assertFalse(GXVulkanCommandTransferTest.parse("[]").ready);
    }
}
