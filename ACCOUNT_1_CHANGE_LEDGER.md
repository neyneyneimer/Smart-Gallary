# Account 1 — Change Ledger

- Repository: `samredwings/Aura-AI-Gallery`
- Working branch: `android/account-1-m1-hardening`
- Base commit: `88bac935e92a9620373917430dbeebb70acd1c4c` on `android/rebuild-m1`
- Scope: Account 1 — Gallery Core Hardening.

## Changed files

| File | Change | Cross-account note |
|---|---|---|
| `app/build.gradle.kts` | Generate versionCode from CI run number (+1000), local fallback and dynamic versionName; add a verification Gradle task. | Build configuration only. |
| `.github/workflows/android.yml` | Add explicit generated-version verification. | Shared CI workflow; coordinate future workflow edits. |
| `presentation/GalleryScreen.kt` | Retain GalleryRoute coordinator, wire refresh and screen fallbacks; detect repeated permission denial and route users to Android app settings; hide cached media and reset stale screens after all media grants are revoked. | Shared route. Existing Account 2 AI Sets/People state and use cases are preserved. |
| `presentation/LibraryScreen.kt` | Extract Library grid/filters/tiles; add nested-scroll pull-to-refresh, refreshing indicator and retry Snackbar; expose permanent-denial state. | Main Library UI ownership moves here. |
| `presentation/AlbumsScreen.kt` | Extract Albums and album detail UI; use unique stable keys for virtual albums. | Fixes Android 15 duplicate LazyGrid key crash. |
| `domain/grouping/GalleryCollectionGrouping.kt` | Give virtual albums title-based stable keys distinct from their shared source-folder paths. | Domain grouping only; no Account 2/3 ownership touched. |
| `presentation/RecentlyDeletedScreen.kt` | Extract Recently Deleted/recovery UI. | Room trash semantics unchanged. |
| `presentation/MediaViewerScreen.kt` | Extract full-screen media viewer and actions. | Extracted from GalleryScreen. |
| `presentation/PeopleScreen.kt` | Extract existing People view without editing recognition model/clustering/persistence logic. | Shared with Account 2; coordinate any further People UI edits here. |
| `presentation/AuraEditorScreen.kt` | Extract current non-destructive tone editor. | Existing editor implementation retained. |
| `presentation/OptimizeScreen.kt` | Extract Optimize UI and visibly mark unfinished duplicate/video analysis. | No Account 3 deduplication implementation touched. |
| `presentation/SettingsScreen.kt` | Extract Settings and show Drive/MEGA/TeraBox as unavailable with disabled toggles. | No cloud provider integration touched. |
| `presentation/FavouritesScreen.kt` | Add a reusable dedicated favourites surface based on existing favorite state. | Existing Library filter remains the current navigation path. |
| `presentation/FeatureFlags.kt` | Default-off flags for cloud backup, MEGA, TeraBox and video frame analysis. | Presentation gates only. |
| `presentation/ScreenErrorBoundary.kt` | Add a Compose-compatible fallback driven by ViewModel/caller error state. | Compose compiler does not support try/catch around composable calls; this is not an automatic composition exception catcher. |
| `presentation/GalleryViewModel.kt` | Preserve refresh coalescer; add SQLiteException diagnostics and scan timing/item-count metrics; collect repository-level database error state. | Account 2 AI Set APIs remain unchanged; this is a shared ViewModel. |
| `diagnostics/CrashReporter.kt` | Add no-op crash reporter integration seam. | No crash data upload. |
| `diagnostics/GalleryErrorReporter.kt` | Publish safe user-facing messages for Room SQLite failures. | Local-only diagnostics; no data upload. |
| `diagnostics/CrashRecoveryStore.kt` | Persist a minimal fatal-crash marker so next launch can show a recovery dialog. | Stores only a boolean in app-private preferences. |
| `data/repository/RoomMediaRepository.kt` | Catch SQLiteException on all Room read/write paths; keep observation flows safe and publish shared error state. | Existing schema/DAO untouched. |
| `MainActivity.kt` | Consume fatal-crash marker and show generic next-launch recovery dialog. | No automatic crash recovery of in-flight operations. |
| `platform/MediaPermissionPolicy.kt` | Add pure policy for deciding when repeated denial should open app settings and whether cached media may be displayed under current access. | API-aware permission policy only. |
| `diagnostics/PerformanceMetrics.kt` | Log cold start, scan duration, heap, scroll frame timing and peak scroll heap. | Local-only; device measurements pending. |
| `AuraGalleryApplication.kt` | Mark process start and install uncaught-exception logging/forwarding. | App lifecycle surface. |
| `MediaPermissionPolicyTest.kt` | Add API 34/36 full/partial/revoked policy tests, denial routing and revoked-cache visibility coverage. | Policy tests plus API 35 emulator smoke test; API 33/34/36 OS matrix remains open. |
| `GalleryCollectionGroupingTest.kt` | Verify virtual album stable keys remain unique when Recent and Camera share `DCIM/Camera`. | Regression test for emulator-discovered Compose crash. |
| `diagnostics/GalleryErrorReporterTest.kt` | Verify safe database failure messaging and clearing without exposing filesystem paths. | Unit test. |
| `presentation/FeatureFlagsTest.kt` | Assert unfinished feature flags default off. | New unit test. |
| `ROADMAP.md` | Add target/measurement table and keep device results explicitly unmeasured. | Documentation only. |
| `ACCOUNT_1_CHANGE_LEDGER.md` | Keep this file-level cross-account record. | Update whenever the patch adds/removes a changed file. |

## Explicitly untouched

- Account 2 recognition implementation: `data/recognition/**`, `OnDeviceMediaAnalyzer.kt`, `FaceIdentityClusterer.kt`.
- Account 3 dedup/cloud implementation: `data/deduplication/**`, `ExactDuplicateDetector.kt`, `data/cloud/**`, `GoogleDriveBackupManager.kt`.
- Room schema, DAO and migration registration: `AuraGalleryDatabase.kt`, `MediaItemDao.kt`, `DatabaseModule.kt`, and `app/schemas/**`. Baseline is Room v7; no schema change was necessary.
- `main` and `android/rebuild-m1`; all changes are on the Account 1 branch.

## Validation status

- Required CI gate: `./gradlew --no-daemon :app:check :app:testDebugUnitTest :app:assembleDebug`.
- Pixel 9 Android 15/API35 emulator smoke tests passed for limited/full/denied/revoked/re-granted media access and album navigation. API 33/34/36 runtime acceptance remains pending.
- A 1,001-photo benchmark was run; cold start was 2,025 ms (25 ms over target), follow-up MediaStore refresh loaded 1,001 rows in 1,063 ms, used runtime heap after scan was 14.1 MB, peak scroll heap was 9.56 MB, and sampled scroll callbacks within 16 ms were 0.0% (performance target failed).
- Never mark a performance target passed without device log measurements.
