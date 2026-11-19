package ai.sakana.aura.gallery.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "scene_embeddings",
    indices = [Index(value = ["modelVersion"]), Index(value = ["sourceFingerprint"])],
)
data class SceneEmbeddingEntity(
    @PrimaryKey val contentUri: String,
    val modelVersion: String,
    val sourceFingerprint: String,
    val vectorBytes: ByteArray,
    val dimensions: Int,
    val analyzedAtEpochMillis: Long,
)
