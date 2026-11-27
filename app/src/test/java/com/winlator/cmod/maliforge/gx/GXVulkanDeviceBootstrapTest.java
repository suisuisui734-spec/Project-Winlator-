package com.winlator.cmod.maliforge.gx;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GXVulkanDeviceBootstrapTest {
    @Test
    public void parsesRetainedLogicalDeviceAndGraphicsQueue() {
        String payload = "{\"status\":\"ready\",\"stage\":\"logical_device_created\","
                + "\"vkResult\":0,\"deviceName\":\"Mali-G57\","
                + "\"vendorId\":5045,\"deviceId\":2,\"apiVersion\":\"1.3.0\","
                + "\"queueFamilyIndex\":0,\"queueCount\":1,\"graphicsQueue\":true,"
                + "\"retainedInProcess\":true,\"reused\":false,\"detail\":\"ok\"}";
        GXVulkanDeviceBootstrap.Result result = GXVulkanDeviceBootstrap.parse(payload);
        assertTrue(result.ready);
        assertEquals("Mali-G57", result.deviceName);
        assertEquals(0, result.queueFamilyIndex);
        assertTrue(result.graphicsQueue);
        assertTrue(result.retainedInProcess);
    }

    @Test
    public void reportsExistingDeviceAsReused() {
        String payload = "{\"status\":\"ready\",\"stage\":\"logical_device_created\","
                + "\"vkResult\":0,\"deviceName\":\"GPU\",\"queueFamilyIndex\":1,"
                + "\"queueCount\":2,\"graphicsQueue\":true,\"retainedInProcess\":true,"
                + "\"reused\":true}";
        assertTrue(GXVulkanDeviceBootstrap.parse(payload).reused);
    }

    @Test
    public void rejectsReadyStatusWithoutRetainedQueue() {
        GXVulkanDeviceBootstrap.Result result = GXVulkanDeviceBootstrap.parse(
                "{\"status\":\"ready\",\"stage\":\"logical_device_created\","
                        + "\"vkResult\":0,\"deviceName\":\"GPU\","
                        + "\"queueFamilyIndex\":0,\"queueCount\":1,"
                        + "\"graphicsQueue\":false,\"retainedInProcess\":false}");
        assertFalse(result.ready);
        assertEquals("invalid_response", result.status);
    }

    @Test
    public void handlesExplicitFailureAndMalformedPayload() {
        assertFalse(GXVulkanDeviceBootstrap.parse(
                "{\"status\":\"unavailable\",\"stage\":\"create_logical_device\","
                        + "\"vkResult\":-3}").ready);
        assertFalse(GXVulkanDeviceBootstrap.parse("{").ready);
        assertFalse(GXVulkanDeviceBootstrap.parse("").ready);
    }
}
