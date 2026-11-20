package ai.sakana.aura.gallery.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Needs `abstract fun editHistoryDao(): EditHistoryDao` in AuraGalleryDatabase (Account 1). */
@Dao
abstract class EditHistoryDao {
    @Insert
    abstract suspend fun insert(entity: EditHistoryEntity): Long

    @Update
    abstract suspend fun update(entity: EditHistoryEntity): Int

    @Delete
    abstract suspend fun delete(entity: EditHistoryEntity): Int

    @Query("SELECT * FROM edit_history WHERE id = :id LIMIT 1")
    abstract suspend fun getById(id: Long): EditHistoryEntity?

    @Query("SELECT * FROM edit_history WHERE contentUri = :contentUri ORDER BY appliedAt DESC, id DESC")
    abstract suspend fun getAllForUri(contentUri: String): List<EditHistoryEntity>

    @Query("SELECT * FROM edit_history WHERE contentUri = :contentUri ORDER BY appliedAt DESC, id DESC")
    abstract fun observeAllForUri(contentUri: String): Flow<List<EditHistoryEntity>>

    @Query("SELECT * FROM edit_history WHERE contentUri = :contentUri AND isActive = 1 ORDER BY appliedAt DESC, id DESC LIMIT 1")
    abstract suspend fun getActiveForUri(contentUri: String): EditHistoryEntity?

    @Query("UPDATE edit_history SET isActive = 0 WHERE contentUri = :contentUri")
    abstract suspend fun clearActive(contentUri: String)

    @Query("UPDATE edit_history SET isActive = 1 WHERE id = :id")
    abstract suspend fun markActive(id: Long)

    /** Makes [id] the only active preset for its photo. */
    @Transaction
    open suspend fun setActive(id: Long) {
        val target = getById(id) ?: return
        clearActive(target.contentUri)
        markActive(id)
    }

    /** Inserts [entity] as the single active entry for its photo and returns its new id. */
    @Transaction
    open suspend fun insertActive(entity: EditHistoryEntity): Long {
        clearActive(entity.contentUri)
        return insert(entity.copy(id = 0, isActive = true))
    }
}
