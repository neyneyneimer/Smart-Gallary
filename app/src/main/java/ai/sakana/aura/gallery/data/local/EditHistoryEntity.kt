package ai.sakana.aura.gallery.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One saved, named set of tone parameters for a photo. The original file is never modified.
 * NOT yet registered in AuraGalleryDatabase: Account 1 adds it in the v7 -> v8 migration
 * (see docs/account3/SCHEMA_REQUESTS_ACCOUNT_3.md, request 3).
 */
@Entity(
    tableName = "edit_history",
    foreignKeys = [
        ForeignKey(
            entity = MediaItemEntity::class,
            parentColumns = ["contentUri"],
            childColumns = ["contentUri"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["contentUri"])],
)
data class EditHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contentUri: String,
    val name: String,
    @ColumnInfo(defaultValue = "0") val brightness: Float = 0f,
    @ColumnInfo(defaultValue = "0") val contrast: Float = 0f,
    @ColumnInfo(defaultValue = "0") val saturation: Float = 0f,
    @ColumnInfo(defaultValue = "0") val warmth: Float = 0f,
    val appliedAt: Long,
    @ColumnInfo(defaultValue = "0") val isActive: Boolean = false,
)
