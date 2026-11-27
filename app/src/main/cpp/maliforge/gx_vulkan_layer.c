#define _GNU_SOURCE
#include <vulkan/vulkan.h>
#include <vulkan/vk_layer.h>

#include <errno.h>
#include <fcntl.h>
#include <pthread.h>
#include <stdarg.h>
#include <stdint.h>
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
    uint64_t queue_submit_calls;
    uint64_t queue_submit_failures;
    uint64_t queue_submit_total_ns;
    uint64_t queue_submit_max_ns;
    uint64_t queue_present_calls;
    uint64_t queue_present_failures;
    uint64_t queue_present_successes;
    uint64_t queue_present_total_ns;
    uint64_t queue_present_max_ns;
    uint64_t queue_present_interval_samples;
    uint64_t queue_present_interval_total_ns;
    uint64_t queue_present_interval_max_ns;
    uint64_t last_present_start_ns;
    struct GxDeviceRecord *next;
} GxDeviceRecord;

typedef struct GxQueueRecord {
    VkQueue queue;
    VkDevice device;
    struct GxQueueRecord *next;
} GxQueueRecord;

typedef struct GxQueueDispatch {
    VkDevice device;
    PFN_vkGetDeviceProcAddr next_gdpa;
} GxQueueDispatch;

static pthread_mutex_t gx_lock = PTHREAD_MUTEX_INITIALIZER;
static GxInstanceRecord *gx_instances;
static GxPhysicalDeviceRecord *gx_physical_devices;
static GxDeviceRecord *gx_devices;
static GxQueueRecord *gx_queues;

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

static void remember_queue(VkDevice device, VkQueue queue) {
    if (device == VK_NULL_HANDLE || queue == VK_NULL_HANDLE) return;
    pthread_mutex_lock(&gx_lock);
    GxQueueRecord *record;
    for (record = gx_queues; record != NULL; record = record->next) {
        if (record->queue == queue) {
            record->device = device;
            pthread_mutex_unlock(&gx_lock);
            return;
        }
    }
    record = (GxQueueRecord *) calloc(1, sizeof(*record));
    if (record != NULL) {
        record->queue = queue;
        record->device = device;
        record->next = gx_queues;
        gx_queues = record;
    }
    pthread_mutex_unlock(&gx_lock);
}

static int snapshot_queue_dispatch(VkQueue queue, GxQueueDispatch *dispatch) {
    if (queue == VK_NULL_HANDLE || dispatch == NULL) return 0;
    int found = 0;
    pthread_mutex_lock(&gx_lock);
    for (GxQueueRecord *queue_record = gx_queues;
         queue_record != NULL; queue_record = queue_record->next) {
        if (queue_record->queue != queue) continue;
        GxDeviceRecord *device_record = find_device(queue_record->device);
        if (device_record != NULL) {
            dispatch->device = device_record->device;
            dispatch->next_gdpa = device_record->next_gdpa;
            found = 1;
        }
        break;
    }
    pthread_mutex_unlock(&gx_lock);
    return found;
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

    GxQueueRecord **queue_cursor = &gx_queues;
    while (*queue_cursor != NULL) {
        if ((*queue_cursor)->device == device) {
            GxQueueRecord *removed = *queue_cursor;
            *queue_cursor = removed->next;
            free(removed);
            continue;
        }
        queue_cursor = &(*queue_cursor)->next;
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


static uint64_t monotonic_now_ns(void) {
    struct timespec now;
    if (clock_gettime(CLOCK_MONOTONIC, &now) != 0) return 0;
    return (uint64_t) now.tv_sec * 1000000000ULL + (uint64_t) now.tv_nsec;
}

static void record_queue_submit_metrics(VkDevice device, uint64_t elapsed_ns,
                                        VkResult result) {
    uint64_t calls = 0, failures = 0, total_ns = 0, max_ns = 0;
    int emit = 0;
    pthread_mutex_lock(&gx_lock);
    GxDeviceRecord *record = find_device(device);
    if (record != NULL) {
        record->queue_submit_calls++;
        if (result != VK_SUCCESS) record->queue_submit_failures++;
        record->queue_submit_total_ns += elapsed_ns;
        if (elapsed_ns > record->queue_submit_max_ns)
            record->queue_submit_max_ns = elapsed_ns;
        calls = record->queue_submit_calls;
        failures = record->queue_submit_failures;
        total_ns = record->queue_submit_total_ns;
        max_ns = record->queue_submit_max_ns;
        emit = calls == 1 || (calls % 256) == 0;
    }
    pthread_mutex_unlock(&gx_lock);

    if (emit) {
        log_event("QUEUE_SUBMIT_METRICS",
                  "calls=%llu failures=%llu last_result=%d avg_cpu_ns=%llu max_cpu_ns=%llu",
                  (unsigned long long) calls, (unsigned long long) failures, (int) result,
                  (unsigned long long) (calls == 0 ? 0 : total_ns / calls),
                  (unsigned long long) max_ns);
    }
}

static void record_queue_present_metrics(VkDevice device, uint64_t start_ns,
                                         uint64_t elapsed_ns, VkResult result) {
    uint64_t calls = 0, failures = 0, successes = 0;
    uint64_t total_ns = 0, max_ns = 0, interval_samples = 0;
    uint64_t interval_total_ns = 0, interval_max_ns = 0;
    int emit = 0;
    pthread_mutex_lock(&gx_lock);
    GxDeviceRecord *record = find_device(device);
    if (record != NULL) {
        record->queue_present_calls++;
        if (result == VK_SUCCESS || result == VK_SUBOPTIMAL_KHR) {
            if (record->last_present_start_ns != 0 && start_ns >= record->last_present_start_ns) {
                uint64_t interval_ns = start_ns - record->last_present_start_ns;
                record->queue_present_interval_samples++;
                record->queue_present_interval_total_ns += interval_ns;
                if (interval_ns > record->queue_present_interval_max_ns)
                    record->queue_present_interval_max_ns = interval_ns;
            }
            record->last_present_start_ns = start_ns;
            record->queue_present_successes++;
        } else {
            record->queue_present_failures++;
        }
        record->queue_present_total_ns += elapsed_ns;
        if (elapsed_ns > record->queue_present_max_ns)
            record->queue_present_max_ns = elapsed_ns;
        calls = record->queue_present_calls;
        failures = record->queue_present_failures;
        successes = record->queue_present_successes;
        total_ns = record->queue_present_total_ns;
        max_ns = record->queue_present_max_ns;
        interval_samples = record->queue_present_interval_samples;
        interval_total_ns = record->queue_present_interval_total_ns;
        interval_max_ns = record->queue_present_interval_max_ns;
        emit = calls == 1 || (successes != 0 && (successes % 60) == 0);
    }
    pthread_mutex_unlock(&gx_lock);

    if (emit) {
        log_event("QUEUE_PRESENT_METRICS",
                  "calls=%llu successes=%llu failures=%llu last_result=%d avg_cpu_ns=%llu max_cpu_ns=%llu avg_call_interval_ns=%llu max_call_interval_ns=%llu metric=guest_present_call_cadence_not_display_fps",
                  (unsigned long long) calls, (unsigned long long) successes,
                  (unsigned long long) failures, (int) result,
                  (unsigned long long) (calls == 0 ? 0 : total_ns / calls),
                  (unsigned long long) max_ns,
                  (unsigned long long) (interval_samples == 0 ? 0 : interval_total_ns / interval_samples),
                  (unsigned long long) interval_max_ns);
    }
}

static void VKAPI_CALL gx_get_device_queue(VkDevice device, uint32_t queue_family_index,
                                           uint32_t queue_index, VkQueue *queue) {
    GxDeviceRecord *record = snapshot_device(device);
    if (record == NULL) {
        if (queue != NULL) *queue = VK_NULL_HANDLE;
        return;
    }
    PFN_vkGetDeviceQueue next_get =
            (PFN_vkGetDeviceQueue) record->next_gdpa(device, "vkGetDeviceQueue");
    free(record);
    if (next_get == NULL) {
        if (queue != NULL) *queue = VK_NULL_HANDLE;
        log_event("GET_DEVICE_QUEUE_UNAVAILABLE", "");
        return;
    }
    next_get(device, queue_family_index, queue_index, queue);
    if (queue != NULL && *queue != VK_NULL_HANDLE) remember_queue(device, *queue);
}

static void VKAPI_CALL gx_get_device_queue2(VkDevice device,
                                             const VkDeviceQueueInfo2 *queue_info,
                                             VkQueue *queue) {
    GxDeviceRecord *record = snapshot_device(device);
    if (record == NULL) {
        if (queue != NULL) *queue = VK_NULL_HANDLE;
        return;
    }
    PFN_vkGetDeviceQueue2 next_get =
            (PFN_vkGetDeviceQueue2) record->next_gdpa(device, "vkGetDeviceQueue2");
    free(record);
    if (next_get == NULL) {
        if (queue != NULL) *queue = VK_NULL_HANDLE;
        log_event("GET_DEVICE_QUEUE2_UNAVAILABLE", "");
        return;
    }
    next_get(device, queue_info, queue);
    if (queue != NULL && *queue != VK_NULL_HANDLE) remember_queue(device, *queue);
}

static VkResult VKAPI_CALL gx_queue_submit(VkQueue queue, uint32_t submit_count,
                                           const VkSubmitInfo *submits, VkFence fence) {
    GxQueueDispatch dispatch;
    if (!snapshot_queue_dispatch(queue, &dispatch)) {
        log_event("QUEUE_DISPATCH_UNAVAILABLE", "command=vkQueueSubmit");
        return VK_ERROR_INITIALIZATION_FAILED;
    }
    PFN_vkQueueSubmit next_submit =
            (PFN_vkQueueSubmit) dispatch.next_gdpa(dispatch.device, "vkQueueSubmit");
    if (next_submit == NULL) return VK_ERROR_INITIALIZATION_FAILED;
    uint64_t start_ns = monotonic_now_ns();
    VkResult result = next_submit(queue, submit_count, submits, fence);
    uint64_t end_ns = monotonic_now_ns();
    record_queue_submit_metrics(dispatch.device,
            end_ns >= start_ns ? end_ns - start_ns : 0, result);
    return result;
}

static VkResult VKAPI_CALL gx_queue_submit2(VkQueue queue, uint32_t submit_count,
                                            const VkSubmitInfo2 *submits, VkFence fence) {
    GxQueueDispatch dispatch;
    if (!snapshot_queue_dispatch(queue, &dispatch)) {
        log_event("QUEUE_DISPATCH_UNAVAILABLE", "command=vkQueueSubmit2");
        return VK_ERROR_INITIALIZATION_FAILED;
    }
    PFN_vkQueueSubmit2 next_submit =
            (PFN_vkQueueSubmit2) dispatch.next_gdpa(dispatch.device, "vkQueueSubmit2");
    if (next_submit == NULL) return VK_ERROR_EXTENSION_NOT_PRESENT;
    uint64_t start_ns = monotonic_now_ns();
    VkResult result = next_submit(queue, submit_count, submits, fence);
    uint64_t end_ns = monotonic_now_ns();
    record_queue_submit_metrics(dispatch.device,
            end_ns >= start_ns ? end_ns - start_ns : 0, result);
    return result;
}

static VkResult VKAPI_CALL gx_queue_submit2_khr(VkQueue queue, uint32_t submit_count,
                                                const VkSubmitInfo2KHR *submits, VkFence fence) {
    GxQueueDispatch dispatch;
    if (!snapshot_queue_dispatch(queue, &dispatch)) {
        log_event("QUEUE_DISPATCH_UNAVAILABLE", "command=vkQueueSubmit2KHR");
        return VK_ERROR_INITIALIZATION_FAILED;
    }
    PFN_vkQueueSubmit2KHR next_submit =
            (PFN_vkQueueSubmit2KHR) dispatch.next_gdpa(dispatch.device, "vkQueueSubmit2KHR");
    if (next_submit == NULL) return VK_ERROR_EXTENSION_NOT_PRESENT;
    uint64_t start_ns = monotonic_now_ns();
    VkResult result = next_submit(queue, submit_count, submits, fence);
    uint64_t end_ns = monotonic_now_ns();
    record_queue_submit_metrics(dispatch.device,
            end_ns >= start_ns ? end_ns - start_ns : 0, result);
    return result;
}

static VkResult VKAPI_CALL gx_queue_present_khr(VkQueue queue,
                                                 const VkPresentInfoKHR *present_info) {
    GxQueueDispatch dispatch;
    if (!snapshot_queue_dispatch(queue, &dispatch)) {
        log_event("QUEUE_DISPATCH_UNAVAILABLE", "command=vkQueuePresentKHR");
        return VK_ERROR_INITIALIZATION_FAILED;
    }
    PFN_vkQueuePresentKHR next_present =
            (PFN_vkQueuePresentKHR) dispatch.next_gdpa(dispatch.device, "vkQueuePresentKHR");
    if (next_present == NULL) return VK_ERROR_EXTENSION_NOT_PRESENT;
    uint64_t start_ns = monotonic_now_ns();
    VkResult result = next_present(queue, present_info);
    uint64_t end_ns = monotonic_now_ns();
    record_queue_present_metrics(dispatch.device, start_ns,
            end_ns >= start_ns ? end_ns - start_ns : 0, result);
    return result;
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

    if (strcmp(name, "vkGetDeviceQueue") == 0) {
        free(record);
        return (PFN_vkVoidFunction) gx_get_device_queue;
    }
    if (strcmp(name, "vkGetDeviceQueue2") == 0) {
        PFN_vkVoidFunction next = record->next_gdpa(device, name);
        free(record);
        return next != NULL ? (PFN_vkVoidFunction) gx_get_device_queue2 : NULL;
    }
    if (strcmp(name, "vkQueueSubmit") == 0) {
        PFN_vkVoidFunction next = record->next_gdpa(device, name);
        free(record);
        return next != NULL ? (PFN_vkVoidFunction) gx_queue_submit : NULL;
    }
    if (strcmp(name, "vkQueueSubmit2") == 0) {
        PFN_vkVoidFunction next = record->next_gdpa(device, name);
        free(record);
        return next != NULL ? (PFN_vkVoidFunction) gx_queue_submit2 : NULL;
    }
    if (strcmp(name, "vkQueueSubmit2KHR") == 0) {
        PFN_vkVoidFunction next = record->next_gdpa(device, name);
        free(record);
        return next != NULL ? (PFN_vkVoidFunction) gx_queue_submit2_khr : NULL;
    }
    if (strcmp(name, "vkQueuePresentKHR") == 0) {
        PFN_vkVoidFunction next = record->next_gdpa(device, name);
        free(record);
        return next != NULL ? (PFN_vkVoidFunction) gx_queue_present_khr : NULL;
    }

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
