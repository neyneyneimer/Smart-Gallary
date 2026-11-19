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
| `presentation/GalleryScreen.kt` | Retain GalleryRoute coordinator, wire refresh and add screen composition fallback wrappers. | Shared route. Existing Account 2 AI Sets/People state and use cases are preserved. |
| `presentation/LibraryScreen.kt` | Extract Library grid/filters/tiles; add nested-scroll pull-to-refresh, refreshing indicator and retry Snackbar. | Main Library UI ownership moves here. |
| `presentation/AlbumsScreen.kt` | Extract Albums and album detail UI. | Extracted from GalleryScreen. |
| `presentation/RecentlyDeletedScreen.kt` | Extract Recently Deleted/recovery UI. | Room trash semantics unchanged. |
| `presentation/MediaViewerScreen.kt` | Extract full-screen media viewer and actions. | Extracted from GalleryScreen. |
| `presentation/PeopleScreen.kt` | Extract existing People view without editing recognition model/clustering/persistence logic. | Shared with Account 2; coordinate any further People UI edits here. |
| `presentation/AuraEditorScreen.kt` | Extract current non-destructive tone editor. | Existing editor implementation retained. |
| `presentation/OptimizeScreen.kt` | Extract Optimize UI and visibly mark unfinished duplicate/video analysis. | No Account 3 deduplication implementation touched. |
| `presentation/SettingsScreen.kt` | Extract Settings and show Drive/MEGA/TeraBox as unavailable with disabled toggles. | No cloud provider integration touched. |
| `presentation/FavouritesScreen.kt` | Add a reusable dedicated favourites surface based on existing favorite state. | Existing Library filter remains the current navigation path. |
| `presentation/FeatureFlags.kt` | Default-off flags for cloud backup, MEGA, TeraBox and video frame analysis. | Presentation gates only. |
| `presentation/ScreenErrorBoundary.kt` | Add synchronous composable-screen fallback. | Does not claim to catch deferred layout/draw or process failures. |
| `presentation/GalleryViewModel.kt` | Preserve refresh coalescer; add SQLiteException diagnostics and scan timing/item-count metrics. | Account 2 AI Set APIs remain unchanged; this is a shared ViewModel. |
| `diagnostics/CrashReporter.kt` | Add no-op crash reporter integration seam. | No crash data upload. |
| `diagnostics/PerformanceMetrics.kt` | Log cold start, scan duration, heap, scroll frame timing and peak scroll heap. | Local-only; device measurements pending. |
| `AuraGalleryApplication.kt` | Mark process start and install uncaught-exception logging/forwarding. | App lifecycle surface. |
| `MediaPermissionPolicyTest.kt` | Add API 34/36 full/partial/revoked permission-policy unit tests. | Policy-only tests; OS/device verification remains open. |
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
- API 33/34/36 runtime permission acceptance and 1,000+ media measurements remain device/emulator gates.
- Never mark a performance target passed without device log measurements.
