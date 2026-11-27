# Project Winlator Build Guide

## Requirements

- JDK 17
- Android SDK platform 34
- Android Build Tools 35.0.0
- Android NDK 29.0.14206865
- CMake 3.22.1
- Git with recursive submodule support
- Linux/macOS shell or Windows with the Gradle wrapper

The app's Gradle ABI filter currently targets `arm64-v8a`.

## Clone

```bash
git clone --recurse-submodules https://github.com/suisuisui734-spec/Project-Winlator-.git
cd Project-Winlator-
git checkout feature/helio-g100-mali-optimization
```

If already cloned:

```bash
git submodule update --init --recursive
```

## Validate and build a debug APK

```bash
bash scripts/validate-source.sh
chmod +x ./gradlew
./gradlew --no-daemon :app:testStandardDebugUnitTest :app:lintStandardDebug :app:assembleStandardDebug
```

The APK is expected under:

```
app/build/outputs/apk/standard/debug/
```

The Gradle `downloadAssets` task downloads the upstream `imagefs.tzst` asset before pre-build tasks. A network connection to the configured release URL is required.

## GitHub Actions

Pushes to `feature/helio-g100-mali-optimization` run source validation, standard debug unit-test/lint/build tasks and upload a debug APK as a workflow artifact. The workflow can also be started manually to choose another flavor or release build type.

Artifacts are temporary CI outputs and are not published as a GitHub Release. A release-signed APK requires a separately configured signing key; never commit a keystore or signing password.

## Troubleshooting

- Check that the adrenotools submodule is initialized recursively.
- Confirm the NDK and CMake versions match `app/build.gradle`.
- Check that `imagefs.tzst` can be downloaded from the upstream release configured in Gradle.
- Inspect the failed Actions job log and fix the first causal error before downstream errors.
- Do not remove native libraries or graphics assets as a build-size workaround without tracing their dependencies.

## Rollback

The optimization work is isolated in `feature/helio-g100-mali-optimization`. Checkout `main` to return to the imported upstream source; this workflow does not merge to main.
