#define _GNU_SOURCE
#include <vulkan/vulkan.h>
#include <vulkan/vk_layer.h>

#include <errno.h>
#include <fcntl.h>
#include <pthread.h>
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <unistd.h>

#define GX_LAYER_NAME "VK_LAYER_MALIFORGE_gx_diagnostics"
#define GX_LAYER_EXPORT __attribute__((visibility("default")))

typedef struct GxInstanceRecord {
    VkInstance instance;
    PFN_vkGetInstanceProcAddr next_gipa;
    PFN_vkGetDeviceProcAddr next_gdpa;
    PFN_vkSetInstanceLoaderData set_loader_data;
    uint32_t api_version;
    VkBool32 device_group_extension_enabled;
    struct GxInstanceRecord *next;
} GxInstanceRecord;

typedef struct GxPhysicalDeviceRecord {
    VkPhysicalDevice physical_device;
    VkInstance instance;
    struct GxPhysicalDeviceRecord *next;
} GxPhysicalDeviceRecord;

typedef struct GxDeviceRecord {
    VkDevice device;
    PFN_vkGetDeviceProcAddr next_gdpa;
    struct GxDeviceRecord *next;
} GxDeviceRecord;

static pthread_mutex_t gx_lock = PTHREAD_MUTEX_INITIALIZER;
static GxInstanceRecord *gx_instances;
static GxPhysicalDeviceRecord *gx_physical_devices;
static GxDeviceRecord *gx_devices;

static GxInstanceRecord *find_instance(VkInstance instance) {
    GxInstanceRecord *record;
    for (record = gx_instances; record != NULL; record = record->next) {
        if (record->instance == instance) return record;
    }
    return NULL;
}

static GxInstanceRecord *find_physical_device_instance(VkPhysicalDevice physical_device) {
    GxPhysicalDeviceRecord *record;
    for (record = gx_physical_devices; record != NULL; record = record->next) {
        if (record->physical_device == physical_device) return find_instance(record->instance);
    }
    return NULL;
}

static GxDeviceRecord *find_device(VkDevice device) {
    GxDeviceRecord *record;
    for (record = gx_devices; record != NULL; record = record->next) {
        if (record->device == device) return record;
    }
    return NULL;
}

static void log_event(const char *event, const char *format, ...) {
    const char *path = getenv("MALIFORGE_GX_LAYER_LOG");
    if (path == NULL || path[0] == '\0') return;

    int fd = open(path, O_WRONLY | O_CREAT | O_APPEND | O_CLOEXEC, 0600);
    if (fd < 0) return;

    struct timespec now;
    clock_gettime(CLOCK_REALTIME, &now);
    dprintf(fd, "time=%lld.%09ld pid=%ld layer=%s event=%s",
            (long long) now.tv_sec, now.tv_nsec, (long) getpid(),
            GX_LAYER_NAME, event);
    if (format != NULL && format[0] != '\0') {
        char detail[768];
        va_list args;
        va_start(args, format);
        vsnprintf(detail, sizeof(detail), format, args);
        va_end(args);
        dprintf(fd, " %s", detail);
    }
    dprintf(fd, "\n");
    close(fd);
}

static VkLayerInstanceCreateInfo *find_instance_link_info(const VkInstanceCreateInfo *create_info) {
    const VkBaseInStructure *node = (const VkBaseInStructure *) create_info->pNext;
    while (node != NULL) {
        if (node->sType == VK_STRUCTURE_TYPE_LOADER_INSTANCE_CREATE_INFO) {
            VkLayerInstanceCreateInfo *layer_info = (VkLayerInstanceCreateInfo *) node;
            if (layer_info->function == VK_LAYER_LINK_INFO) return layer_info;
        }
        node = node->pNext;
    }
    return NULL;
}

static PFN_vkSetInstanceLoaderData find_instance_loader_data_callback(
        const VkInstanceCreateInfo *create_info) {
    const VkBaseInStructure *node = (const VkBaseInStructure *) create_info->pNext;
    while (node != NULL) {
        if (node->sType == VK_STRUCTURE_TYPE_LOADER_INSTANCE_CREATE_INFO) {
            const VkLayerInstanceCreateInfo *layer_info = (const VkLayerInstanceCreateInfo *) node;
            if (layer_info->function == VK_LOADER_DATA_CALLBACK)
                return layer_info->u.pfnSetInstanceLoaderData;
        }
        node = node->pNext;
    }
    return NULL;
}

static VkLayerDeviceCreateInfo *find_device_link_info(const VkDeviceCreateInfo *create_info) {
    const VkBaseInStructure *node = (const VkBaseInStructure *) create_info->pNext;
    while (node != NULL) {
        if (node->sType == VK_STRUCTURE_TYPE_LOADER_DEVICE_CREATE_INFO) {
            VkLayerDeviceCreateInfo *layer_info = (VkLayerDeviceCreateInfo *) node;
            if (layer_info->function == VK_LAYER_LINK_INFO) return layer_info;
        }
        node = node->pNext;
    }
    return NULL;
}

static PFN_vkSetDeviceLoaderData find_device_loader_data_callback(
        const VkDeviceCreateInfo *create_info) {
    const VkBaseInStructure *node = (const VkBaseInStructure *) create_info->pNext;
    while (node != NULL) {
        if (node->sType == VK_STRUCTURE_TYPE_LOADER_DEVICE_CREATE_INFO) {
            const VkLayerDeviceCreateInfo *layer_info = (const VkLayerDeviceCreateInfo *) node;
            if (layer_info->function == VK_LOADER_DATA_CALLBACK)
                return layer_info->u.pfnSetDeviceLoaderData;
        }
        node = node->pNext;
    }
    return NULL;
}

static void remember_physical_devices(VkInstance instance, uint32_t count,
                                      const VkPhysicalDevice *physical_devices) {
    uint32_t i;
    pthread_mutex_lock(&gx_lock);
    for (i = 0; i < count; ++i) {
        GxPhysicalDeviceRecord *existing = NULL;
        GxPhysicalDeviceRecord *cursor;
        for (cursor = gx_physical_devices; cursor != NULL; cursor = cursor->next) {
            if (cursor->physical_device == physical_devices[i]) {
                existing = cursor;
                break;
            }
        }
        if (existing != NULL) {
            existing->instance = instance;
            continue;
        }
        GxPhysicalDeviceRecord *created = (GxPhysicalDeviceRecord *) calloc(1, sizeof(*created));
        if (created == NULL) continue;
        created->physical_device = physical_devices[i];
        created->instance = instance;
        created->next = gx_physical_devices;
        gx_physical_devices = created;
    }
    pthread_mutex_unlock(&gx_lock);
}

static void forget_instance(VkInstance instance) {
    pthread_mutex_lock(&gx_lock);
    GxInstanceRecord **instance_cursor = &gx_instances;
    while (*instance_cursor != NULL) {
        if ((*instance_cursor)->instance == instance) {
            GxInstanceRecord *removed = *instance_cursor;
            *instance_cursor = removed->next;
            free(removed);
            break;
        }
        instance_cursor = &(*instance_cursor)->next;
    }

    GxPhysicalDeviceRecord **physical_cursor = &gx_physical_devices;
    while (*physical_cursor != NULL) {
        if ((*physical_cursor)->instance == instance) {
            GxPhysicalDeviceRecord *removed = *physical_cursor;
            *physical_cursor = removed->next;
            free(removed);
            continue;
        }
        physical_cursor = &(*physical_cursor)->next;
    }
    pthread_mutex_unlock(&gx_lock);
}

static void forget_device(VkDevice device) {
    pthread_mutex_lock(&gx_lock);
    GxDeviceRecord **cursor = &gx_devices;
    while (*cursor != NULL) {
        if ((*cursor)->device == device) {
            GxDeviceRecord *removed = *cursor;
            *cursor = removed->next;
            free(removed);
            break;
        }
        cursor = &(*cursor)->next;
    }
    pthread_mutex_unlock(&gx_lock);
}

static GxInstanceRecord *snapshot_instance(VkInstance instance) {
    GxInstanceRecord copy;
    GxInstanceRecord *found = NULL;
    pthread_mutex_lock(&gx_lock);
    GxInstanceRecord *record = find_instance(instance);
    if (record != NULL) {
        copy = *record;
        found = (GxInstanceRecord *) malloc(sizeof(*found));
        if (found != NULL) {
            *found = copy;
            found->next = NULL;
        }
    }
    pthread_mutex_unlock(&gx_lock);
    return found;
}

static GxDeviceRecord *snapshot_device(VkDevice device) {
    GxDeviceRecord copy;
    GxDeviceRecord *found = NULL;
    pthread_mutex_lock(&gx_lock);
    GxDeviceRecord *record = find_device(device);
    if (record != NULL) {
        copy = *record;
        found = (GxDeviceRecord *) malloc(sizeof(*found));
        if (found != NULL) {
            *found = copy;
            found->next = NULL;
        }
    }
    pthread_mutex_unlock(&gx_lock);
    return found;
}

static PFN_vkVoidFunction VKAPI_CALL gx_get_instance_proc_addr(VkInstance instance, const char *name);
static PFN_vkVoidFunction VKAPI_CALL gx_get_device_proc_addr(VkDevice device, const char *name);

static VkResult VKAPI_CALL gx_create_instance(const VkInstanceCreateInfo *create_info,
                                               const VkAllocationCallbacks *allocator,
                                               VkInstance *instance) {
    if (create_info == NULL || instance == NULL) return VK_ERROR_INITIALIZATION_FAILED;

    VkLayerInstanceCreateInfo *chain_info = find_instance_link_info(create_info);
    if (chain_info == NULL || chain_info->u.pLayerInfo == NULL) {
        log_event("CREATE_INSTANCE_CHAIN_MISSING", "");
        return VK_ERROR_INITIALIZATION_FAILED;
    }

    PFN_vkSetInstanceLoaderData set_loader_data =
            find_instance_loader_data_callback(create_info);
    VkLayerInstanceLink *link = chain_info->u.pLayerInfo;
    PFN_vkGetInstanceProcAddr next_gipa = link->pfnNextGetInstanceProcAddr;
    if (next_gipa == NULL) return VK_ERROR_INITIALIZATION_FAILED;

    PFN_vkCreateInstance next_create =
            (PFN_vkCreateInstance) next_gipa(VK_NULL_HANDLE, "vkCreateInstance");
    if (next_create == NULL) return VK_ERROR_INITIALIZATION_FAILED;

    GxInstanceRecord *record = (GxInstanceRecord *) calloc(1, sizeof(*record));
    if (record == NULL) return VK_ERROR_OUT_OF_HOST_MEMORY;

    chain_info->u.pLayerInfo = link->pNext;
    VkResult result = next_create(create_info, allocator, instance);
    if (result != VK_SUCCESS) {
        free(record);
        log_event("CREATE_INSTANCE_FAILED", "VkResult=%d", (int) result);
        return result;
    }

    if (set_loader_data != NULL
            && set_loader_data(*instance, *instance) != VK_SUCCESS) {
        PFN_vkDestroyInstance next_destroy =
                (PFN_vkDestroyInstance) next_gipa(*instance, "vkDestroyInstance");
        if (next_destroy != NULL) next_destroy(*instance, allocator);
        free(record);
        log_event("CREATE_INSTANCE_LOADER_DATA_FAILED", "");
        return VK_ERROR_INITIALIZATION_FAILED;
    }

    record->instance = *instance;
    record->next_gipa = next_gipa;
    record->next_gdpa = (PFN_vkGetDeviceProcAddr) next_gipa(*instance, "vkGetDeviceProcAddr");
    record->set_loader_data = set_loader_data;
    record->api_version = create_info->pApplicationInfo != NULL
            ? create_info->pApplicationInfo->apiVersion : VK_API_VERSION_1_0;
    record->device_group_extension_enabled = VK_FALSE;
    for (uint32_t extension_index = 0;
         extension_index < create_info->enabledExtensionCount; ++extension_index) {
        if (strcmp(create_info->ppEnabledExtensionNames[extension_index],
                   VK_KHR_DEVICE_GROUP_CREATION_EXTENSION_NAME) == 0) {
            record->device_group_extension_enabled = VK_TRUE;
            break;
        }
    }

    if (record->next_gdpa == NULL) {
        PFN_vkDestroyInstance next_destroy =
                (PFN_vkDestroyInstance) next_gipa(*instance, "vkDestroyInstance");
        if (next_destroy != NULL) next_destroy(*instance, allocator);
        free(record);
        log_event("CREATE_INSTANCE_NO_GDPA", "");
        return VK_ERROR_INITIALIZATION_FAILED;
    }

    pthread_mutex_lock(&gx_lock);
    record->next = gx_instances;
    gx_instances = record;
    pthread_mutex_unlock(&gx_lock);

    uint32_t api_version = VK_API_VERSION_1_0;
    const char *app_name = "unknown";
    if (create_info->pApplicationInfo != NULL) {
        api_version = create_info->pApplicationInfo->apiVersion;
        if (create_info->pApplicationInfo->pApplicationName != NULL)
            app_name = create_info->pApplicationInfo->pApplicationName;
    }
    log_event("INSTANCE_CREATED", "application=%s api=%u.%u.%u",
              app_name, VK_VERSION_MAJOR(api_version), VK_VERSION_MINOR(api_version),
              VK_VERSION_PATCH(api_version));
    return VK_SUCCESS;
}

static void VKAPI_CALL gx_destroy_instance(VkInstance instance,
                                            const VkAllocationCallbacks *allocator) {
    GxInstanceRecord *record = snapshot_instance(instance);
    if (record == NULL) return;
    PFN_vkDestroyInstance next_destroy =
            (PFN_vkDestroyInstance) record->next_gipa(instance, "vkDestroyInstance");
    log_event("INSTANCE_DESTROYED", "");
    if (next_destroy != NULL) next_destroy(instance, allocator);
    free(record);
    forget_instance(instance);
}

static VkResult VKAPI_CALL gx_enumerate_physical_devices(VkInstance instance,
                                                           uint32_t *count,
                                                           VkPhysicalDevice *devices) {
    GxInstanceRecord *record = snapshot_instance(instance);
    if (record == NULL) return VK_ERROR_INITIALIZATION_FAILED;
    PFN_vkEnumeratePhysicalDevices next_enumerate =
            (PFN_vkEnumeratePhysicalDevices) record->next_gipa(instance, "vkEnumeratePhysicalDevices");
    PFN_vkSetInstanceLoaderData set_loader_data = record->set_loader_data;
    free(record);
    if (next_enumerate == NULL) return VK_ERROR_INITIALIZATION_FAILED;

    VkResult result = next_enumerate(instance, count, devices);
    if (devices != NULL && count != NULL && (result == VK_SUCCESS || result == VK_INCOMPLETE)) {
        uint32_t i;
        for (i = 0; i < *count; ++i) {
            if (set_loader_data != NULL
                    && set_loader_data(instance, devices[i]) != VK_SUCCESS) {
                log_event("PHYSICAL_DEVICE_LOADER_DATA_FAILED", "index=%u", i);
                return VK_ERROR_INITIALIZATION_FAILED;
            }
        }
        remember_physical_devices(instance, *count, devices);
    }
    if (count != NULL && (result == VK_SUCCESS || result == VK_INCOMPLETE)) {
        log_event("PHYSICAL_DEVICES_ENUMERATED", "count=%u", *count);
    }
    return result;
}

static VkResult gx_enumerate_physical_device_groups_common(
        VkInstance instance, uint32_t *count, VkPhysicalDeviceGroupProperties *groups,
        const char *next_name) {
    GxInstanceRecord *record = snapshot_instance(instance);
    if (record == NULL) return VK_ERROR_INITIALIZATION_FAILED;
    PFN_vkEnumeratePhysicalDeviceGroups next_enumerate =
            (PFN_vkEnumeratePhysicalDeviceGroups) record->next_gipa(instance, next_name);
    PFN_vkSetInstanceLoaderData set_loader_data = record->set_loader_data;
    free(record);
    if (next_enumerate == NULL) return VK_ERROR_EXTENSION_NOT_PRESENT;

    VkResult result = next_enumerate(instance, count, groups);
    if (groups != NULL && count != NULL && (result == VK_SUCCESS || result == VK_INCOMPLETE)) {
        uint32_t i, j;
        for (i = 0; i < *count; ++i) {
            for (j = 0; j < groups[i].physicalDeviceCount; ++j) {
                if (set_loader_data != NULL
                        && set_loader_data(instance, groups[i].physicalDevices[j]) != VK_SUCCESS) {
                    log_event("PHYSICAL_DEVICE_GROUP_LOADER_DATA_FAILED",
                              "group=%u index=%u", i, j);
                    return VK_ERROR_INITIALIZATION_FAILED;
                }
            }
            remember_physical_devices(instance, groups[i].physicalDeviceCount, groups[i].physicalDevices);
        }
    }
    if (count != NULL && (result == VK_SUCCESS || result == VK_INCOMPLETE)) {
        log_event("PHYSICAL_DEVICE_GROUPS_ENUMERATED", "count=%u", *count);
    }
    return result;
}

static VkResult VKAPI_CALL gx_enumerate_physical_device_groups(
        VkInstance instance, uint32_t *count, VkPhysicalDeviceGroupProperties *groups) {
    return gx_enumerate_physical_device_groups_common(
            instance, count, groups, "vkEnumeratePhysicalDeviceGroups");
}

static VkResult VKAPI_CALL gx_enumerate_physical_device_groups_khr(
        VkInstance instance, uint32_t *count, VkPhysicalDeviceGroupProperties *groups) {
    return gx_enumerate_physical_device_groups_common(
            instance, count, groups, "vkEnumeratePhysicalDeviceGroupsKHR");
}

static VkResult VKAPI_CALL gx_create_device(VkPhysicalDevice physical_device,
                                             const VkDeviceCreateInfo *create_info,
                                             const VkAllocationCallbacks *allocator,
                                             VkDevice *device) {
    if (create_info == NULL || device == NULL) return VK_ERROR_INITIALIZATION_FAILED;

    pthread_mutex_lock(&gx_lock);
    GxInstanceRecord *instance_record = find_physical_device_instance(physical_device);
    VkInstance instance = instance_record == NULL ? VK_NULL_HANDLE : instance_record->instance;
    PFN_vkGetInstanceProcAddr known_gipa = instance_record == NULL ? NULL : instance_record->next_gipa;
    pthread_mutex_unlock(&gx_lock);

    VkLayerDeviceCreateInfo *chain_info = find_device_link_info(create_info);
    if (chain_info == NULL || chain_info->u.pLayerInfo == NULL) {
        log_event("CREATE_DEVICE_CHAIN_MISSING", "");
        return VK_ERROR_INITIALIZATION_FAILED;
    }
    PFN_vkSetDeviceLoaderData set_loader_data =
            find_device_loader_data_callback(create_info);
    VkLayerDeviceLink *link = chain_info->u.pLayerInfo;
    PFN_vkGetInstanceProcAddr next_gipa = link->pfnNextGetInstanceProcAddr;
    PFN_vkGetDeviceProcAddr next_gdpa = link->pfnNextGetDeviceProcAddr;
    if (instance == VK_NULL_HANDLE || next_gipa == NULL || next_gdpa == NULL) {
        log_event("CREATE_DEVICE_PARENT_UNKNOWN", "");
        return VK_ERROR_INITIALIZATION_FAILED;
    }

    PFN_vkCreateDevice next_create =
            (PFN_vkCreateDevice) next_gipa(instance, "vkCreateDevice");
    if (next_create == NULL) return VK_ERROR_INITIALIZATION_FAILED;

    GxDeviceRecord *record = (GxDeviceRecord *) calloc(1, sizeof(*record));
    if (record == NULL) return VK_ERROR_OUT_OF_HOST_MEMORY;

    chain_info->u.pLayerInfo = link->pNext;
    VkResult result = next_create(physical_device, create_info, allocator, device);
    if (result != VK_SUCCESS) {
        free(record);
        log_event("CREATE_DEVICE_FAILED", "VkResult=%d", (int) result);
        return result;
    }

    if (set_loader_data != NULL
            && set_loader_data(*device, *device) != VK_SUCCESS) {
        PFN_vkDestroyDevice next_destroy =
                (PFN_vkDestroyDevice) next_gdpa(*device, "vkDestroyDevice");
        if (next_destroy != NULL) next_destroy(*device, allocator);
        free(record);
        log_event("CREATE_DEVICE_LOADER_DATA_FAILED", "");
        return VK_ERROR_INITIALIZATION_FAILED;
    }

    record->device = *device;
    record->next_gdpa = next_gdpa;
    pthread_mutex_lock(&gx_lock);
    record->next = gx_devices;
    gx_devices = record;
    pthread_mutex_unlock(&gx_lock);

    char gpu_name[VK_MAX_PHYSICAL_DEVICE_NAME_SIZE + 1] = "unknown";
    uint32_t vendor_id = 0;
    uint32_t device_id = 0;
    if (known_gipa != NULL) {
        PFN_vkGetPhysicalDeviceProperties get_properties =
                (PFN_vkGetPhysicalDeviceProperties) known_gipa(instance, "vkGetPhysicalDeviceProperties");
        if (get_properties != NULL) {
            VkPhysicalDeviceProperties properties;
            memset(&properties, 0, sizeof(properties));
            get_properties(physical_device, &properties);
            memcpy(gpu_name, properties.deviceName, VK_MAX_PHYSICAL_DEVICE_NAME_SIZE);
            gpu_name[VK_MAX_PHYSICAL_DEVICE_NAME_SIZE] = '\0';
            vendor_id = properties.vendorID;
            device_id = properties.deviceID;
        }
    }
    log_event("DEVICE_CREATED", "gpu=%s vendor_id=%u device_id=%u",
              gpu_name, vendor_id, device_id);
    return VK_SUCCESS;
}

static void VKAPI_CALL gx_destroy_device(VkDevice device,
                                          const VkAllocationCallbacks *allocator) {
    GxDeviceRecord *record = snapshot_device(device);
    if (record == NULL) return;
    PFN_vkDestroyDevice next_destroy =
            (PFN_vkDestroyDevice) record->next_gdpa(device, "vkDestroyDevice");
    log_event("DEVICE_DESTROYED", "");
    if (next_destroy != NULL) next_destroy(device, allocator);
    free(record);
    forget_device(device);
}

static PFN_vkVoidFunction VKAPI_CALL gx_get_instance_proc_addr(VkInstance instance,
                                                                const char *name) {
    if (name == NULL) return NULL;
    if (strcmp(name, "vkGetInstanceProcAddr") == 0)
        return (PFN_vkVoidFunction) gx_get_instance_proc_addr;
    if (strcmp(name, "vkCreateInstance") == 0)
        return (PFN_vkVoidFunction) gx_create_instance;

    if (instance == VK_NULL_HANDLE) return NULL;

    if (strcmp(name, "vkGetDeviceProcAddr") == 0)
        return (PFN_vkVoidFunction) gx_get_device_proc_addr;
    if (strcmp(name, "vkCreateDevice") == 0)
        return (PFN_vkVoidFunction) gx_create_device;
    if (strcmp(name, "vkDestroyInstance") == 0)
        return (PFN_vkVoidFunction) gx_destroy_instance;
    if (strcmp(name, "vkEnumeratePhysicalDevices") == 0)
        return (PFN_vkVoidFunction) gx_enumerate_physical_devices;

    GxInstanceRecord *record = snapshot_instance(instance);
    if (record == NULL) return NULL;
    if (strcmp(name, "vkEnumeratePhysicalDeviceGroups") == 0) {
        VkBool32 available = record->api_version >= VK_API_VERSION_1_1;
        free(record);
        return available ? (PFN_vkVoidFunction) gx_enumerate_physical_device_groups : NULL;
    }
    if (strcmp(name, "vkEnumeratePhysicalDeviceGroupsKHR") == 0) {
        VkBool32 available = record->device_group_extension_enabled;
        free(record);
        return available ? (PFN_vkVoidFunction) gx_enumerate_physical_device_groups_khr : NULL;
    }
    PFN_vkVoidFunction next = record->next_gipa(instance, name);
    free(record);
    return next;
}

static PFN_vkVoidFunction VKAPI_CALL gx_get_device_proc_addr(VkDevice device,
                                                              const char *name) {
    if (name == NULL) return NULL;
    if (strcmp(name, "vkGetDeviceProcAddr") == 0)
        return (PFN_vkVoidFunction) gx_get_device_proc_addr;
    if (strcmp(name, "vkDestroyDevice") == 0)
        return (PFN_vkVoidFunction) gx_destroy_device;

    GxDeviceRecord *record = snapshot_device(device);
    if (record == NULL) return NULL;
    PFN_vkVoidFunction next = record->next_gdpa(device, name);
    free(record);
    return next;
}

GX_LAYER_EXPORT VKAPI_ATTR VkResult VKAPI_CALL
vkNegotiateLoaderLayerInterfaceVersion(VkNegotiateLayerInterface *version) {
    if (version == NULL || version->sType != LAYER_NEGOTIATE_INTERFACE_STRUCT)
        return VK_ERROR_INITIALIZATION_FAILED;
    if (version->loaderLayerInterfaceVersion < MIN_SUPPORTED_LOADER_LAYER_INTERFACE_VERSION)
        return VK_ERROR_INITIALIZATION_FAILED;

    if (version->loaderLayerInterfaceVersion > CURRENT_LOADER_LAYER_INTERFACE_VERSION)
        version->loaderLayerInterfaceVersion = CURRENT_LOADER_LAYER_INTERFACE_VERSION;
    version->pfnGetInstanceProcAddr = gx_get_instance_proc_addr;
    version->pfnGetDeviceProcAddr = gx_get_device_proc_addr;
    /* This layer does not intercept physical-device extension commands. */
    version->pfnGetPhysicalDeviceProcAddr = NULL;
    return VK_SUCCESS;
}

GX_LAYER_EXPORT VKAPI_ATTR PFN_vkVoidFunction VKAPI_CALL
vkGetInstanceProcAddr(VkInstance instance, const char *name) {
    return gx_get_instance_proc_addr(instance, name);
}

GX_LAYER_EXPORT VKAPI_ATTR PFN_vkVoidFunction VKAPI_CALL
vkGetDeviceProcAddr(VkDevice device, const char *name) {
    return gx_get_device_proc_addr(device, name);
}
