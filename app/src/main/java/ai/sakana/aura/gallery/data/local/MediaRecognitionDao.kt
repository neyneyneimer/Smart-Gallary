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

}
