#!/usr/bin/env bash
set -euo pipefail

workflow=".github/workflows/build-apk.yml"
if [[ ! -f "$workflow" ]]; then
  echo "Missing workflow: $workflow" >&2
  exit 1
fi

required_branches=(
  "feature/maliforge-gx-stage19-config-safety"
  "feature/maliforge-gx-stage20-storage"
  "feature/maliforge-gx-stage21-game-advisor"
  "feature/maliforge-gx-stage22-ci"
  "feature/maliforge-gx-stage23-release"
)

for branch in "${required_branches[@]}"; do
  count="$(grep -F -- "- ${branch}" "$workflow" | wc -l | tr -d ' ')"
  if [[ "$count" -lt 2 ]]; then
    echo "Workflow must include ${branch} in both push and pull_request filters (found ${count})." >&2
    exit 1
  fi
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

echo "MaliForge GX CI coverage validated for Stages 19–23."
