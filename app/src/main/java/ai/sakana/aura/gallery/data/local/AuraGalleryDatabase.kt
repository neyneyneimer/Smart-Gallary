package ai.sakana.aura.gallery.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [MediaItemEntity::class, SceneEmbeddingEntity::class, DetectedFaceEntity::class, FaceEmbeddingEntity::class, AiSetEntity::class, AiSetMembershipEntity::class],
    version = 7,
    exportSchema = true,
)
abstract class AuraGalleryDatabase : RoomDatabase() {
    abstract fun mediaItemDao(): MediaItemDao
    abstract fun mediaRecognitionDao(): MediaRecognitionDao

    companion object {
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE media_items ADD COLUMN mediaType TEXT NOT NULL DEFAULT 'IMAGE'")
                db.execSQL("ALTER TABLE media_items ADD COLUMN bucketName TEXT NOT NULL DEFAULT 'Other'")
                db.execSQL("ALTER TABLE media_items ADD COLUMN relativePath TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE media_items ADD COLUMN durationMillis INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE media_items ADD COLUMN isTrashed INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE media_items ADD COLUMN trashedAtEpochMillis INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_media_items_mediaType ON media_items(mediaType)")
            }
        }

        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS scene_embeddings (`contentUri` TEXT NOT NULL, `modelVersion` TEXT NOT NULL, `sourceFingerprint` TEXT NOT NULL, `vectorBytes` BLOB NOT NULL, `dimensions` INTEGER NOT NULL, `analyzedAtEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`contentUri`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_scene_embeddings_modelVersion ON scene_embeddings(modelVersion)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_scene_embeddings_sourceFingerprint ON scene_embeddings(sourceFingerprint)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS detected_faces (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `contentUri` TEXT NOT NULL, `frameTimeMillis` INTEGER NOT NULL, `faceIndex` INTEGER NOT NULL, `leftNormalized` REAL NOT NULL, `topNormalized` REAL NOT NULL, `rightNormalized` REAL NOT NULL, `bottomNormalized` REAL NOT NULL, `yawDegrees` REAL NOT NULL, `rollDegrees` REAL NOT NULL)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_detected_faces_contentUri ON detected_faces(contentUri)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_detected_faces_contentUri_frameTimeMillis ON detected_faces(contentUri, frameTimeMillis)")
            }
        }

        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE media_items ADD COLUMN dateModifiedEpochMillis INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_6_7: Migration = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS face_embeddings (`detectedFaceId` INTEGER NOT NULL, `modelVersion` TEXT NOT NULL, `vectorBytes` BLOB NOT NULL, `dimensions` INTEGER NOT NULL, PRIMARY KEY(`detectedFaceId`), FOREIGN KEY(`detectedFaceId`) REFERENCES `detected_faces`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_face_embeddings_modelVersion ON face_embeddings(modelVersion)")
            }
        }

        val MIGRATION_5_6: Migration = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_media_items_sourceType_mediaType ON media_items(sourceType, mediaType)")
            }
        }

        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS ai_sets (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `createdAtEpochMillis` INTEGER NOT NULL, `updatedAtEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_ai_sets_updatedAtEpochMillis ON ai_sets(updatedAtEpochMillis)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS ai_set_memberships (`setId` TEXT NOT NULL, `contentUri` TEXT NOT NULL, `addedAtEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`setId`, `contentUri`), FOREIGN KEY(`setId`) REFERENCES `ai_sets`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_ai_set_memberships_contentUri ON ai_set_memberships(contentUri)")
            }
        }
    }
}
