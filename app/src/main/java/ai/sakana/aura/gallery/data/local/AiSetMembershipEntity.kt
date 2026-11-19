package ai.sakana.aura.gallery.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "ai_set_memberships",
    primaryKeys = ["setId", "contentUri"],
    foreignKeys = [
        ForeignKey(
            entity = AiSetEntity::class,
            parentColumns = ["id"],
            childColumns = ["setId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["contentUri"])],
)
data class AiSetMembershipEntity(
    val setId: String,
    val contentUri: String,
    val addedAtEpochMillis: Long,
)
