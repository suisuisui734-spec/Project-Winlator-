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
        if (families[n].queueCount > 0) { family = n; break; }
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
    vkDestroyDevice(device, NULL);
    vkDestroyInstance(instance, NULL);
    puts("Vulkan instance/device lifecycle succeeded.");
    return 0;
}
