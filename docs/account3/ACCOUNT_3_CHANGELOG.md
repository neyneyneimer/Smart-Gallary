# Account 3 — Change Log (Dedup, Edit History, Cloud)

Base: `android/rebuild-m1` snapshot (Room v7, package `ai.sakana.aura.gallery`). Paths below are under
`app/src/main/java/ai/sakana/aura/gallery/` unless marked TEST. Rule: every file Account 3 creates or modifies is listed here.

## Status: all 10 tasks written. NOT compiled or run (no Gradle in my sandbox). See DEPENDENCIES_FOR_ACCOUNT_1.md.

## Files MODIFIED (the only edits to existing files)
| File | Change | Compatibility |
|---|---|---|
| `data/export/MediaEditorExporter.kt` | Rewritten: atomic IS_PENDING write via AtomicMediaWriter, EXIF copy, quality/location options, progress + completion callbacks. | Original `saveToneEdit(context, item, matrixValues)` signature kept and behaves as before. |

No other file owned by Account 1 or 2 was edited. GalleryScreen.kt, the database, DAO, build files, manifest and DI modules are untouched.

## Files CREATED
**Task 1/2 dedup** (`data/deduplication/`): Sha256Hasher, MediaByteSource, DuplicateKeepPolicy, ExactDuplicateDetector,
HammingDistance, PerceptualHashGenerator (DHash + Android source), SimilarPhotoGrouper (+ DisjointSet), SimilarPhotoDetector.
`domain/usecase/`: DetectExactDuplicatesUseCase, DetectSimilarPhotosUseCase. `ui/dedup/DuplicateReviewScreen.kt` (two tabs).
**Task 3 edit history**: `data/local/EditHistoryEntity.kt`, `data/local/EditHistoryDao.kt` (NOT yet registered in the DB),
`domain/editing/EditHistoryRules.kt`, `ui/edit/EditHistoryPanel.kt`.
**Task 4 export**: `data/export/` AtomicMediaWriter, ExifTagPolicy, EditMetadataJson, ExportSettings, `ui/edit/ExportProgressSheet.kt`,
`ui/settings/ExportSettingsSection.kt`.
**Tasks 5-10 cloud** (`data/cloud/`): CloudModels, MiniJson, BackupManifestJson, CloudApi (+LocalMediaGateway), RetryPolicy,
CloudConflictResolver, CloudSyncEngine, CloudRepository (+state stores, EngineBackedCloudRepository), GoogleDriveBackupManager,
MegaBackupManager, TeraboxBackupManager (+token manager), CloudProviderFactory (+CloudSettings), BackupPolicy, DriveRestApi,
MediaStoreLocalGateway. `domain/usecase/CloudUseCases.kt` (Sync, Import, ResolveConflict). `ui/settings/CloudSettingsSection.kt`,
`ui/cloud/ConflictResolutionSheet.kt`.
**TEST** (`app/src/test/.../`): ExactDuplicateDetectorTest, PerceptualHashGeneratorTest, SimilarPhotoGrouperTest, EditHistoryRulesTest,
MediaEditorExporterTest, CloudSyncEngineTest, CloudManagersTest, CloudTestSupport (fakes).
**Outside src**: `account3-pending/BackupWorker.kt`, `DedupWorker.kt` (need WorkManager + hilt-work), `docs/account3/*`.

## Deviations from the assignment sheet
- AuraEditorScreen / SettingsScreen are private inside GalleryScreen.kt, so their Task 3/4/8 UI ships as standalone composables.
- No MEGA or TeraBox SDK adapter (SDK/API access missing); their managers, tests and factory handling are complete.
- Google Drive adapter is plain REST (`DriveRestApi`), no Google client library; only the sign-in token provider is missing.
- Details and the semantic choices needing your decision: DEPENDENCIES_FOR_ACCOUNT_1.md section F.
