#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
SOURCE="$ROOT_DIR/app/src/main/cpp/maliforge/gx_vulkan_layer.c"
ASSET_DIR="$ROOT_DIR/app/src/main/assets/maliforge-gx"
BUILD_DIR="$ROOT_DIR/build/maliforge-gx-layer"
MANIFEST_DIR="$BUILD_DIR/implicit_layer.d"
LIBRARY="$ASSET_DIR/libmaliforge_gx_layer_x86_64.so"
MANIFEST="$MANIFEST_DIR/MaliForge-GX.json"

mkdir -p "$ASSET_DIR" "$MANIFEST_DIR" "$BUILD_DIR"
command -v cc >/dev/null 2>&1 || { echo "A C compiler is required" >&2; exit 1; }
command -v readelf >/dev/null 2>&1 || { echo "binutils (readelf) is required" >&2; exit 1; }
test -f "$SOURCE" || { echo "Vulkan layer source is missing: $SOURCE" >&2; exit 1; }
test -f /usr/include/vulkan/vulkan.h || { echo "libvulkan-dev headers are required" >&2; exit 1; }
test -f /usr/include/vulkan/vk_layer.h || { echo "Vulkan loader layer headers are required" >&2; exit 1; }

cc -std=c11 -O2 -fPIC -fvisibility=hidden -Wall -Wextra \
  -Werror=implicit-function-declaration -shared \
  -Wl,-z,defs -Wl,-soname,libmaliforge_gx_layer_x86_64.so \
  -o "$LIBRARY" "$SOURCE" -pthread
readelf -h "$LIBRARY" | grep -q 'Class:.*ELF64'
readelf -h "$LIBRARY" | grep -q 'Data:.*little endian'
readelf -h "$LIBRARY" | grep -q 'Machine:.*Advanced Micro Devices X86-64'
nm -D "$LIBRARY" | grep -q 'vkNegotiateLoaderLayerInterfaceVersion'
nm -D "$LIBRARY" | grep -q 'vkGetInstanceProcAddr'
nm -D "$LIBRARY" | grep -q 'vkGetDeviceProcAddr'

cat > "$MANIFEST" <<EOF
{
  "file_format_version": "1.2.0",
  "layer": {
    "name": "VK_LAYER_MALIFORGE_gx_diagnostics",
    "type": "GLOBAL",
    "library_path": "$LIBRARY",
    "library_arch": "64",
    "api_version": "1.3.0",
    "implementation_version": "1",
    "description": "MaliForge GX opt-in Vulkan pass-through runtime diagnostics",
    "enable_environment": {"MALIFORGE_GX_VULKAN_LAYER": "1"},
    "disable_environment": {"MALIFORGE_GX_VULKAN_LAYER_DISABLE": "1"}
  }
}
EOF
python3 -m json.tool "$MANIFEST" >/dev/null

if [[ "${1:-}" == "--smoke-test" ]]; then
  DRIVER_JSON="$(find /usr/share/vulkan/icd.d -maxdepth 1 -type f -iname '*lvp*.json' -print -quit 2>/dev/null || true)"
  if [[ -z "$DRIVER_JSON" ]]; then
    echo "Mesa lavapipe Vulkan ICD is required for the software smoke test" >&2
    exit 1
  fi
  test -f "$ROOT_DIR/scripts/maliforge-gx-vulkan-smoke.c"
  cc -std=c11 -O2 -Wall -Wextra -Werror \
    -o "$BUILD_DIR/gx_vulkan_layer_smoke" \
    "$ROOT_DIR/scripts/maliforge-gx-vulkan-smoke.c" -lvulkan

  export VK_ICD_FILENAMES="$DRIVER_JSON"
  export VK_ADD_IMPLICIT_LAYER_PATH="$MANIFEST_DIR${VK_ADD_IMPLICIT_LAYER_PATH:+:$VK_ADD_IMPLICIT_LAYER_PATH}"
  export MALIFORGE_GX_VULKAN_LAYER=1
  unset MALIFORGE_GX_VULKAN_LAYER_DISABLE || true
  export MALIFORGE_GX_LAYER_LOG="$BUILD_DIR/vulkan-layer-smoke.log"
  rm -f "$MALIFORGE_GX_LAYER_LOG"
  "$BUILD_DIR/gx_vulkan_layer_smoke"

  grep -q "event=QUEUE_SUBMIT_METRICS.*calls=1.*failures=0" "$MALIFORGE_GX_LAYER_LOG" || {
    echo "Expected Vulkan queue-submit telemetry event missing" >&2
    cat "$MALIFORGE_GX_LAYER_LOG" >&2 || true
    exit 1
  }
  for event in INSTANCE_CREATED PHYSICAL_DEVICES_ENUMERATED PHYSICAL_DEVICE_GROUPS_ENUMERATED DEVICE_CREATED QUEUE_SUBMIT_METRICS DEVICE_DESTROYED INSTANCE_DESTROYED; do
    grep -q "event=$event" "$MALIFORGE_GX_LAYER_LOG" || {
      echo "Expected Vulkan layer event missing: $event" >&2
      cat "$MALIFORGE_GX_LAYER_LOG" >&2 || true
      exit 1
    }
  done
  grep -q "event=DEVICE_CREATED.*gpu=" "$MALIFORGE_GX_LAYER_LOG" || { echo "GPU identity log missing" >&2; cat "$MALIFORGE_GX_LAYER_LOG" >&2; exit 1; }
  echo "MaliForge GX implicit Vulkan layer software smoke test passed."
  cat "$MALIFORGE_GX_LAYER_LOG"
fi
