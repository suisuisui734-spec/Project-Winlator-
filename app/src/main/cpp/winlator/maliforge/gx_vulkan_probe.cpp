#include <jni.h>
#include <vulkan/vulkan.h>

#include <cstdint>
#include <limits>
#include <sstream>
#include <string>
#include <vector>

namespace {

std::string jsonEscape(const char *value) {
    std::string output;
    if (value == nullptr) return output;
    for (const unsigned char c : std::string(value)) {
        switch (c) {
            case '"': output += "\\\""; break;
            case '\\': output += "\\\\"; break;
            case '\b': output += "\\b"; break;
            case '\f': output += "\\f"; break;
            case '\n': output += "\\n"; break;
            case '\r': output += "\\r"; break;
            case '\t': output += "\\t"; break;
            default:
                if (c < 0x20) {
                    static const char hex[] = "0123456789abcdef";
                    output += "\\u00";
                    output += hex[(c >> 4) & 0x0f];
                    output += hex[c & 0x0f];
                } else {
                    output += static_cast<char>(c);
                }
        }
    }
    return output;
}

const char *deviceTypeName(VkPhysicalDeviceType type) {
    switch (type) {
        case VK_PHYSICAL_DEVICE_TYPE_INTEGRATED_GPU: return "integrated";
        case VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU: return "discrete";
        case VK_PHYSICAL_DEVICE_TYPE_VIRTUAL_GPU: return "virtual";
        case VK_PHYSICAL_DEVICE_TYPE_CPU: return "cpu";
        case VK_PHYSICAL_DEVICE_TYPE_OTHER:
        default: return "other";
    }
}

bool hasDeviceExtension(const std::vector<VkExtensionProperties> &extensions,
                        const char *requested) {
    for (const VkExtensionProperties &extension : extensions) {
        if (std::string(extension.extensionName) == requested) return true;
    }
    return false;
}

std::string failureResult(const char *status, const char *stage, VkResult result) {
    std::ostringstream json;
    json << "{\"status\":\"" << status << "\",\"stage\":\"" << stage
         << "\",\"vkResult\":" << static_cast<int>(result)
         << ",\"deviceCount\":0,\"devices\":[]}";
    return json.str();
}

std::string probeHostVulkan() {
    VkApplicationInfo applicationInfo{};
    applicationInfo.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO;
    applicationInfo.pApplicationName = "MaliForge GX host preflight";
    applicationInfo.applicationVersion = VK_MAKE_VERSION(0, 4, 0);
    applicationInfo.pEngineName = "MaliForge GX";
    applicationInfo.engineVersion = VK_MAKE_VERSION(0, 4, 0);
    // Vulkan 1.0 instance creation keeps this read-only probe compatible with older Android loaders.
    applicationInfo.apiVersion = VK_API_VERSION_1_0;

    VkInstanceCreateInfo createInfo{};
    createInfo.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
    createInfo.pApplicationInfo = &applicationInfo;

    VkInstance instance = VK_NULL_HANDLE;
    VkResult result = vkCreateInstance(&createInfo, nullptr, &instance);
    if (result != VK_SUCCESS || instance == VK_NULL_HANDLE) {
        return failureResult("unavailable", "create_instance", result);
    }

    uint32_t deviceCount = 0;
    result = vkEnumeratePhysicalDevices(instance, &deviceCount, nullptr);
    if (result != VK_SUCCESS) {
        vkDestroyInstance(instance, nullptr);
        return failureResult("unavailable", "enumerate_physical_devices", result);
    }
    if (deviceCount == 0) {
        vkDestroyInstance(instance, nullptr);
        return failureResult("no_devices", "enumerate_physical_devices", VK_SUCCESS);
    }

    std::vector<VkPhysicalDevice> physicalDevices(deviceCount, VK_NULL_HANDLE);
    result = vkEnumeratePhysicalDevices(instance, &deviceCount, physicalDevices.data());
    if (result != VK_SUCCESS) {
        vkDestroyInstance(instance, nullptr);
        return failureResult("unavailable", "read_physical_devices", result);
    }
    physicalDevices.resize(deviceCount);

    std::ostringstream json;
    json << "{\"status\":\"available\",\"stage\":\"complete\",\"vkResult\":0"
         << ",\"deviceCount\":" << physicalDevices.size() << ",\"devices\":[";

    bool firstDevice = true;
    for (VkPhysicalDevice physicalDevice : physicalDevices) {
        if (physicalDevice == VK_NULL_HANDLE) continue;

        VkPhysicalDeviceProperties properties{};
        vkGetPhysicalDeviceProperties(physicalDevice, &properties);

        uint32_t queueFamilyCount = 0;
        vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice, &queueFamilyCount, nullptr);
        std::vector<VkQueueFamilyProperties> queueFamilies(queueFamilyCount);
        if (queueFamilyCount > 0) {
            vkGetPhysicalDeviceQueueFamilyProperties(
                    physicalDevice, &queueFamilyCount, queueFamilies.data());
            queueFamilies.resize(queueFamilyCount);
        }

        bool graphicsQueue = false;
        bool computeQueue = false;
        bool transferQueue = false;
        for (const VkQueueFamilyProperties &queueFamily : queueFamilies) {
            if (queueFamily.queueCount == 0) continue;
            graphicsQueue = graphicsQueue
                    || (queueFamily.queueFlags & VK_QUEUE_GRAPHICS_BIT) != 0;
            computeQueue = computeQueue
                    || (queueFamily.queueFlags & VK_QUEUE_COMPUTE_BIT) != 0;
            transferQueue = transferQueue
                    || (queueFamily.queueFlags & VK_QUEUE_TRANSFER_BIT) != 0;
        }

        VkPhysicalDeviceMemoryProperties memoryProperties{};
        vkGetPhysicalDeviceMemoryProperties(physicalDevice, &memoryProperties);
        uint64_t deviceLocalMemoryBytes = 0;
        for (uint32_t i = 0; i < memoryProperties.memoryHeapCount; ++i) {
            const VkMemoryHeap &heap = memoryProperties.memoryHeaps[i];
            if ((heap.flags & VK_MEMORY_HEAP_DEVICE_LOCAL_BIT) == 0) continue;
            const uint64_t heapSize = static_cast<uint64_t>(heap.size);
            if (std::numeric_limits<uint64_t>::max() - deviceLocalMemoryBytes < heapSize) {
                deviceLocalMemoryBytes = std::numeric_limits<uint64_t>::max();
                break;
            }
            deviceLocalMemoryBytes += heapSize;
        }

        uint32_t extensionCount = 0;
        std::vector<VkExtensionProperties> extensions;
        VkResult extensionResult = vkEnumerateDeviceExtensionProperties(
                physicalDevice, nullptr, &extensionCount, nullptr);
        bool extensionsEnumerated = extensionResult == VK_SUCCESS;
        if (extensionsEnumerated && extensionCount > 0) {
            extensions.resize(extensionCount);
            extensionResult = vkEnumerateDeviceExtensionProperties(
                    physicalDevice, nullptr, &extensionCount, extensions.data());
            extensionsEnumerated = extensionResult == VK_SUCCESS;
            if (extensionsEnumerated) extensions.resize(extensionCount);
            else extensions.clear();
        }

        if (!firstDevice) json << ',';
        firstDevice = false;
        json << "{\"name\":\"" << jsonEscape(properties.deviceName) << "\""
             << ",\"vendorId\":" << properties.vendorID
             << ",\"deviceId\":" << properties.deviceID
             << ",\"apiVersion\":\"" << VK_VERSION_MAJOR(properties.apiVersion)
             << '.' << VK_VERSION_MINOR(properties.apiVersion)
             << '.' << VK_VERSION_PATCH(properties.apiVersion) << "\""
             << ",\"driverVersion\":" << properties.driverVersion
             << ",\"deviceType\":\"" << deviceTypeName(properties.deviceType) << "\""
             << ",\"graphicsQueue\":" << (graphicsQueue ? "true" : "false")
             << ",\"computeQueue\":" << (computeQueue ? "true" : "false")
             << ",\"transferQueue\":" << (transferQueue ? "true" : "false")
             << ",\"deviceLocalMemoryBytes\":" << deviceLocalMemoryBytes
             << ",\"extensionsEnumerated\":" << (extensionsEnumerated ? "true" : "false")
             << ",\"extensions\":{"
             << "\"swapchain\":" << (hasDeviceExtension(extensions, VK_KHR_SWAPCHAIN_EXTENSION_NAME) ? "true" : "false")
             << ",\"synchronization2\":" << (hasDeviceExtension(extensions, "VK_KHR_synchronization2") ? "true" : "false")
             << ",\"memoryBudget\":" << (hasDeviceExtension(extensions, "VK_EXT_memory_budget") ? "true" : "false")
             << ",\"driverProperties\":" << (hasDeviceExtension(extensions, "VK_KHR_driver_properties") ? "true" : "false")
             << "}}";
    }

    json << "]}";
    vkDestroyInstance(instance, nullptr);
    return json.str();
}

}  // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_winlator_cmod_maliforge_gx_GXNativeBridge_nativeProbeHostVulkan(
        JNIEnv *env, jclass) {
    const std::string result = probeHostVulkan();
    return env->NewStringUTF(result.c_str());
}
