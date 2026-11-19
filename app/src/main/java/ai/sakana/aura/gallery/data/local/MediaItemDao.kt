package ai.sakana.aura.gallery.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaItemDao {
    @Query("SELECT * FROM media_items WHERE isAvailable = 1 AND isTrashed = 0 ORDER BY dateTakenEpochMillis DESC, id DESC")
    fun observeAvailable(): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE isAvailable = 1 AND isTrashed = 1 ORDER BY trashedAtEpochMillis DESC, id DESC")
    fun observeTrashed(): Flow<List<MediaItemEntity>>

    @Query("SELECT id, contentUri, isFavorite, isTrashed, trashedAtEpochMillis FROM media_items")
    suspend fun getRefreshStateSnapshot(): List<MediaItemRefreshState>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<MediaItemEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPicked(items: List<MediaItemEntity>)

    @Query("UPDATE media_items SET isAvailable = 0 WHERE sourceType = 'MEDIA_STORE' AND mediaType = :mediaType")
    suspend fun markMediaStoreUnavailable(mediaType: String)

    @Query("UPDATE media_items SET isFavorite = CASE WHEN isFavorite = 1 THEN 0 ELSE 1 END WHERE contentUri = :contentUri")
    suspend fun toggleFavorite(contentUri: String)

    @Query("UPDATE media_items SET isTrashed = 1, trashedAtEpochMillis = :trashedAt WHERE contentUri = :contentUri")
    suspend fun moveToTrash(contentUri: String, trashedAt: Long)

    @Query("UPDATE media_items SET isTrashed = 0, trashedAtEpochMillis = 0 WHERE contentUri = :contentUri")
    suspend fun restoreFromTrash(contentUri: String)
}
