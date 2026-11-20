package ai.sakana.aura.gallery.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "media_items",
    indices = [
        Index(value = ["contentUri"], unique = true),
        Index(value = ["dateTakenEpochMillis"]),
        Index(value = ["isFavorite", "isAvailable"]),
        Index(value = ["mediaType"]),
        Index(value = ["sourceType", "mediaType"]),
        Index(value = ["sha256Hash"], name = "index_media_items_sha256Hash"),
        Index(value = ["perceptualHash"], name = "index_media_items_perceptualHash"),
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
    @ColumnInfo(defaultValue = "'IMAGE'") val mediaType: String = "IMAGE",
    @ColumnInfo(defaultValue = "'Other'") val bucketName: String = "Other",
    @ColumnInfo(defaultValue = "''") val relativePath: String = "",
    @ColumnInfo(defaultValue = "0") val durationMillis: Long = 0L,
    @ColumnInfo(defaultValue = "0") val isTrashed: Boolean = false,
    @ColumnInfo(defaultValue = "0") val trashedAtEpochMillis: Long = 0L,
    @ColumnInfo(defaultValue = "0") val dateModifiedEpochMillis: Long = 0L,
    val sha256Hash: String? = null,
    val perceptualHash: Long? = null,
)
