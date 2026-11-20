package ai.sakana.aura.gallery.domain.usecase

import ai.sakana.aura.gallery.data.recognition.OnDeviceMediaAnalyzer
import ai.sakana.aura.gallery.data.recognition.VideoFrameEmbeddingResult
import ai.sakana.aura.gallery.data.recognition.VideoFrameExtractor
import ai.sakana.aura.gallery.data.recognition.analyzeVideoFramesWithDedup
import ai.sakana.aura.gallery.domain.model.MediaItem
import ai.sakana.aura.gallery.domain.recognition.DetectedFace
import ai.sakana.aura.gallery.domain.repository.PersonClusterRepository
import android.graphics.Bitmap
import javax.inject.Inject

class GetPersonFacesUseCase @Inject constructor(
    private val repository: PersonClusterRepository,
) {
    suspend operator fun invoke(personId: Long): List<DetectedFace> =
        repository.getPersonFaces(personId)
}

class RenamePersonUseCase @Inject constructor(
    private val repository: PersonClusterRepository,
) {
    suspend operator fun invoke(personId: Long, newName: String) {
        require(newName.isNotBlank()) { "Person name cannot be blank." }
        repository.renamePerson(personId, newName.trim())
    }
}

class MergePersonsUseCase @Inject constructor(
    private val repository: PersonClusterRepository,
) {
    suspend operator fun invoke(sourcePersonId: Long, targetPersonId: Long) {
        if (sourcePersonId == targetPersonId) return
        repository.mergePersons(sourcePersonId, targetPersonId)
    }
}

class SplitPersonUseCase @Inject constructor(
    private val repository: PersonClusterRepository,
) {
    suspend operator fun invoke(personId: Long, faceIdToDetach: Long) {
        repository.splitPerson(personId, faceIdToDetach)
    }
}

class DeletePersonUseCase @Inject constructor(
    private val repository: PersonClusterRepository,
) {
    suspend operator fun invoke(personId: Long) {
        repository.deletePerson(personId)
    }
}

class AnalyzeVideoFramesUseCase @Inject constructor(
    private val analyzer: OnDeviceMediaAnalyzer,
    private val videoFrameExtractor: VideoFrameExtractor,
) {
    operator fun invoke(
        item: MediaItem,
        embedSceneBitmap: (Bitmap) -> FloatArray,
        allowLargeVideos: Boolean = false,
    ): List<VideoFrameEmbeddingResult> =
        analyzer.analyzeVideoFramesWithDedup(item, videoFrameExtractor, embedSceneBitmap, allowLargeVideos)
}
