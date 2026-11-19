# Android Build Review — 19 November 2026

## Scope

- Repository: `samredwings/Aura-AI-Gallery`
- Branch: `android/rebuild-m1`
- Reviewed source revision: `fa11d469a462fc298256764686169923ec861ebd`
- Android package: `ai.sakana.aura.gallery`

## Verification

| Check | Result |
|---|---|
| `./gradlew --no-daemon clean :app:check :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease --stacktrace` | PASS; 38.9 seconds; 130 actionable tasks |
| `./gradlew --no-daemon :app:testDebugUnitTest --rerun-tasks` | PASS; 9 tests, 0 failures, 0 errors, 0 skipped |
| Android Lint | PASS; 0 errors, 19 warnings |
| Debug APK | Built; 19 MB; package `ai.sakana.aura.gallery`; version `0.1.0`; min SDK 26; target SDK 36 |
| Release APK | Built; 1.9 MB; unsigned (not a distributable/install-over-install release artifact) |

The sandbox build used Temurin JDK 21. The committed GitHub workflow targets JDK 17; CI must complete to verify that exact toolchain. The 19 lint warnings are version-update notices, an unnecessary `mipmap-anydpi-v26` qualifier, and the documented Room KAPT/KSP trade-off. They are warnings, not build errors.

## CI failure and correction

The previous GitHub Actions run for the reviewed revision failed before Gradle was invoked. The `Set up Android SDK` step used the `android-actions/setup-android@v3` default package list, which included the obsolete SDK package `tools` alongside `platform-tools`. The SDK manager reported `Failed to find package 'tools'` and exited with code 1.

This review explicitly sets the action's `packages` input to `platform-tools`, overriding its obsolete default. The next push will trigger the JDK 17 GitHub Actions workflow; its result still needs to be checked after the push.

## Functional scope and gaps

- The current MediaStore data source queries `MediaStore.Images` only. The app does not index videos, and the manifest does not request `READ_MEDIA_VIDEO`.
- The People destination is a truthful placeholder. Face detection, identity embeddings, person clustering and scene/AI-set clustering are not implemented.
- The editor has local preview adjustments, but export/save is disabled and not implemented.
- Optimize currently displays indexed-photo/storage information. Exact or perceptual duplicate scanning and cleanup are not implemented.
- Cloud backup and background upload are not implemented.

These gaps mean the build is a functioning photo-gallery foundation, not completion of the requested all-media AI gallery. In particular, it does not yet meet the requirement to group images and videos across the full timeline by recurring scenes, people and couples.

## Runtime and release gates still open

- No Android emulator or attached Android device was available in the sandbox (device host reported zero free Android slots; `adb devices` was empty). UI/device workflow testing was therefore not performed.
- GitHub Actions must pass on JDK 17 after the SDK setup correction.
- Exercise full and partial media permissions and gallery behavior on supported API levels; include video fixtures when video indexing is added.
- The release APK is unsigned and must not be represented as a signed production release.
- This review did not perform a dedicated third-party dependency CVE audit.

## Recommended next implementation order

1. Add video MediaStore indexing, `READ_MEDIA_VIDEO`, mixed-media persistence and migration tests.
2. Add on-device scene embeddings separately from licensed face-identity embeddings; sample video frames and preserve the distinction between scene similarity and face identity.
3. Implement real duplicate analysis and safe review actions, then editor export.
4. Complete device/partial-access and large-library acceptance tests; add real backup only after its provider and retry behavior are implemented.
