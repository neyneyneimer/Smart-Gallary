package ai.sakana.aura.gallery.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Face location only; this row does not claim to identify a person. */
@Entity(
    tableName = "detected_faces",
    indices = [Index(value = ["contentUri"]), Index(value = ["contentUri", "frameTimeMillis"])],
)
data class DetectedFaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contentUri: String,
    val frameTimeMillis: Long,
    val faceIndex: Int,
    val leftNormalized: Float,
    val topNormalized: Float,
    val rightNormalized: Float,
    val bottomNormalized: Float,
    val yawDegrees: Float,
    val rollDegrees: Float,
)
