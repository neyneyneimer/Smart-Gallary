package ai.sakana.aura.gallery.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Private on-device biometric features associated with a detected face.
 * A feature vector is not a name or a confirmed person identity.
 */
@Entity(
    tableName = "face_embeddings",
    foreignKeys = [
        ForeignKey(
            entity = DetectedFaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["detectedFaceId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["modelVersion"])],
)
data class FaceEmbeddingEntity(
    @PrimaryKey val detectedFaceId: Long,
    val modelVersion: String,
    val vectorBytes: ByteArray,
    val dimensions: Int,
)
