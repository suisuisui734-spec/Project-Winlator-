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
    public void parsesAdvertisedExtensionsFeaturesMemoryAndLimits() {
        String payload = "{\"status\":\"ready\",\"stage\":\"logical_device_created\","
                + "\"vkResult\":0,\"deviceName\":\"Mali-G57\",\"deviceType\":\"INTEGRATED_GPU\","
                + "\"driverVersion\":1234,\"apiVersion\":\"1.3.0\",\"queueFamilyIndex\":0,"
                + "\"queueCount\":1,\"graphicsQueue\":true,\"retainedInProcess\":true,"
                + "\"extensionsEnumerated\":true,"
                + "\"deviceExtensions\":[\"VK_KHR_swapchain\",\"VK_KHR_synchronization2\"],"
                + "\"availableFeatures\":[\"samplerAnisotropy\",\"geometryShader\"],"
                + "\"memoryHeapCount\":2,\"memoryTypeCount\":4,\"deviceLocalHeapBytes\":4096,"
                + "\"hostVisibleMemoryTypeCount\":2,\"maxImageDimension2D\":8192,"
                + "\"maxBoundDescriptorSets\":8,\"maxPushConstantsSize\":128,"
                + "\"timestampComputeAndGraphics\":true}";
        GXVulkanDeviceBootstrap.Result result = GXVulkanDeviceBootstrap.parse(payload);
        assertTrue(result.ready);
        assertEquals("INTEGRATED_GPU", result.deviceType);
        assertEquals(1234L, result.driverVersion);
        assertTrue(result.extensionsEnumerated);
        assertTrue(result.hasExtension("VK_KHR_swapchain"));
        assertTrue(result.hasFeature("samplerAnisotropy"));
        assertEquals(4096L, result.deviceLocalHeapBytes);
        assertEquals(8192, result.maxImageDimension2D);
        assertEquals(8, result.maxBoundDescriptorSets);
        assertTrue(result.timestampComputeAndGraphics);
        assertTrue(result.toDiagnosticText().contains("not enabled"));
    }

    @Test
    public void allowsDeviceReadyWhenExtensionEnumerationIsUnavailable() {
        String payload = "{\"status\":\"ready\",\"stage\":\"logical_device_created\","
                + "\"vkResult\":0,\"deviceName\":\"GPU\",\"queueFamilyIndex\":0,"
                + "\"queueCount\":1,\"graphicsQueue\":true,\"retainedInProcess\":true,"
                + "\"extensionsEnumerated\":false}";
        GXVulkanDeviceBootstrap.Result result = GXVulkanDeviceBootstrap.parse(payload);
        assertTrue(result.ready);
        assertFalse(result.extensionsEnumerated);
        assertFalse(result.hasExtension("VK_KHR_swapchain"));
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
