# Aura AI Gallery — Android

Aura AI Gallery is being rebuilt as a phone-first Android gallery. The Android app owns the on-device media catalogue; media is discovered from MediaStore or chosen by the user through Android Photo Picker.

## Current build

- Branch: android/rebuild-m1
- Package: ai.sakana.aura.gallery
- M0: Android foundation, launcher Activity, adaptive icon and CI workflow implemented; awaiting JDK 17 GitHub Actions verification
- M1: real MediaStore/Photo Picker grid, Android 14+ partial-access UI and Room-backed favourites implemented; runtime/device acceptance is still in progress

The previous AI Studio/web implementation is preserved on legacy-reference/ai-studio-web as a read-only reference. It is not part of this Android app.

## Build requirements

- JDK 17+
- Android SDK platform 36
- Gradle wrapper 8.13 (committed)
- Android Gradle Plugin 8.13.0

## Build and test

Build commands: ./gradlew --no-daemon :app:check
Unit tests: ./gradlew --no-daemon :app:testDebugUnitTest
Debug APK: ./gradlew --no-daemon :app:assembleDebug

The debug APK is written to app/build/outputs/apk/debug/app-debug.apk. The release APK is currently unsigned; see the project plan for the signing policy.\n\nRoom 2.8.5 schema processing currently uses KAPT because Room\x27s release KSP path fails under the pinned Kotlin 2.0.21 compiler. Hilt remains on KSP. Review this documented exception during toolchain upgrades.

## M1 media access

- Photo Picker is available without broad media permissions.
- Full device-image access requests the version-appropriate Android permissions.
- Android 14+ partial selection is supported and shown explicitly.
- Favourites are stored in Room and survive process restarts.
- The gallery displays content URIs from the device, not bundled sample images.

## Project documentation

- docs/PROJECT_PLAN.md
- ROADMAP.md

See the roadmap for implementation scope, acceptance gates and current status.
