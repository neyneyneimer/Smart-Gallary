package ai.sakana.aura.gallery.domain.repository

import ai.sakana.aura.gallery.domain.model.MediaItem
import ai.sakana.aura.gallery.domain.recognition.DetectedFace
import ai.sakana.aura.gallery.domain.recognition.SceneCluster
import ai.sakana.aura.gallery.domain.recognition.PersonCluster
import kotlinx.coroutines.flow.Flow

data class RecognitionSummary(
    val processed: Int,
    val alreadyAnalyzed: Int,
    val failed: Int,
)

interface MediaRecognitionRepository {
    fun observeSceneClusters(): Flow<List<SceneCluster>>
    fun observeDetectedFaces(): Flow<List<DetectedFace>>
    fun observePersonClusters(): Flow<List<PersonCluster>>
    suspend fun analyze(items: List<MediaItem>, onProgress: (completed: Int, total: Int) -> Unit): RecognitionSummary
}
