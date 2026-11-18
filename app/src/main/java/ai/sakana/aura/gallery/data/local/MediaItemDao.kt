package ai.sakana.aura.gallery.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaItemDao {
    @Query("SELECT * FROM media_items WHERE isAvailable = 1 ORDER BY dateTakenEpochMillis DESC, id DESC")
    fun observeAvailable(): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items")
    suspend fun getAllSnapshot(): List<MediaItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<MediaItemEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPicked(items: List<MediaItemEntity>)

    @Query("UPDATE media_items SET isAvailable = 0 WHERE sourceType = 'MEDIA_STORE'")
    suspend fun markMediaStoreUnavailable()

    @Query("UPDATE media_items SET isFavorite = CASE WHEN isFavorite = 1 THEN 0 ELSE 1 END WHERE contentUri = :contentUri")
    suspend fun toggleFavorite(contentUri: String)
}
