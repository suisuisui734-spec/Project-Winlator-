#include <vulkan/vulkan.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

int main(void) {
    VkApplicationInfo app;
    memset(&app, 0, sizeof(app));
    app.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO;
    app.pApplicationName = "MaliForge GX layer smoke test";
    app.applicationVersion = 1;
    app.pEngineName = "MaliForge GX CI";
    app.engineVersion = 1;
    app.apiVersion = VK_API_VERSION_1_1;

    VkInstanceCreateInfo instanceInfo;
    memset(&instanceInfo, 0, sizeof(instanceInfo));
    instanceInfo.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
    instanceInfo.pApplicationInfo = &app;

    VkInstance instance = VK_NULL_HANDLE;
    VkResult result = vkCreateInstance(&instanceInfo, NULL, &instance);
    if (result != VK_SUCCESS) {
        fprintf(stderr, "vkCreateInstance failed: %d\n", (int) result);
        return 10;
    }

    uint32_t count = 0;
    result = vkEnumeratePhysicalDevices(instance, &count, NULL);
    if (result != VK_SUCCESS || count == 0) {
        fprintf(stderr, "No Vulkan physical device: result=%d count=%u\n", (int) result, count);
        vkDestroyInstance(instance, NULL);
        return 11;
    }
    VkPhysicalDevice *devices = calloc(count, sizeof(*devices));
    if (devices == NULL) { vkDestroyInstance(instance, NULL); return 12; }
    result = vkEnumeratePhysicalDevices(instance, &count, devices);
    if (result != VK_SUCCESS || count == 0) {
        free(devices); vkDestroyInstance(instance, NULL); return 13;
    }
    uint32_t groupCount = 0;
    result = vkEnumeratePhysicalDeviceGroups(instance, &groupCount, NULL);
    if (result != VK_SUCCESS && result != VK_INCOMPLETE) {
        free(devices); vkDestroyInstance(instance, NULL); return 17;
    }

    VkPhysicalDeviceProperties properties;
    memset(&properties, 0, sizeof(properties));
    vkGetPhysicalDeviceProperties(devices[0], &properties);
    printf("Software Vulkan device: %s (vendor=%u device=%u)\n",
           properties.deviceName, properties.vendorID, properties.deviceID);

    uint32_t familyCount = 0;
    vkGetPhysicalDeviceQueueFamilyProperties(devices[0], &familyCount, NULL);
    VkQueueFamilyProperties *families = familyCount ? calloc(familyCount, sizeof(*families)) : NULL;
    if (familyCount == 0 || families == NULL) {
        free(devices); vkDestroyInstance(instance, NULL); return 14;
    }
    vkGetPhysicalDeviceQueueFamilyProperties(devices[0], &familyCount, families);
    uint32_t family = UINT32_MAX;
    for (uint32_t n = 0; n < familyCount; ++n) {
        if (families[n].queueCount > 0
                && (families[n].queueFlags & VK_QUEUE_GRAPHICS_BIT) != 0) {
            family = n;
            break;
        }
    }
    free(families);
    if (family == UINT32_MAX) { free(devices); vkDestroyInstance(instance, NULL); return 15; }

    float priority = 1.0f;
    VkDeviceQueueCreateInfo queueInfo;
    memset(&queueInfo, 0, sizeof(queueInfo));
    queueInfo.sType = VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO;
    queueInfo.queueFamilyIndex = family;
    queueInfo.queueCount = 1;
    queueInfo.pQueuePriorities = &priority;

    VkDeviceCreateInfo deviceInfo;
    memset(&deviceInfo, 0, sizeof(deviceInfo));
    deviceInfo.sType = VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO;
    deviceInfo.queueCreateInfoCount = 1;
    deviceInfo.pQueueCreateInfos = &queueInfo;

    VkDevice device = VK_NULL_HANDLE;
    result = vkCreateDevice(devices[0], &deviceInfo, NULL, &device);
    free(devices);
    if (result != VK_SUCCESS) {
        fprintf(stderr, "vkCreateDevice failed: %d\n", (int) result);
        vkDestroyInstance(instance, NULL);
        return 16;
    }
    VkQueue queue = VK_NULL_HANDLE;
    vkGetDeviceQueue(device, family, 0, &queue);
    if (queue == VK_NULL_HANDLE) {
        fprintf(stderr, "vkGetDeviceQueue returned a null queue\n");
        vkDestroyDevice(device, NULL);
        vkDestroyInstance(instance, NULL);
        return 18;
    }

    VkCommandPoolCreateInfo poolInfo;
    memset(&poolInfo, 0, sizeof(poolInfo));
    poolInfo.sType = VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO;
    poolInfo.queueFamilyIndex = family;
    VkCommandPool commandPool = VK_NULL_HANDLE;
    result = vkCreateCommandPool(device, &poolInfo, NULL, &commandPool);
    if (result != VK_SUCCESS) {
        fprintf(stderr, "vkCreateCommandPool failed: %d\n", (int) result);
        vkDestroyDevice(device, NULL);
        vkDestroyInstance(instance, NULL);
        return 19;
    }

    VkCommandBufferAllocateInfo commandInfo;
    memset(&commandInfo, 0, sizeof(commandInfo));
    commandInfo.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO;
    commandInfo.commandPool = commandPool;
    commandInfo.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY;
    commandInfo.commandBufferCount = 1;
    VkCommandBuffer commandBuffer = VK_NULL_HANDLE;
    result = vkAllocateCommandBuffers(device, &commandInfo, &commandBuffer);
    if (result != VK_SUCCESS) {
        fprintf(stderr, "vkAllocateCommandBuffers failed: %d\n", (int) result);
        vkDestroyCommandPool(device, commandPool, NULL);
        vkDestroyDevice(device, NULL);
        vkDestroyInstance(instance, NULL);
        return 20;
    }

    VkCommandBufferBeginInfo beginInfo;
    memset(&beginInfo, 0, sizeof(beginInfo));
    beginInfo.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO;
    beginInfo.flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;
    result = vkBeginCommandBuffer(commandBuffer, &beginInfo);
    if (result == VK_SUCCESS) result = vkEndCommandBuffer(commandBuffer);
    if (result != VK_SUCCESS) {
        fprintf(stderr, "Command buffer record failed: %d\n", (int) result);
        vkDestroyCommandPool(device, commandPool, NULL);
        vkDestroyDevice(device, NULL);
        vkDestroyInstance(instance, NULL);
        return 21;
    }

    VkFenceCreateInfo fenceInfo;
    memset(&fenceInfo, 0, sizeof(fenceInfo));
    fenceInfo.sType = VK_STRUCTURE_TYPE_FENCE_CREATE_INFO;
    VkFence fence = VK_NULL_HANDLE;
    result = vkCreateFence(device, &fenceInfo, NULL, &fence);
    if (result != VK_SUCCESS) {
        fprintf(stderr, "vkCreateFence failed: %d\n", (int) result);
        vkDestroyCommandPool(device, commandPool, NULL);
        vkDestroyDevice(device, NULL);
        vkDestroyInstance(instance, NULL);
        return 22;
    }

    VkSubmitInfo submitInfo;
    memset(&submitInfo, 0, sizeof(submitInfo));
    submitInfo.sType = VK_STRUCTURE_TYPE_SUBMIT_INFO;
    submitInfo.commandBufferCount = 1;
    submitInfo.pCommandBuffers = &commandBuffer;
    result = vkQueueSubmit(queue, 1, &submitInfo, fence);
    if (result != VK_SUCCESS) {
        fprintf(stderr, "vkQueueSubmit failed: %d\n", (int) result);
        vkDestroyFence(device, fence, NULL);
        vkDestroyCommandPool(device, commandPool, NULL);
        vkDestroyDevice(device, NULL);
        vkDestroyInstance(instance, NULL);
        return 23;
    }
    result = vkWaitForFences(device, 1, &fence, VK_TRUE, 10000000000ULL);
    if (result != VK_SUCCESS) {
        fprintf(stderr, "vkWaitForFences failed: %d\n", (int) result);
        vkDestroyFence(device, fence, NULL);
        vkDestroyCommandPool(device, commandPool, NULL);
        vkDestroyDevice(device, NULL);
        vkDestroyInstance(instance, NULL);
        return 24;
    }

    vkDestroyFence(device, fence, NULL);
    vkDestroyCommandPool(device, commandPool, NULL);
    vkDestroyDevice(device, NULL);
    vkDestroyInstance(instance, NULL);
    puts("Vulkan instance/device and command submission lifecycle succeeded.");
    return 0;
}
