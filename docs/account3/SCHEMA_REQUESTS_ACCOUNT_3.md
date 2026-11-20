# Schema Requests from Account 3 → Account 1

## Request 1 (needed now)
Subject: Schema Request — sha256Hash on media_items
Requested by: Account 3
Reason: Persist SHA-256 so exact-duplicate scans and cloud incremental sync do not re-read every file.
SQL:
ALTER TABLE media_items ADD COLUMN sha256Hash TEXT DEFAULT NULL;
CREATE INDEX index_media_items_sha256Hash ON media_items(sha256Hash);
Migration: v7 → v8
Files that will use this: ExactDuplicateDetector.kt, DetectExactDuplicatesUseCase.kt, GoogleDriveBackupManager.kt, MegaBackupManager.kt, TeraboxBackupManager.kt
Note: add `val sha256Hash: String? = null` to MediaItemEntity with `@ColumnInfo(defaultValue = "NULL")` and the new Index entry so the exported schema matches.

## Request 2 (Task 2 — bundle into the same v8 if possible)
Subject: Schema Request — perceptualHash on media_items
Reason: Store 64-bit dHash for similar-photo grouping.
SQL:
ALTER TABLE media_items ADD COLUMN perceptualHash INTEGER DEFAULT NULL;
CREATE INDEX index_media_items_perceptualHash ON media_items(perceptualHash);
Migration: v7 → v8
Files: PerceptualHashGenerator.kt, DetectSimilarPhotosUseCase.kt, DuplicateReviewScreen.kt

## Request 3 (Task 3 — bundle into the same v8 if possible)
Subject: Schema Request — edit_history table
Reason: Persist non-destructive edit presets.
SQL:
CREATE TABLE edit_history (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, contentUri TEXT NOT NULL, name TEXT NOT NULL, brightness REAL NOT NULL DEFAULT 0, contrast REAL NOT NULL DEFAULT 0, saturation REAL NOT NULL DEFAULT 0, warmth REAL NOT NULL DEFAULT 0, appliedAt INTEGER NOT NULL, isActive INTEGER NOT NULL DEFAULT 0, FOREIGN KEY(contentUri) REFERENCES media_items(contentUri) ON UPDATE NO ACTION ON DELETE CASCADE);
CREATE INDEX index_edit_history_contentUri ON edit_history(contentUri);
Migration: v7 → v8
Files: EditHistoryEntity.kt, EditHistoryDao.kt, MediaEditorExporter.kt
Note: media_items.contentUri already has a unique index, so the foreign key is valid. Entity must be registered in AuraGalleryDatabase with `abstract fun editHistoryDao()`.
