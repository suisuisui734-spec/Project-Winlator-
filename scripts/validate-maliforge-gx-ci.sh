#!/usr/bin/env bash
set -euo pipefail

workflow=".github/workflows/build-apk.yml"
if [[ ! -f "$workflow" ]]; then
  echo "Missing workflow: $workflow" >&2
  exit 1
fi

required_branches=(
  "main"
  "feature/maliforge-gx-stage19-config-safety"
  "feature/maliforge-gx-stage20-storage"
  "feature/maliforge-gx-stage21-game-advisor"
  "feature/maliforge-gx-stage22-ci"
  "feature/maliforge-gx-stage23-release"
  "feature/maliforge-gx-game-rendering-backend"
)

count_in_section() {
  local section="$1"
  local branch="$2"
  awk -v wanted_section="$section" -v wanted_branch="$branch" '
    $0 == "  push:" { current_section = "push"; next }
    $0 == "  pull_request:" { current_section = "pull_request"; next }
    $0 == "  workflow_dispatch:" { current_section = ""; next }
    current_section == wanted_section && $0 == "      - " wanted_branch { count++ }
    END { print count + 0 }
  ' "$workflow"
}

for branch in "${required_branches[@]}"; do
  for section in push pull_request; do
    count="$(count_in_section "$section" "$branch")"
    if [[ "$count" -ne 1 ]]; then
      echo "Workflow must include ${branch} exactly once in ${section} filters (found ${count})." >&2
      exit 1
    fi
  done
done

required_markers=(
  "workflow_dispatch:"
  "bash scripts/validate-source.sh"
  "bash scripts/validate-maliforge-gx-ci.sh"
  "bash scripts/build-maliforge-gx-layer.sh --smoke-test"
  "Run unit tests and lint for the selected debug flavor"
  "Build APK(s)"
  "Inspect APK package metadata and native ABI payload"
  "Verify MaliForge GX guest layer is packaged as an x86_64 asset"
  "actions/upload-artifact@v4"
  "stage23-validation"
)

for marker in "${required_markers[@]}"; do
  if ! grep -Fq -- "${marker}" "$workflow"; then
    echo "Missing required CI workflow marker: ${marker}" >&2
    exit 1
  fi
done

echo "MaliForge GX CI coverage validated for Stages 19–23 and the Vulkan capability snapshot branch in both push and pull_request filters."
