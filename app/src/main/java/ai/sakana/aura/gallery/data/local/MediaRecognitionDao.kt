package ai.sakana.aura.gallery.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaRecognitionDao {
    @Query("SELECT * FROM scene_embeddings ORDER BY contentUri")
    fun observeSceneEmbeddings(): Flow<List<SceneEmbeddingEntity>>

    @Query("SELECT * FROM scene_embeddings")
    suspend fun getSceneEmbeddings(): List<SceneEmbeddingEntity>

    @Query("SELECT * FROM scene_embeddings WHERE contentUri = :contentUri LIMIT 1")
    suspend fun getSceneEmbedding(contentUri: String): SceneEmbeddingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSceneEmbedding(embedding: SceneEmbeddingEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSceneEmbeddings(embeddings: List<SceneEmbeddingEntity>)

    @Query("SELECT * FROM detected_faces ORDER BY contentUri, frameTimeMillis, faceIndex")
    fun observeDetectedFaces(): Flow<List<DetectedFaceEntity>>

    @Query("SELECT COUNT(*) FROM detected_faces")
    fun observeDetectedFaceCount(): Flow<Int>

    @Query("DELETE FROM detected_faces WHERE contentUri = :contentUri")
    suspend fun deleteFacesForMedia(contentUri: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFaces(faces: List<DetectedFaceEntity>)


    @Query("SELECT ai_sets.id AS setId, ai_sets.name AS name, ai_sets.createdAtEpochMillis AS createdAtEpochMillis, ai_sets.updatedAtEpochMillis AS updatedAtEpochMillis, ai_set_memberships.contentUri AS contentUri FROM ai_sets LEFT JOIN ai_set_memberships ON ai_sets.id = ai_set_memberships.setId ORDER BY ai_sets.updatedAtEpochMillis DESC, ai_sets.id, ai_set_memberships.contentUri")
    fun observeAiSetMembers(): Flow<List<AiSetMemberRow>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAiSet(set: AiSetEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAiSetMembers(members: List<AiSetMembershipEntity>)

    @Query("UPDATE ai_sets SET name = :name, updatedAtEpochMillis = :updatedAt WHERE id = :setId")
    suspend fun renameAiSet(setId: String, name: String, updatedAt: Long): Int

    @Query("DELETE FROM ai_set_memberships WHERE setId = :setId AND contentUri = :contentUri")
    suspend fun removeAiSetMember(setId: String, contentUri: String): Int

    @Query("INSERT OR IGNORE INTO ai_set_memberships (setId, contentUri, addedAtEpochMillis) SELECT :targetSetId, contentUri, :addedAt FROM ai_set_memberships WHERE setId = :sourceSetId")
    suspend fun copyAiSetMembers(sourceSetId: String, targetSetId: String, addedAt: Long)

    @Query("DELETE FROM ai_sets WHERE id = :setId")
    suspend fun deleteAiSet(setId: String): Int

    @Query("SELECT COUNT(*) FROM ai_sets WHERE id = :setId")
    suspend fun countAiSet(setId: String): Int

    @Query("UPDATE ai_sets SET updatedAtEpochMillis = :updatedAt WHERE id = :setId")
    suspend fun touchAiSet(setId: String, updatedAt: Long): Int

}
