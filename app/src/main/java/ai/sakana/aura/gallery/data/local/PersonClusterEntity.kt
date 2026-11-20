package ai.sakana.aura.gallery.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "person_clusters",
    indices = [Index(value = ["representativeFaceId"], name = "index_person_clusters_representative")],
)
data class PersonClusterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val representativeFaceId: Long,
    val confidence: Float,
    @ColumnInfo(defaultValue = "1") val memberCount: Int = 1,
    val createdAt: Long,
)
