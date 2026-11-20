package ai.sakana.aura.gallery.domain.repository

import ai.sakana.aura.gallery.domain.recognition.DetectedFace
import ai.sakana.aura.gallery.domain.recognition.PersonClusterCandidate
import kotlinx.coroutines.flow.Flow

data class RecognitionProgressState(
    val currentItemIndex: Int,
    val totalItems: Int,
    val processedCount: Int,
    val lastRunEpochMillis: Long,
    val isPaused: Boolean = false,
)

interface PersonClusterRepository {
    fun observePersonClusters(): Flow<List<PersonClusterCandidate>>
    suspend fun getPersonFaces(personId: Long): List<DetectedFace>
    suspend fun renamePerson(personId: Long, newName: String)
    suspend fun mergePersons(sourcePersonId: Long, targetPersonId: Long)
    suspend fun splitPerson(personId: Long, faceIdToDetach: Long)
    suspend fun deletePerson(personId: Long)
    suspend fun hasFaceEmbedding(detectedFaceId: Long, modelVersion: String): Boolean
    suspend fun saveRecognitionProgress(state: RecognitionProgressState)
    suspend fun getRecognitionProgress(): RecognitionProgressState?
}
