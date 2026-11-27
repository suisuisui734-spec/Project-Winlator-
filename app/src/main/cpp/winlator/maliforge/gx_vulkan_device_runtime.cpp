#include <jni.h>
#include <vulkan/vulkan.h>

#include <mutex>
#include <sstream>
#include <string>
#include <vector>

namespace {

struct VulkanDeviceRuntime {
    VkInstance instance = VK_NULL_HANDLE;
    VkPhysicalDevice physicalDevice = VK_NULL_HANDLE;
    VkDevice device = VK_NULL_HANDLE;
    VkQueue graphicsQueue = VK_NULL_HANDLE;
    VkPhysicalDeviceProperties properties{};
    uint32_t graphicsQueueFamily = UINT32_MAX;
    uint32_t queueCount = 0;
};

std::mutex g_runtimeMutex;
VulkanDeviceRuntime g_runtime;

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

std::string failureResult(const char *stage, VkResult result, const char *detail) {
    std::ostringstream json;
    json << "{\"status\":\"unavailable\",\"stage\":\"" << stage
         << "\",\"vkResult\":" << static_cast<int>(result)
         << ",\"deviceName\":\"\",\"vendorId\":0,\"deviceId\":0"
         << ",\"queueFamilyIndex\":-1,\"queueCount\":0"
         << ",\"graphicsQueue\":false,\"detail\":\"" << jsonEscape(detail)
         << "\"}";
    return json.str();
}

void destroyRuntime(VulkanDeviceRuntime *runtime) {
    if (runtime == nullptr) return;
    if (runtime->device != VK_NULL_HANDLE) {
        vkDeviceWaitIdle(runtime->device);
        vkDestroyDevice(runtime->device, nullptr);
    }
    if (runtime->instance != VK_NULL_HANDLE) {
        vkDestroyInstance(runtime->instance, nullptr);
    }
    *runtime = VulkanDeviceRuntime{};
}

std::string readyResult(bool reused) {
    std::ostringstream json;
    json << "{\"status\":\"ready\",\"stage\":\"logical_device_created\""
         << ",\"vkResult\":0,\"deviceName\":\"" << jsonEscape(g_runtime.properties.deviceName) << "\""
         << ",\"vendorId\":" << g_runtime.properties.vendorID
         << ",\"deviceId\":" << g_runtime.properties.deviceID
         << ",\"apiVersion\":\"" << VK_VERSION_MAJOR(g_runtime.properties.apiVersion)
         << '.' << VK_VERSION_MINOR(g_runtime.properties.apiVersion)
         << '.' << VK_VERSION_PATCH(g_runtime.properties.apiVersion) << "\""
         << ",\"queueFamilyIndex\":" << g_runtime.graphicsQueueFamily
         << ",\"queueCount\":" << g_runtime.queueCount
         << ",\"graphicsQueue\":true,\"retainedInProcess\":true"
         << ",\"reused\":" << (reused ? "true" : "false")
         << ",\"detail\":\"Host VkDevice and graphics queue are initialized and retained. "
            "No game surface, swapchain, Wine renderer, or guest ICD is created.\"}";
    return json.str();
}

std::string initializeHostDevice() {
    std::lock_guard<std::mutex> lock(g_runtimeMutex);
    if (g_runtime.instance != VK_NULL_HANDLE
            && g_runtime.device != VK_NULL_HANDLE
            && g_runtime.graphicsQueue != VK_NULL_HANDLE) {
        return readyResult(true);
    }

    VulkanDeviceRuntime candidate;
    VkApplicationInfo applicationInfo{};
    applicationInfo.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO;
    applicationInfo.pApplicationName = "MaliForge GX Vulkan device bootstrap";
    applicationInfo.applicationVersion = VK_MAKE_VERSION(0, 4, 0);
    applicationInfo.pEngineName = "MaliForge GX";
    applicationInfo.engineVersion = VK_MAKE_VERSION(0, 4, 0);
    applicationInfo.apiVersion = VK_API_VERSION_1_0;

    VkInstanceCreateInfo instanceInfo{};
    instanceInfo.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
    instanceInfo.pApplicationInfo = &applicationInfo;

    VkResult result = vkCreateInstance(&instanceInfo, nullptr, &candidate.instance);
    if (result != VK_SUCCESS || candidate.instance == VK_NULL_HANDLE) {
        destroyRuntime(&candidate);
        return failureResult("create_instance", result, "Android host Vulkan instance creation failed.");
    }

    uint32_t physicalDeviceCount = 0;
    result = vkEnumeratePhysicalDevices(candidate.instance, &physicalDeviceCount, nullptr);
    if (result != VK_SUCCESS || physicalDeviceCount == 0) {
        destroyRuntime(&candidate);
        return failureResult("enumerate_physical_devices",
                result == VK_SUCCESS ? VK_ERROR_INITIALIZATION_FAILED : result,
                "No host Vulkan physical device could be enumerated.");
    }

    std::vector<VkPhysicalDevice> physicalDevices(physicalDeviceCount, VK_NULL_HANDLE);
    result = vkEnumeratePhysicalDevices(candidate.instance, &physicalDeviceCount, physicalDevices.data());
    if (result != VK_SUCCESS) {
        destroyRuntime(&candidate);
        return failureResult("read_physical_devices", result,
                "Host Vulkan physical-device enumeration failed.");
    }
    physicalDevices.resize(physicalDeviceCount);

    bool foundGraphicsQueue = false;
    for (VkPhysicalDevice physicalDevice : physicalDevices) {
        if (physicalDevice == VK_NULL_HANDLE) continue;

        uint32_t familyCount = 0;
        vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice, &familyCount, nullptr);
        if (familyCount == 0) continue;

        std::vector<VkQueueFamilyProperties> families(familyCount);
        vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice, &familyCount, families.data());
        families.resize(familyCount);

        for (uint32_t familyIndex = 0; familyIndex < familyCount; ++familyIndex) {
            const VkQueueFamilyProperties &family = families[familyIndex];
            if (family.queueCount == 0 || (family.queueFlags & VK_QUEUE_GRAPHICS_BIT) == 0) continue;

            candidate.physicalDevice = physicalDevice;
            candidate.graphicsQueueFamily = familyIndex;
            candidate.queueCount = family.queueCount;
            vkGetPhysicalDeviceProperties(physicalDevice, &candidate.properties);
            foundGraphicsQueue = true;
            break;
        }
        if (foundGraphicsQueue) break;
    }

    if (!foundGraphicsQueue) {
        destroyRuntime(&candidate);
        return failureResult("select_graphics_queue", VK_ERROR_FEATURE_NOT_PRESENT,
                "No host Vulkan queue family supports graphics commands.");
    }

    const float queuePriority = 1.0f;
    VkDeviceQueueCreateInfo queueInfo{};
    queueInfo.sType = VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO;
    queueInfo.queueFamilyIndex = candidate.graphicsQueueFamily;
    queueInfo.queueCount = 1;
    queueInfo.pQueuePriorities = &queuePriority;

    // Enable no optional features or extensions at this stage. Later renderer work must
    // negotiate each required capability explicitly instead of assuming device support.
    VkPhysicalDeviceFeatures enabledFeatures{};
    VkDeviceCreateInfo deviceInfo{};
    deviceInfo.sType = VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO;
    deviceInfo.queueCreateInfoCount = 1;
    deviceInfo.pQueueCreateInfos = &queueInfo;
    deviceInfo.pEnabledFeatures = &enabledFeatures;

    result = vkCreateDevice(candidate.physicalDevice, &deviceInfo, nullptr, &candidate.device);
    if (result != VK_SUCCESS || candidate.device == VK_NULL_HANDLE) {
        destroyRuntime(&candidate);
        return failureResult("create_logical_device", result,
                "Host Vulkan logical-device creation failed for the selected graphics queue.");
    }

    vkGetDeviceQueue(candidate.device, candidate.graphicsQueueFamily, 0, &candidate.graphicsQueue);
    if (candidate.graphicsQueue == VK_NULL_HANDLE) {
        destroyRuntime(&candidate);
        return failureResult("get_graphics_queue", VK_ERROR_INITIALIZATION_FAILED,
                "The Vulkan device was created but returned a null graphics queue.");
    }

    g_runtime = candidate;
    return readyResult(false);
}

void shutdownHostDevice() {
    std::lock_guard<std::mutex> lock(g_runtimeMutex);
    destroyRuntime(&g_runtime);
}

}  // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_winlator_cmod_maliforge_gx_GXNativeBridge_nativeInitializeHostVulkanDevice(
        JNIEnv *env, jclass) {
    const std::string result = initializeHostDevice();
    return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_winlator_cmod_maliforge_gx_GXNativeBridge_nativeShutdownHostVulkanDevice(
        JNIEnv *, jclass) {
    shutdownHostDevice();
}
