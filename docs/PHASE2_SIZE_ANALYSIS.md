# Phase 2 APK and Resource Size Analysis

## Source tree findings

The Phase 1 repository tree contained approximately 236 MiB of tracked blobs. The app asset directory accounted for approximately 223.3 MiB. Largest source archives included:

| Asset | Approximate size |
|---|---:|
| container_pattern_common.tzst | 80.6 MiB |
| wincomponents/direct3d.tzst | 29.8 MiB |
| graphics_driver/adrenotools-v819.tzst | 9.4 MiB |
| dxwrapper/dxvk-1.10.3.tzst | 7.2 MiB |
| graphics_driver/wrapper.tzst | 3.9 MiB |
| graphics_driver/wrapper-v2.tzst | 3.6 MiB |
| graphics_driver/wrapper-1.1.tzst | 3.6 MiB |

The asset inventory also contains multiple Box64, FEXCore, DXVK/VKD3D, Wrapper, Wine components and support data. Their presence is not proof that each item is duplicated or unused.

## Known build packaging behavior

- app/build.gradle filters native builds to arm64-v8a, so only this ABI should be packaged.
- The Gradle downloadAssets task fetches imagefs.tzst into app assets before preBuild. This is a large external build-time dependency and is bundled with the app assets unless packaging changes.
- Phase 1 GitHub Actions artifact ZIP size was 446,623,228 bytes. This is not the separately measured APK file size.
- No runtime archive, driver or wrapper was deleted in Phase 2.

## Phase 2 APK inspection

After assembling the APK, Phase 2 CI runs aapt dump badging and scripts/inspect-apk.py. The JSON report records:
- exact APK file size in bytes
- package name and version metadata
- native ABI list and native library byte totals
- asset entry count, compressed/uncompressed totals and asset groups
- 20 largest APK ZIP entries

The inspection fails CI if AndroidManifest.xml is missing, package metadata cannot be parsed, arm64-v8a is absent, or an unexpected ABI is packaged.

Exact Phase 2 APK size and contents: PENDING CI inspection.

## Safe size-reduction options for later evaluation

- Measure archive use in actual install/container paths before making assets optional.
- Preserve offline setup unless an explicit online-only mode is designed with checksum verification, atomic install, retry and offline fallback.
- Do not remove older DXVK/Wrapper/Wine versions solely because they are large; containers or shortcuts may select them.
- Keep ABI filtering at arm64-v8a until another supported target is intentionally added.
