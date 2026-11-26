# Phase 2 APK and Resource Size Analysis

## Latest successful APK inspection

- CI run: 45905943515
- Source commit: ddd75ccb66c3682e28d3aeed0e9a3f134325930d
- APK: app/build/outputs/apk/standard/debug/app-standard-debug.apk
- Package: com.winlator.mali
- Version: 1.2.1 Bionic, versionCode 23
- Exact APK size: 447,763,096 bytes (about 427 MiB)
- ZIP entries: 1,291
- Native ABIs: arm64-v8a only
- Native library count: 22
- Native libraries, uncompressed: 11,663,800 bytes
- Asset entries: 110
- Asset uncompressed total: 433,937,395 bytes
- Asset compressed total: 432,187,879 bytes

## Largest APK contents

| APK entry | Uncompressed bytes |
|---|---:|
| assets/imagefs.tzst | 199,788,880 |
| assets/container_pattern_common.tzst | 84,522,663 |
| assets/wincomponents/direct3d.tzst | 31,207,067 |
| classes.dex | 11,046,824 |
| assets/graphics_driver/adrenotools-v819.tzst | 9,881,991 |

Other large asset groups:

| Group | Uncompressed bytes |
|---|---:|
| DX wrappers | 47,386,731 |
| Windows components | 38,154,710 |
| Graphics drivers | 33,465,796 |
| FEXCore packages | 8,448,372 |
| Box64 packages | 6,510,212 |
| Layers archive | 4,447,187 |

## Conclusions

APK size is dominated by bundled runtime assets. The native library payload is about 11.7 MB and is not the main package-size driver. Native packaging already filters to arm64-v8a, so ABI splits would not materially reduce size while only this ABI is configured.

No runtime archive, driver, wrapper, Wine/Box64/FEX package, or game/container data was deleted to reduce size.

## Safe future options

- Design optional on-demand runtime packages only after verifying each asset's install/offline usage path.
- Add trusted checksums, atomic writes, retries, resume, rollback and offline fallback before making any runtime download optional.
- Preserve the existing bundled/offline mode.
- Do not remove older Wrapper/DXVK/Wine versions until references from per-container and per-shortcut configurations are proven unused.
