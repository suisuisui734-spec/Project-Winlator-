package com.winlator.cmod.maliforge.gx;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GXHostVulkanProbeTest {
    @Test
    public void parsesDeviceMetadataAndQueueCapabilities() {
        String payload = "{\"status\":\"available\",\"stage\":\"complete\",\"vkResult\":0,"
                + "\"deviceCount\":1,\"devices\":[{"
                + "\"name\":\"Mali-G57\",\"vendorId\":1600,\"deviceId\":2,"
                + "\"apiVersion\":\"1.3.0\",\"driverVersion\":7,"
                + "\"deviceType\":\"integrated\",\"graphicsQueue\":true,"
                + "\"computeQueue\":true,\"transferQueue\":true,"
                + "\"deviceLocalMemoryBytes\":536870912,"
                + "\"extensionsEnumerated\":true,"
                + "\"extensions\":{\"swapchain\":true,\"synchronization2\":true,"
                + "\"memoryBudget\":false,\"driverProperties\":true}}]}";

        GXHostVulkanProbe.Result result = GXHostVulkanProbe.parse(payload);
        assertTrue(result.available);
        assertEquals(1, result.devices.size());
        assertEquals("Mali-G57", result.devices.get(0).name);
        assertEquals(1600, result.devices.get(0).vendorId);
        assertTrue(result.devices.get(0).graphicsQueue);
        assertTrue(result.devices.get(0).computeQueue);
        assertTrue(result.devices.get(0).swapchain);
        assertEquals(536870912L, result.devices.get(0).deviceLocalMemoryBytes);
    }

    @Test
    public void failedProbeNeverReportsAvailable() {
        GXHostVulkanProbe.Result result = GXHostVulkanProbe.parse(
                "{\"status\":\"unavailable\",\"stage\":\"create_instance\","
                        + "\"vkResult\":-9,\"deviceCount\":0,\"devices\":[]}");
        assertFalse(result.available);
        assertEquals("unavailable", result.status);
        assertEquals("create_instance", result.stage);
        assertTrue(result.devices.isEmpty());
    }

    @Test
    public void successWithoutDeviceRecordsIsRejected() {
        GXHostVulkanProbe.Result result = GXHostVulkanProbe.parse(
                "{\"status\":\"available\",\"stage\":\"complete\","
                        + "\"vkResult\":0,\"deviceCount\":1,\"devices\":[]}");
        assertFalse(result.available);
        assertEquals("invalid_response", result.status);
    }

    @Test
    public void malformedAndEmptyPayloadsFailClosed() {
        assertFalse(GXHostVulkanProbe.parse("{").available);
        assertFalse(GXHostVulkanProbe.parse("").available);
        assertFalse(GXHostVulkanProbe.unavailable("not loaded").available);
    }
}
