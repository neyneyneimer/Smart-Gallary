package ai.sakana.aura.gallery.data.repository

import androidx.room.withTransaction
import ai.sakana.aura.gallery.data.local.AuraGalleryDatabase
import ai.sakana.aura.gallery.data.local.DetectedFaceEntity
import ai.sakana.aura.gallery.data.local.FaceEmbeddingEntity
import ai.sakana.aura.gallery.data.local.MediaRecognitionDao
import ai.sakana.aura.gallery.data.local.SceneEmbeddingEntity
import ai.sakana.aura.gallery.data.recognition.OnDeviceMediaAnalyzer
import ai.sakana.aura.gallery.data.recognition.SceneVectorCodec
import ai.sakana.aura.gallery.data.recognition.SFaceFaceEmbedder
import ai.sakana.aura.gallery.domain.model.MediaItem
import ai.sakana.aura.gallery.domain.recognition.DetectedFace
import ai.sakana.aura.gallery.domain.recognition.SceneCluster
import ai.sakana.aura.gallery.domain.recognition.FaceIdentityEmbedding
import ai.sakana.aura.gallery.domain.recognition.FaceIdentityClusterer
import ai.sakana.aura.gallery.domain.recognition.PersonCluster
import ai.sakana.aura.gallery.domain.recognition.SceneEmbedding
import ai.sakana.aura.gallery.domain.recognition.SceneSimilarityClusterer
import ai.sakana.aura.gallery.domain.repository.MediaRecognitionRepository
import ai.sakana.aura.gallery.domain.repository.RecognitionSummary
import dagger.Lazy
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import timber.log.Timber

@Singleton
class RoomMediaRecognitionRepository @Inject constructor(
    private val database: AuraGalleryDatabase,
    private val dao: MediaRecognitionDao,
    private val analyzer: Lazy<OnDeviceMediaAnalyzer>,
) : MediaRecognitionRepository {
    private val clusterer = SceneSimilarityClusterer()
    private val faceIdentityClusterer = FaceIdentityClusterer()

    override fun observeSceneClusters(): Flow<List<SceneCluster>> =
        dao.observeSceneEmbeddings().map { rows ->
            clusterer.cluster(rows.mapNotNull { row ->
                runCatching {
                    SceneEmbedding(
                        mediaUri = row.contentUri,
                        modelVersion = row.modelVersion,
                        vector = SceneVectorCodec.decode(row.vectorBytes, row.dimensions),
                    )
                }.onFailure { error -> Timber.w(error, "Ignoring invalid stored scene vector") }.getOrNull()
            })
        }

    override fun observeDetectedFaces(): Flow<List<DetectedFace>> =
        dao.observeDetectedFaces().map { rows -> rows.map(DetectedFaceEntity::toDomain) }

    override fun observePersonClusters(): Flow<List<PersonCluster>> =
        dao.observeFaceEmbeddings().map { rows ->
            val embeddings = rows.mapNotNull { row ->
                if (row.modelVersion != SFaceFaceEmbedder.MODEL_VERSION) return@mapNotNull null
                runCatching {
                    FaceIdentityEmbedding(
                        faceId = row.faceId,
                        mediaUri = row.contentUri,
                        modelVersion = row.modelVersion,
                        vector = SceneVectorCodec.decode(row.vectorBytes, row.dimensions),
                    )
                }.onFailure { error ->
                    Timber.w(error, "Ignoring invalid stored face feature")
                }.getOrNull()
            }
            faceIdentityClusterer.cluster(embeddings)
        }.flowOn(Dispatchers.Default)


    override suspend fun analyze(
        items: List<MediaItem>,
        onProgress: (completed: Int, total: Int) -> Unit,
    ): RecognitionSummary = withContext(Dispatchers.IO) {
        var processed = 0
        var alreadyAnalyzed = 0
        var failed = 0
        val total = items.size
        items.forEachIndexed { index, item ->
            val fingerprint = item.sourceFingerprint()
            val existing = dao.getSceneEmbedding(item.contentUri)
            if (existing != null && existing.modelVersion == OnDeviceMediaAnalyzer.MODEL_VERSION && existing.sourceFingerprint == fingerprint) {
                alreadyAnalyzed++
            } else {
                try {
                    val result = analyzer.get().analyze(item)
                    val embedding = SceneEmbeddingEntity(
                        contentUri = item.contentUri,
                        modelVersion = OnDeviceMediaAnalyzer.MODEL_VERSION,
                        sourceFingerprint = fingerprint,
                        vectorBytes = SceneVectorCodec.encode(result.sceneVector),
                        dimensions = result.sceneVector.size,
                        analyzedAtEpochMillis = System.currentTimeMillis(),
                    )
                    database.withTransaction {
                        dao.upsertSceneEmbedding(embedding)
                        dao.deleteFacesForMedia(item.contentUri)
                        if (result.detectedFaces.isNotEmpty()) {
                            val faceIds = dao.insertFaces(result.detectedFaces.map(DetectedFace::toEntity))
                            val faceEmbeddings = result.detectedFaces.zip(faceIds).mapNotNull { (face, faceId) ->
                                face.embeddingVector?.let { vector ->
                                    FaceEmbeddingEntity(
                                        detectedFaceId = faceId,
                                        modelVersion = SFaceFaceEmbedder.MODEL_VERSION,
                                        vectorBytes = SceneVectorCodec.encode(vector),
                                        dimensions = vector.size,
                                    )
                                }
                            }
                            if (faceEmbeddings.isNotEmpty()) dao.upsertFaceEmbeddings(faceEmbeddings)
                        }
                    }
                    processed++
                } catch (error: Exception) {
                    failed++
                    Timber.w(error, "On-device analysis failed for one media item")
                }
            }
            onProgress(index + 1, total)
        }
        RecognitionSummary(processed, alreadyAnalyzed, failed)
    }

    private fun MediaItem.sourceFingerprint(): String = listOf(
        contentUri,
        sizeBytes,
        dateTakenEpochMillis,
        dateModifiedEpochMillis,
        width,
        height,
        mimeType.orEmpty(),
        durationMillis,
        SFaceFaceEmbedder.MODEL_VERSION,
    ).joinToString("|")
}

private fun DetectedFace.toEntity() = DetectedFaceEntity(
    contentUri = contentUri,
    frameTimeMillis = frameTimeMillis,
    faceIndex = faceIndex,
    leftNormalized = leftNormalized,
    topNormalized = topNormalized,
    rightNormalized = rightNormalized,
    bottomNormalized = bottomNormalized,
    yawDegrees = yawDegrees,
    rollDegrees = rollDegrees,
)

private fun DetectedFaceEntity.toDomain() = DetectedFace(
    contentUri = contentUri,
    frameTimeMillis = frameTimeMillis,
    faceIndex = faceIndex,
    leftNormalized = leftNormalized,
    topNormalized = topNormalized,
    rightNormalized = rightNormalized,
    bottomNormalized = bottomNormalized,
    yawDegrees = yawDegrees,
    rollDegrees = rollDegrees,
)
