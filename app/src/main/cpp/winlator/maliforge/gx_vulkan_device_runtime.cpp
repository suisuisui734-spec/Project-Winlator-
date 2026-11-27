#include <jni.h>
#include <vulkan/vulkan.h>

#include <mutex>
#include <cstdint>
#include <cstring>
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



std::string commandTransferResult(const char *status, const char *stage, VkResult result,
                                  bool submitted, bool fenceSignaled, bool dataVerified,
                                  const char *detail) {
    std::ostringstream json;
    json << "{\"status\":\"" << status << "\",\"stage\":\"" << stage
         << "\",\"vkResult\":" << static_cast<int>(result)
         << ",\"bytesCopied\":" << (dataVerified ? 64 : 0)
         << ",\"commandBufferSubmitted\":" << (submitted ? "true" : "false")
         << ",\"fenceSignaled\":" << (fenceSignaled ? "true" : "false")
         << ",\"dataVerified\":" << (dataVerified ? "true" : "false")
         << ",\"detail\":\"" << jsonEscape(detail) << "\"}";
    return json.str();
}

std::string runCommandTransferTest() {
    // Ensure the existing isolated host VkDevice bootstrap is initialized first.
    // This diagnostic never changes container driver selection or the Wine renderer.
    initializeHostDevice();

    std::lock_guard<std::mutex> lock(g_runtimeMutex);
    if (g_runtime.device == VK_NULL_HANDLE || g_runtime.graphicsQueue == VK_NULL_HANDLE
            || g_runtime.physicalDevice == VK_NULL_HANDLE) {
        return commandTransferResult("unavailable", "host_device_unavailable",
                VK_ERROR_INITIALIZATION_FAILED, false, false, false,
                "Android host Vulkan device or graphics queue is unavailable.");
    }

    const VkDevice device = g_runtime.device;
    constexpr VkDeviceSize kTransferBytes = 64;
    VkBuffer sourceBuffer = VK_NULL_HANDLE;
    VkBuffer destinationBuffer = VK_NULL_HANDLE;
    VkDeviceMemory sourceMemory = VK_NULL_HANDLE;
    VkDeviceMemory destinationMemory = VK_NULL_HANDLE;
    VkCommandPool commandPool = VK_NULL_HANDLE;
    VkCommandBuffer commandBuffer = VK_NULL_HANDLE;
    VkFence fence = VK_NULL_HANDLE;
    void *sourceMapping = nullptr;
    void *destinationMapping = nullptr;
    bool commandSubmitted = false;
    bool fenceSignaled = false;
    bool dataVerified = false;

    const auto finish = [&](const char *status, const char *stage, VkResult result,
                            const char *detail) -> std::string {
        if (commandSubmitted) (void) vkDeviceWaitIdle(device);
        if (sourceMapping != nullptr && sourceMemory != VK_NULL_HANDLE) {
            vkUnmapMemory(device, sourceMemory);
        }
        if (destinationMapping != nullptr && destinationMemory != VK_NULL_HANDLE) {
            vkUnmapMemory(device, destinationMemory);
        }
        if (fence != VK_NULL_HANDLE) vkDestroyFence(device, fence, nullptr);
        if (commandPool != VK_NULL_HANDLE) vkDestroyCommandPool(device, commandPool, nullptr);
        if (sourceBuffer != VK_NULL_HANDLE) vkDestroyBuffer(device, sourceBuffer, nullptr);
        if (destinationBuffer != VK_NULL_HANDLE) vkDestroyBuffer(device, destinationBuffer, nullptr);
        if (sourceMemory != VK_NULL_HANDLE) vkFreeMemory(device, sourceMemory, nullptr);
        if (destinationMemory != VK_NULL_HANDLE) vkFreeMemory(device, destinationMemory, nullptr);
        return commandTransferResult(status, stage, result, commandSubmitted,
                fenceSignaled, dataVerified, detail);
    };

    VkPhysicalDeviceMemoryProperties memoryProperties{};
    vkGetPhysicalDeviceMemoryProperties(g_runtime.physicalDevice, &memoryProperties);
    const auto findHostVisibleMemoryType = [&](uint32_t allowedBits, uint32_t *memoryTypeIndex,
                                                bool *hostCoherent) -> bool {
        uint32_t nonCoherentFallback = UINT32_MAX;
        for (uint32_t i = 0; i < memoryProperties.memoryTypeCount; ++i) {
            if ((allowedBits & (1U << i)) == 0) continue;
            const VkMemoryPropertyFlags flags = memoryProperties.memoryTypes[i].propertyFlags;
            if ((flags & VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT) == 0) continue;
            if ((flags & VK_MEMORY_PROPERTY_HOST_COHERENT_BIT) != 0) {
                *memoryTypeIndex = i;
                *hostCoherent = true;
                return true;
            }
            if (nonCoherentFallback == UINT32_MAX) nonCoherentFallback = i;
        }
        if (nonCoherentFallback != UINT32_MAX) {
            *memoryTypeIndex = nonCoherentFallback;
            *hostCoherent = false;
            return true;
        }
        return false;
    };

    VkBufferCreateInfo sourceBufferInfo{};
    sourceBufferInfo.sType = VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO;
    sourceBufferInfo.size = kTransferBytes;
    sourceBufferInfo.usage = VK_BUFFER_USAGE_TRANSFER_SRC_BIT;
    sourceBufferInfo.sharingMode = VK_SHARING_MODE_EXCLUSIVE;
    VkResult result = vkCreateBuffer(device, &sourceBufferInfo, nullptr, &sourceBuffer);
    if (result != VK_SUCCESS) {
        return finish("failed", "create_source_buffer", result,
                "Vulkan could not create the transfer-source buffer.");
    }

    VkBufferCreateInfo destinationBufferInfo{};
    destinationBufferInfo.sType = VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO;
    destinationBufferInfo.size = kTransferBytes;
    destinationBufferInfo.usage = VK_BUFFER_USAGE_TRANSFER_DST_BIT;
    destinationBufferInfo.sharingMode = VK_SHARING_MODE_EXCLUSIVE;
    result = vkCreateBuffer(device, &destinationBufferInfo, nullptr, &destinationBuffer);
    if (result != VK_SUCCESS) {
        return finish("failed", "create_destination_buffer", result,
                "Vulkan could not create the transfer-destination buffer.");
    }

    VkMemoryRequirements sourceRequirements{};
    VkMemoryRequirements destinationRequirements{};
    vkGetBufferMemoryRequirements(device, sourceBuffer, &sourceRequirements);
    vkGetBufferMemoryRequirements(device, destinationBuffer, &destinationRequirements);

    uint32_t sourceMemoryType = UINT32_MAX;
    uint32_t destinationMemoryType = UINT32_MAX;
    bool sourceHostCoherent = false;
    bool destinationHostCoherent = false;
    if (!findHostVisibleMemoryType(sourceRequirements.memoryTypeBits,
                                   &sourceMemoryType, &sourceHostCoherent)
            || !findHostVisibleMemoryType(destinationRequirements.memoryTypeBits,
                                          &destinationMemoryType, &destinationHostCoherent)) {
        return finish("unavailable", "host_visible_memory_unavailable",
                VK_ERROR_FEATURE_NOT_PRESENT,
                "The selected Vulkan device exposes no compatible host-visible buffer memory.");
    }

    VkMemoryAllocateInfo sourceAllocationInfo{};
    sourceAllocationInfo.sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
    sourceAllocationInfo.allocationSize = sourceRequirements.size;
    sourceAllocationInfo.memoryTypeIndex = sourceMemoryType;
    result = vkAllocateMemory(device, &sourceAllocationInfo, nullptr, &sourceMemory);
    if (result != VK_SUCCESS) {
        return finish("failed", "allocate_source_memory", result,
                "Vulkan could not allocate memory for the transfer-source buffer.");
    }
    result = vkBindBufferMemory(device, sourceBuffer, sourceMemory, 0);
    if (result != VK_SUCCESS) {
        return finish("failed", "bind_source_memory", result,
                "Vulkan could not bind memory to the transfer-source buffer.");
    }

    VkMemoryAllocateInfo destinationAllocationInfo{};
    destinationAllocationInfo.sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
    destinationAllocationInfo.allocationSize = destinationRequirements.size;
    destinationAllocationInfo.memoryTypeIndex = destinationMemoryType;
    result = vkAllocateMemory(device, &destinationAllocationInfo, nullptr, &destinationMemory);
    if (result != VK_SUCCESS) {
        return finish("failed", "allocate_destination_memory", result,
                "Vulkan could not allocate memory for the transfer-destination buffer.");
    }
    result = vkBindBufferMemory(device, destinationBuffer, destinationMemory, 0);
    if (result != VK_SUCCESS) {
        return finish("failed", "bind_destination_memory", result,
                "Vulkan could not bind memory to the transfer-destination buffer.");
    }

    result = vkMapMemory(device, sourceMemory, 0, VK_WHOLE_SIZE, 0, &sourceMapping);
    if (result != VK_SUCCESS || sourceMapping == nullptr) {
        return finish("failed", "map_source_memory",
                result == VK_SUCCESS ? VK_ERROR_MEMORY_MAP_FAILED : result,
                "Vulkan could not map the transfer-source memory.");
    }
    uint8_t *sourceBytes = static_cast<uint8_t *>(sourceMapping);
    for (uint32_t i = 0; i < static_cast<uint32_t>(kTransferBytes); ++i) {
        sourceBytes[i] = static_cast<uint8_t>((i * 37U + 11U) & 0xffU);
    }

    if (!sourceHostCoherent) {
        VkMappedMemoryRange sourceRange{};
        sourceRange.sType = VK_STRUCTURE_TYPE_MAPPED_MEMORY_RANGE;
        sourceRange.memory = sourceMemory;
        sourceRange.offset = 0;
        sourceRange.size = VK_WHOLE_SIZE;
        result = vkFlushMappedMemoryRanges(device, 1, &sourceRange);
        if (result != VK_SUCCESS) {
            return finish("failed", "flush_source_memory", result,
                    "Vulkan could not flush non-coherent source-buffer memory.");
        }
    }

    VkCommandPoolCreateInfo poolInfo{};
    poolInfo.sType = VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO;
    poolInfo.flags = VK_COMMAND_POOL_CREATE_TRANSIENT_BIT;
    poolInfo.queueFamilyIndex = g_runtime.graphicsQueueFamily;
    result = vkCreateCommandPool(device, &poolInfo, nullptr, &commandPool);
    if (result != VK_SUCCESS) {
        return finish("failed", "create_command_pool", result,
                "Vulkan could not create a command pool for the selected graphics queue.");
    }

    VkCommandBufferAllocateInfo commandAllocationInfo{};
    commandAllocationInfo.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO;
    commandAllocationInfo.commandPool = commandPool;
    commandAllocationInfo.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY;
    commandAllocationInfo.commandBufferCount = 1;
    result = vkAllocateCommandBuffers(device, &commandAllocationInfo, &commandBuffer);
    if (result != VK_SUCCESS || commandBuffer == VK_NULL_HANDLE) {
        return finish("failed", "allocate_command_buffer",
                result == VK_SUCCESS ? VK_ERROR_INITIALIZATION_FAILED : result,
                "Vulkan could not allocate a primary command buffer.");
    }

    VkCommandBufferBeginInfo beginInfo{};
    beginInfo.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO;
    beginInfo.flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;
    result = vkBeginCommandBuffer(commandBuffer, &beginInfo);
    if (result != VK_SUCCESS) {
        return finish("failed", "begin_command_buffer", result,
                "Vulkan could not begin recording the transfer command buffer.");
    }

    VkBufferCopy copyRegion{};
    copyRegion.size = kTransferBytes;
    vkCmdCopyBuffer(commandBuffer, sourceBuffer, destinationBuffer, 1, &copyRegion);
    result = vkEndCommandBuffer(commandBuffer);
    if (result != VK_SUCCESS) {
        return finish("failed", "end_command_buffer", result,
                "Vulkan could not finish recording the buffer-copy command.");
    }

    VkFenceCreateInfo fenceInfo{};
    fenceInfo.sType = VK_STRUCTURE_TYPE_FENCE_CREATE_INFO;
    result = vkCreateFence(device, &fenceInfo, nullptr, &fence);
    if (result != VK_SUCCESS) {
        return finish("failed", "create_fence", result,
                "Vulkan could not create a fence for command completion.");
    }

    VkSubmitInfo submitInfo{};
    submitInfo.sType = VK_STRUCTURE_TYPE_SUBMIT_INFO;
    submitInfo.commandBufferCount = 1;
    submitInfo.pCommandBuffers = &commandBuffer;
    result = vkQueueSubmit(g_runtime.graphicsQueue, 1, &submitInfo, fence);
    if (result != VK_SUCCESS) {
        return finish("failed", "submit_command_buffer", result,
                "Vulkan queue submission of the transfer command buffer failed.");
    }
    commandSubmitted = true;

    result = vkWaitForFences(device, 1, &fence, VK_TRUE, 10'000'000'000ULL);
    if (result != VK_SUCCESS) {
        return finish("failed", "wait_for_fence", result,
                "The Vulkan transfer command did not signal its completion fence within 10 seconds.");
    }
    fenceSignaled = true;

    result = vkMapMemory(device, destinationMemory, 0, VK_WHOLE_SIZE, 0,
                         &destinationMapping);
    if (result != VK_SUCCESS || destinationMapping == nullptr) {
        return finish("failed", "map_destination_memory",
                result == VK_SUCCESS ? VK_ERROR_MEMORY_MAP_FAILED : result,
                "Vulkan could not map the transfer-destination memory after command completion.");
    }

    if (!destinationHostCoherent) {
        VkMappedMemoryRange destinationRange{};
        destinationRange.sType = VK_STRUCTURE_TYPE_MAPPED_MEMORY_RANGE;
        destinationRange.memory = destinationMemory;
        destinationRange.offset = 0;
        destinationRange.size = VK_WHOLE_SIZE;
        result = vkInvalidateMappedMemoryRanges(device, 1, &destinationRange);
        if (result != VK_SUCCESS) {
            return finish("failed", "invalidate_destination_memory", result,
                    "Vulkan could not invalidate non-coherent destination-buffer memory.");
        }
    }

    dataVerified = std::memcmp(sourceMapping, destinationMapping,
                               static_cast<size_t>(kTransferBytes)) == 0;
    if (!dataVerified) {
        return finish("failed", "verify_buffer_contents", VK_ERROR_UNKNOWN,
                "The destination bytes do not match the deterministic source pattern.");
    }

    return finish("passed", "data_verified", VK_SUCCESS,
            "A Vulkan command buffer copied 64 bytes on the Android host graphics queue; "
            "the completion fence signaled and destination bytes matched the source pattern.");
}




std::string imageClearResult(const char *status, const char *stage, VkResult result,
                             bool submitted, bool fenceSignaled, uint32_t pixelsVerified,
                             bool dataVerified, const char *detail) {
    std::ostringstream json;
    json << "{\"status\":\"" << status << "\",\"stage\":\"" << stage
         << "\",\"vkResult\":" << static_cast<int>(result)
         << ",\"width\":4,\"height\":4,\"pixelsVerified\":" << pixelsVerified
         << ",\"commandBufferSubmitted\":" << (submitted ? "true" : "false")
         << ",\"fenceSignaled\":" << (fenceSignaled ? "true" : "false")
         << ",\"dataVerified\":" << (dataVerified ? "true" : "false")
         << ",\"format\":\"VK_FORMAT_R8G8B8A8_UNORM\""
         << ",\"detail\":\"" << jsonEscape(detail) << "\"}";
    return json.str();
}

std::string runImageClearTest() {
    // Reuse only the isolated Android-host device; no guest or container state is changed.
    initializeHostDevice();

    std::lock_guard<std::mutex> lock(g_runtimeMutex);
    if (g_runtime.device == VK_NULL_HANDLE || g_runtime.graphicsQueue == VK_NULL_HANDLE
            || g_runtime.physicalDevice == VK_NULL_HANDLE) {
        return imageClearResult("unavailable", "host_device_unavailable",
                VK_ERROR_INITIALIZATION_FAILED, false, false, 0, false,
                "Android host Vulkan device or graphics queue is unavailable.");
    }

    const VkDevice device = g_runtime.device;
    constexpr uint32_t kWidth = 4;
    constexpr uint32_t kHeight = 4;
    constexpr VkDeviceSize kReadbackBytes = kWidth * kHeight * 4;

    VkImage image = VK_NULL_HANDLE;
    VkDeviceMemory imageMemory = VK_NULL_HANDLE;
    VkBuffer readbackBuffer = VK_NULL_HANDLE;
    VkDeviceMemory readbackMemory = VK_NULL_HANDLE;
    VkCommandPool commandPool = VK_NULL_HANDLE;
    VkCommandBuffer commandBuffer = VK_NULL_HANDLE;
    VkFence fence = VK_NULL_HANDLE;
    void *readbackMapping = nullptr;
    bool readbackHostCoherent = false;
    bool commandSubmitted = false;
    bool fenceSignaled = false;
    uint32_t pixelsVerified = 0;
    bool dataVerified = false;

    const auto finish = [&](const char *status, const char *stage, VkResult result,
                            const char *detail) -> std::string {
        if (commandSubmitted) (void) vkDeviceWaitIdle(device);
        if (readbackMapping != nullptr && readbackMemory != VK_NULL_HANDLE) {
            vkUnmapMemory(device, readbackMemory);
        }
        if (fence != VK_NULL_HANDLE) vkDestroyFence(device, fence, nullptr);
        if (commandPool != VK_NULL_HANDLE) vkDestroyCommandPool(device, commandPool, nullptr);
        if (readbackBuffer != VK_NULL_HANDLE) vkDestroyBuffer(device, readbackBuffer, nullptr);
        if (image != VK_NULL_HANDLE) vkDestroyImage(device, image, nullptr);
        if (readbackMemory != VK_NULL_HANDLE) vkFreeMemory(device, readbackMemory, nullptr);
        if (imageMemory != VK_NULL_HANDLE) vkFreeMemory(device, imageMemory, nullptr);
        return imageClearResult(status, stage, result, commandSubmitted, fenceSignaled,
                pixelsVerified, dataVerified, detail);
    };

    const VkImageUsageFlags imageUsage =
            VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT;
    VkImageFormatProperties imageFormatProperties{};
    VkResult result = vkGetPhysicalDeviceImageFormatProperties(g_runtime.physicalDevice,
            VK_FORMAT_R8G8B8A8_UNORM, VK_IMAGE_TYPE_2D, VK_IMAGE_TILING_OPTIMAL,
            imageUsage, 0, &imageFormatProperties);
    if (result != VK_SUCCESS) {
        return finish(result == VK_ERROR_FORMAT_NOT_SUPPORTED ? "unavailable" : "failed",
                "image_format_query", result,
                "The selected Vulkan device does not report support for the requested optimal-tiled RGBA8 transfer image.");
    }
    if (imageFormatProperties.maxExtent.width < kWidth
            || imageFormatProperties.maxExtent.height < kHeight
            || imageFormatProperties.maxMipLevels < 1
            || imageFormatProperties.maxArrayLayers < 1) {
        return finish("unavailable", "image_extent_unsupported", VK_ERROR_FORMAT_NOT_SUPPORTED,
                "The selected Vulkan device reports limits smaller than the 4x4 test image.");
    }

    VkImageCreateInfo imageInfo{};
    imageInfo.sType = VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO;
    imageInfo.imageType = VK_IMAGE_TYPE_2D;
    imageInfo.format = VK_FORMAT_R8G8B8A8_UNORM;
    imageInfo.extent = {kWidth, kHeight, 1};
    imageInfo.mipLevels = 1;
    imageInfo.arrayLayers = 1;
    imageInfo.samples = VK_SAMPLE_COUNT_1_BIT;
    imageInfo.tiling = VK_IMAGE_TILING_OPTIMAL;
    imageInfo.usage = imageUsage;
    imageInfo.sharingMode = VK_SHARING_MODE_EXCLUSIVE;
    imageInfo.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
    result = vkCreateImage(device, &imageInfo, nullptr, &image);
    if (result != VK_SUCCESS) {
        return finish("failed", "create_image", result,
                "Vulkan could not create the 4x4 optimal-tiled RGBA8 image.");
    }

    VkPhysicalDeviceMemoryProperties memoryProperties{};
    vkGetPhysicalDeviceMemoryProperties(g_runtime.physicalDevice, &memoryProperties);
    const auto findMemoryType = [&](uint32_t allowedBits, VkMemoryPropertyFlags required,
                                    VkMemoryPropertyFlags preferred, uint32_t *memoryTypeIndex,
                                    bool *hostCoherent) -> bool {
        uint32_t fallback = UINT32_MAX;
        for (uint32_t i = 0; i < memoryProperties.memoryTypeCount; ++i) {
            if ((allowedBits & (1U << i)) == 0) continue;
            const VkMemoryPropertyFlags flags = memoryProperties.memoryTypes[i].propertyFlags;
            if ((flags & required) != required) continue;
            if ((flags & preferred) == preferred) {
                *memoryTypeIndex = i;
                if (hostCoherent != nullptr) {
                    *hostCoherent = (flags & VK_MEMORY_PROPERTY_HOST_COHERENT_BIT) != 0;
                }
                return true;
            }
            if (fallback == UINT32_MAX) fallback = i;
        }
        if (fallback == UINT32_MAX) return false;
        *memoryTypeIndex = fallback;
        if (hostCoherent != nullptr) {
            *hostCoherent = (memoryProperties.memoryTypes[fallback].propertyFlags
                    & VK_MEMORY_PROPERTY_HOST_COHERENT_BIT) != 0;
        }
        return true;
    };

    VkMemoryRequirements imageRequirements{};
    vkGetImageMemoryRequirements(device, image, &imageRequirements);
    uint32_t imageMemoryType = UINT32_MAX;
    if (!findMemoryType(imageRequirements.memoryTypeBits, 0,
                        VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, &imageMemoryType, nullptr)) {
        return finish("unavailable", "image_memory_type_unavailable",
                VK_ERROR_FEATURE_NOT_PRESENT,
                "No memory type is compatible with the test image allocation.");
    }

    VkMemoryAllocateInfo imageAllocationInfo{};
    imageAllocationInfo.sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
    imageAllocationInfo.allocationSize = imageRequirements.size;
    imageAllocationInfo.memoryTypeIndex = imageMemoryType;
    result = vkAllocateMemory(device, &imageAllocationInfo, nullptr, &imageMemory);
    if (result != VK_SUCCESS) {
        return finish("failed", "allocate_image_memory", result,
                "Vulkan could not allocate memory for the test image.");
    }
    result = vkBindImageMemory(device, image, imageMemory, 0);
    if (result != VK_SUCCESS) {
        return finish("failed", "bind_image_memory", result,
                "Vulkan could not bind memory to the test image.");
    }

    VkBufferCreateInfo readbackBufferInfo{};
    readbackBufferInfo.sType = VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO;
    readbackBufferInfo.size = kReadbackBytes;
    readbackBufferInfo.usage = VK_BUFFER_USAGE_TRANSFER_DST_BIT;
    readbackBufferInfo.sharingMode = VK_SHARING_MODE_EXCLUSIVE;
    result = vkCreateBuffer(device, &readbackBufferInfo, nullptr, &readbackBuffer);
    if (result != VK_SUCCESS) {
        return finish("failed", "create_readback_buffer", result,
                "Vulkan could not create the host read-back buffer.");
    }

    VkMemoryRequirements readbackRequirements{};
    vkGetBufferMemoryRequirements(device, readbackBuffer, &readbackRequirements);
    uint32_t readbackMemoryType = UINT32_MAX;
    if (!findMemoryType(readbackRequirements.memoryTypeBits,
                        VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT,
                        VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
                        &readbackMemoryType, &readbackHostCoherent)) {
        return finish("unavailable", "host_visible_readback_memory_unavailable",
                VK_ERROR_FEATURE_NOT_PRESENT,
                "No host-visible memory type is compatible with the image read-back buffer.");
    }

    VkMemoryAllocateInfo readbackAllocationInfo{};
    readbackAllocationInfo.sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
    readbackAllocationInfo.allocationSize = readbackRequirements.size;
    readbackAllocationInfo.memoryTypeIndex = readbackMemoryType;
    result = vkAllocateMemory(device, &readbackAllocationInfo, nullptr, &readbackMemory);
    if (result != VK_SUCCESS) {
        return finish("failed", "allocate_readback_memory", result,
                "Vulkan could not allocate host-visible read-back memory.");
    }
    result = vkBindBufferMemory(device, readbackBuffer, readbackMemory, 0);
    if (result != VK_SUCCESS) {
        return finish("failed", "bind_readback_memory", result,
                "Vulkan could not bind memory to the read-back buffer.");
    }

    VkCommandPoolCreateInfo poolInfo{};
    poolInfo.sType = VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO;
    poolInfo.flags = VK_COMMAND_POOL_CREATE_TRANSIENT_BIT;
    poolInfo.queueFamilyIndex = g_runtime.graphicsQueueFamily;
    result = vkCreateCommandPool(device, &poolInfo, nullptr, &commandPool);
    if (result != VK_SUCCESS) {
        return finish("failed", "create_command_pool", result,
                "Vulkan could not create a command pool for the graphics queue.");
    }

    VkCommandBufferAllocateInfo commandAllocationInfo{};
    commandAllocationInfo.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO;
    commandAllocationInfo.commandPool = commandPool;
    commandAllocationInfo.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY;
    commandAllocationInfo.commandBufferCount = 1;
    result = vkAllocateCommandBuffers(device, &commandAllocationInfo, &commandBuffer);
    if (result != VK_SUCCESS || commandBuffer == VK_NULL_HANDLE) {
        return finish("failed", "allocate_command_buffer",
                result == VK_SUCCESS ? VK_ERROR_INITIALIZATION_FAILED : result,
                "Vulkan could not allocate a primary image-test command buffer.");
    }

    VkCommandBufferBeginInfo beginInfo{};
    beginInfo.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO;
    beginInfo.flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;
    result = vkBeginCommandBuffer(commandBuffer, &beginInfo);
    if (result != VK_SUCCESS) {
        return finish("failed", "begin_command_buffer", result,
                "Vulkan could not begin recording the image-test command buffer.");
    }

    VkImageSubresourceRange colorRange{};
    colorRange.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
    colorRange.baseMipLevel = 0;
    colorRange.levelCount = 1;
    colorRange.baseArrayLayer = 0;
    colorRange.layerCount = 1;

    VkImageMemoryBarrier toTransferDestination{};
    toTransferDestination.sType = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER;
    toTransferDestination.srcAccessMask = 0;
    toTransferDestination.dstAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT;
    toTransferDestination.oldLayout = VK_IMAGE_LAYOUT_UNDEFINED;
    toTransferDestination.newLayout = VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
    toTransferDestination.srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
    toTransferDestination.dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
    toTransferDestination.image = image;
    toTransferDestination.subresourceRange = colorRange;
    vkCmdPipelineBarrier(commandBuffer,
            VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT, 0,
            0, nullptr, 0, nullptr, 1, &toTransferDestination);

    VkClearColorValue clearColor{};
    clearColor.float32[0] = 1.0f;
    clearColor.float32[1] = 0.0f;
    clearColor.float32[2] = 0.0f;
    clearColor.float32[3] = 1.0f;
    vkCmdClearColorImage(commandBuffer, image, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
            &clearColor, 1, &colorRange);

    VkImageMemoryBarrier toTransferSource{};
    toTransferSource.sType = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER;
    toTransferSource.srcAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT;
    toTransferSource.dstAccessMask = VK_ACCESS_TRANSFER_READ_BIT;
    toTransferSource.oldLayout = VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
    toTransferSource.newLayout = VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL;
    toTransferSource.srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
    toTransferSource.dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
    toTransferSource.image = image;
    toTransferSource.subresourceRange = colorRange;
    vkCmdPipelineBarrier(commandBuffer,
            VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT, 0,
            0, nullptr, 0, nullptr, 1, &toTransferSource);

    VkBufferImageCopy imageCopy{};
    imageCopy.bufferOffset = 0;
    imageCopy.bufferRowLength = 0;
    imageCopy.bufferImageHeight = 0;
    imageCopy.imageSubresource.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
    imageCopy.imageSubresource.mipLevel = 0;
    imageCopy.imageSubresource.baseArrayLayer = 0;
    imageCopy.imageSubresource.layerCount = 1;
    imageCopy.imageOffset = {0, 0, 0};
    imageCopy.imageExtent = {kWidth, kHeight, 1};
    vkCmdCopyImageToBuffer(commandBuffer, image, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
            readbackBuffer, 1, &imageCopy);

    VkBufferMemoryBarrier readbackToHost{};
    readbackToHost.sType = VK_STRUCTURE_TYPE_BUFFER_MEMORY_BARRIER;
    readbackToHost.srcAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT;
    readbackToHost.dstAccessMask = VK_ACCESS_HOST_READ_BIT;
    readbackToHost.srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
    readbackToHost.dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
    readbackToHost.buffer = readbackBuffer;
    readbackToHost.offset = 0;
    readbackToHost.size = kReadbackBytes;
    vkCmdPipelineBarrier(commandBuffer,
            VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_HOST_BIT, 0,
            0, nullptr, 1, &readbackToHost, 0, nullptr);

    result = vkEndCommandBuffer(commandBuffer);
    if (result != VK_SUCCESS) {
        return finish("failed", "end_command_buffer", result,
                "Vulkan could not finish recording the image clear/read-back commands.");
    }

    VkFenceCreateInfo fenceInfo{};
    fenceInfo.sType = VK_STRUCTURE_TYPE_FENCE_CREATE_INFO;
    result = vkCreateFence(device, &fenceInfo, nullptr, &fence);
    if (result != VK_SUCCESS) {
        return finish("failed", "create_fence", result,
                "Vulkan could not create a completion fence for the image test.");
    }

    VkSubmitInfo submitInfo{};
    submitInfo.sType = VK_STRUCTURE_TYPE_SUBMIT_INFO;
    submitInfo.commandBufferCount = 1;
    submitInfo.pCommandBuffers = &commandBuffer;
    result = vkQueueSubmit(g_runtime.graphicsQueue, 1, &submitInfo, fence);
    if (result != VK_SUCCESS) {
        return finish("failed", "submit_command_buffer", result,
                "Vulkan queue submission of the image clear/read-back commands failed.");
    }
    commandSubmitted = true;

    result = vkWaitForFences(device, 1, &fence, VK_TRUE, 10'000'000'000ULL);
    if (result != VK_SUCCESS) {
        return finish("failed", "wait_for_fence", result,
                "The image clear/read-back command did not signal its fence within 10 seconds.");
    }
    fenceSignaled = true;

    result = vkMapMemory(device, readbackMemory, 0, VK_WHOLE_SIZE, 0, &readbackMapping);
    if (result != VK_SUCCESS || readbackMapping == nullptr) {
        return finish("failed", "map_readback_memory",
                result == VK_SUCCESS ? VK_ERROR_MEMORY_MAP_FAILED : result,
                "Vulkan could not map image read-back memory.");
    }

    if (!readbackHostCoherent) {
        VkMappedMemoryRange range{};
        range.sType = VK_STRUCTURE_TYPE_MAPPED_MEMORY_RANGE;
        range.memory = readbackMemory;
        range.offset = 0;
        range.size = VK_WHOLE_SIZE;
        result = vkInvalidateMappedMemoryRanges(device, 1, &range);
        if (result != VK_SUCCESS) {
            return finish("failed", "invalidate_readback_memory", result,
                    "Vulkan could not invalidate non-coherent image read-back memory.");
        }
    }

    const uint8_t expectedPixel[4] = {255, 0, 0, 255};
    const uint8_t *actualPixels = static_cast<const uint8_t *>(readbackMapping);
    for (uint32_t pixel = 0; pixel < kWidth * kHeight; ++pixel) {
        if (std::memcmp(actualPixels + (pixel * 4), expectedPixel, sizeof(expectedPixel)) == 0) {
            ++pixelsVerified;
        }
    }
    dataVerified = pixelsVerified == (kWidth * kHeight);
    if (!dataVerified) {
        return finish("failed", "verify_image_readback", VK_ERROR_UNKNOWN,
                "The copied RGBA8 pixels do not all match the expected opaque-red clear value.");
    }

    return finish("passed", "image_data_verified", VK_SUCCESS,
            "A Vulkan optimal-tiled RGBA8 image was allocated, transitioned, cleared, copied to a host buffer, and all 16 pixels matched the expected value.");
}

}  // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_winlator_cmod_maliforge_gx_GXNativeBridge_nativeRunVulkanImageClearTest(
        JNIEnv *env, jclass) {
    const std::string result = runImageClearTest();
    return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_winlator_cmod_maliforge_gx_GXNativeBridge_nativeRunVulkanCommandTransferTest(
        JNIEnv *env, jclass) {
    const std::string result = runCommandTransferTest();
    return env->NewStringUTF(result.c_str());
}

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
