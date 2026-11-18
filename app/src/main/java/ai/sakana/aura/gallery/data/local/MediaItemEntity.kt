package ai.sakana.aura.gallery.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "media_items",
    indices = [
        Index(value = ["contentUri"], unique = true),
        Index(value = ["dateTakenEpochMillis"]),
        Index(value = ["isFavorite", "isAvailable"]),
    ],
)
data class MediaItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val volumeName: String,
    val mediaStoreId: Long?,
    val contentUri: String,
    val displayName: String?,
    val mimeType: String?,
    val sizeBytes: Long,
    val dateTakenEpochMillis: Long,
    val width: Int,
    val height: Int,
    val isFavorite: Boolean = false,
    val sourceType: String,
    val isAvailable: Boolean = true,
)
