# Aura AI Gallery — Android

Aura AI Gallery is being rebuilt as a phone-first Android gallery. The Android app owns the on-device media catalogue; media is discovered from MediaStore or chosen by the user through Android Photo Picker.

## Current build

- Branch: android/rebuild-m1
- Package: ai.sakana.aura.gallery
- M0: Android foundation, launcher Activity, adaptive icon and CI workflow implemented; awaiting JDK 17 GitHub Actions verification
- M1: real mixed photo/video MediaStore gallery, Photos/Albums viewer, trash state and Room-backed favourites implemented; runtime/device acceptance remains in progress
- M2 (in progress): on-device scene-similarity suggestions, sampled video frames, separate face-location detection and Room v4 feature persistence
- Primary purpose: group recurring visual setups into AI Sets and the same people into People groups; scene/person identity remain separate
- Face locations are not people identities. A licensed, validated face-embedding model and cross-image clustering are still required
- Cloud import/export/sync (Drive, MEGA, TeraBox and others) is optional and does not block local recognition

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

## UI design system

The Compose UI follows the supplied Aura_AI_Gallery_UI_Design_System.html:

- Graphite surfaces (#0A0A0B, #101011, #171719) and warm paper text.
- Safelight amber (#E0A24A) is reserved for user action and selection; spectral blue-violet is reserved for real model inference.
- Instrument Serif for editorial headings, Manrope for controls/body, and JetBrains Mono for counts and machine metadata.
- Square image frames, 1dp contact-sheet gutters, hairline separators and 10dp control corners.
- Five screens: Library, People, Edit, Optimize and Settings.

Instrument Serif, Manrope and JetBrains Mono are bundled under app/src/main/res/font; their OFL license texts are included in app/src/main/assets/font-licenses. Fonts do not load from the network.

The People screen shows face detections only; it does not label identities or invent people clusters. AI Sets displays on-device visual-similarity suggestions from a generic ImageNet-trained MobileNet V3 feature model. These are candidates for review, not verified same-room/same-outfit groups. Cross-image person identity requires a separately licensed model. Edit provides a local tone preview and non-destructive copy export. Optimize and Settings label unfinished features truthfully.

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
