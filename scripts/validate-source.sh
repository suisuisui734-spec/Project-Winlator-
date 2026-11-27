#!/usr/bin/env bash
set -euo pipefail

fail() {
  echo "::error::$1" >&2
  exit 1
}
pass() { echo "::notice::$1"; }

[[ -f settings.gradle ]] || fail "settings.gradle is missing"
[[ -f gradlew ]] || fail "Gradle wrapper is missing"
[[ -f app/src/main/AndroidManifest.xml ]] || fail "AndroidManifest.xml is missing"
[[ -f app/src/main/cpp/CMakeLists.txt ]] || fail "Native CMakeLists.txt is missing"

grep -Eq "abiFilters[[:space:]]+['\"]arm64-v8a['\"]" app/build.gradle \
  || fail "Expected arm64-v8a ABI filter was not found"
grep -Fq "ndkVersion '29.0.14206865'" app/build.gradle \
  || fail "Expected NDK version is not configured"
grep -Fq "mali_counters.cpp" app/src/main/cpp/CMakeLists.txt \
  || fail "Mali counter source is not part of the native build"
grep -Fq 'add_subdirectory(zerofg)' app/src/main/cpp/CMakeLists.txt \
  || fail "ZeroFG native target is not registered"
grep -Fq 'DEFAULT_GRAPHICS_DRIVER = "wrapper"' app/src/main/java/com/winlator/cmod/container/Container.java \
  || fail "The expected Wrapper default is missing"

python3 - <<'PY'
import json
from pathlib import Path

for name in ("box64_env_vars.json", "fexcore_env_vars.json", "gpu_cards.json"):
    path = Path("app/src/main/assets") / name
    if not path.is_file():
        raise SystemExit(f"Missing asset: {path}")
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except Exception as exc:
        raise SystemExit(f"Invalid JSON in {path}: {exc}")
    if not isinstance(data, list):
        raise SystemExit(f"Expected a JSON array in {path}")
    if name != "gpu_cards.json" and (not data or any(not isinstance(item, dict) or not item.get("name") for item in data)):
        raise SystemExit(f"Invalid environment variable definitions in {path}")

print("JSON assets: valid")
PY

if [[ -f .gitmodules ]]; then
  submodules="$(git submodule status --recursive || true)"
  if grep -qE '^-' <<<"$submodules"; then
    fail "A required Git submodule was not initialized"
  fi
  [[ -f app/src/main/cpp/adrenotools/CMakeLists.txt ]] \
    || fail "The adrenotools submodule CMakeLists.txt is missing"
fi

pass "Project structure, ARM64 configuration, Mali native source, and JSON assets validated"
