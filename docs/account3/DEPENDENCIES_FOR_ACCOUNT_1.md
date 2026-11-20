# Account 3 — Final dependency list

Everything in `app/src` below is complete as code but UNCOMPILED (no Gradle in my sandbox). Items here
are what I could not do myself. Tick them, merge all three accounts, run CI, then send me the log.

## A. Database (Account 1 owns AuraGalleryDatabase / MediaItemEntity / MediaItemDao)
1. Migration v7 -> v8 with the three requests in `SCHEMA_REQUESTS_ACCOUNT_3.md`
   (sha256Hash, perceptualHash, edit_history). Export `8.json`; `7.json` is also missing from the zip.
2. Add `val sha256Hash: String? = null` and `val perceptualHash: Long? = null` to MediaItemEntity (+ the two indexes).
3. Register `EditHistoryEntity` in `@Database(entities=...)`, add `abstract fun editHistoryDao(): EditHistoryDao`,
   and provide it in the Hilt DatabaseModule.
4. MediaItemDao: one-shot `getAvailableNotTrashed(): List<MediaItemEntity>`, plus `updateSha256(contentUri, hash)`,
   `updatePerceptualHash(contentUri, hash)`, and clear both hashes when a file's dateModified changes.
5. Implement `LocalSnapshotProvider` (MediaItemEntity -> LocalMediaSnapshot) and `HashStore` (see account3-pending).

## B. Gradle (libs.versions.toml + app/build.gradle.kts)
| Dependency | Needed for |
|---|---|
| androidx.work:work-runtime-ktx | BackupWorker, DedupWorker (account3-pending) |
| androidx.hilt:hilt-work + androidx.hilt:hilt-compiler | @HiltWorker |
| com.google.android.gms:play-services-auth | Implement `AccessTokenProvider` for Drive sign-in. DriveRestApi itself needs no Google library. |
| androidx.security:security-crypto | Encrypted storage of the MEGA session and TeraBox tokens |
| MEGA SDK (no official Maven Central artifact; needs a local AAR or NDK build) | MEGA CloudApi adapter |
| org.jetbrains.kotlinx:kotlinx-coroutines-core (explicit) | My code and tests use it; today it only arrives transitively via room-ktx |
Not needed: ExifInterface library (framework android.media.ExifInterface is used), JSON library (MiniJson), mock libraries (hand-written fakes).

## C. Manifest / Application (Account 1)
- `AuraGalleryApplication` implements `Configuration.Provider` with `HiltWorkerFactory`; disable the default WorkManager initializer.
- Permissions: `INTERNET`, `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`,
  and `ACCESS_MEDIA_LOCATION` (without it, GPS EXIF cannot be read from MediaStore on Android 10+ and is not copied on export).
- `SystemForegroundService` with `foregroundServiceType="dataSync"` (tools:node="merge").

## D. UI wiring (all in files I may not edit)
- `GalleryScreen.kt`: mount `EditHistoryPanel` and `ExportProgressSheet` in the private AuraEditorScreen; call the new
  `MediaEditorExporter.saveToneEdit(context, item, matrix, ExportOptions.from(settings, presetName, params), onProgress, onComplete)`
  (the old 3-argument call still works unchanged).
- Add routes for `DuplicateReviewScreen`; embed `ExportSettingsSection` and `CloudSettingsSection` in SettingsScreen; show
  `ConflictResolutionSheet` for `SyncReport.pendingConflicts`. Replace the "PLANNED M2" Settings label for duplicates.
- `GalleryViewModel`: hold `DuplicateReviewUiState`, call `DetectExactDuplicatesUseCase` / `DetectSimilarPhotosUseCase`,
  persist hashes through `onHashComputed`, and use the existing `MoveToTrashUseCase` for `onMoveToTrash`.
- Hilt: provide `CloudProviderFactory(map)`, `CloudSettingsStore`, `ExportSettingsStore`, `PrefsCloudSyncStateStore`,
  `MediaStoreLocalGateway(context, trash = MoveToTrashUseCase, onRestored = favorites/DB restore)`, and
  `GoogleDriveBackupManager(DriveRestApi(tokenProvider), gateway, stateStore)`.

## E. Accounts, keys and documentation only you can supply
1. Google Cloud project: OAuth client for the app's package + signing SHA-1, consent screen, Drive API enabled, `drive.file` scope.
2. MEGA: developer API key, and a decision on how the SDK is shipped.
3. TeraBox: developer/app registration and its official API documentation. I have not written a TeraBox adapter because I cannot
   verify its endpoints and will not guess. `TeraboxBackupManager` and the token manager are finished and tested; until an adapter
   is registered the factory reports TeraBox as unavailable and the UI shows "COMING SOON".

## F. Decisions for you
1. Same-name/different-content with "Keep device" overwrites the cloud copy (Drive keeps revision history). Deletions never
   propagate in either direction automatically. Say if you want the opposite.
2. Retry schedule: three retries (15, 30, 60 min), the fourth consecutive failure stops. Your sheet's "after 3 failures" is ambiguous.
3. Similar-photo pre-filter compares aspect ratio (within 20%), not pixel size, otherwise resized copies could never match.
4. Default export folder stays `Pictures/Aura Edits` (existing behaviour); `Pictures/AuraGallery` and custom are options.
   "Include edit history" defaults ON.
5. `CloudRepository.importFromCloud` instead of `import`, to avoid the Kotlin keyword.
6. Cloud conflict thumbnails: only the device side shows an image; the cloud side shows details (a thumbnail needs a download).
