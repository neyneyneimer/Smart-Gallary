package ai.sakana.aura.gallery.domain.usecase

import ai.sakana.aura.gallery.domain.model.MediaItem
import ai.sakana.aura.gallery.domain.repository.MediaRecognitionRepository
import javax.inject.Inject

class ObserveSceneClustersUseCase @Inject constructor(
    private val repository: MediaRecognitionRepository,
) {
    operator fun invoke() = repository.observeSceneClusters()
}

class ObserveDetectedFacesUseCase @Inject constructor(
    private val repository: MediaRecognitionRepository,
) {
    operator fun invoke() = repository.observeDetectedFaces()
}

class ObservePersonClustersUseCase @Inject constructor(
    private val repository: MediaRecognitionRepository,
) {
    operator fun invoke() = repository.observePersonClusters()
}

class AnalyzeMediaLibraryUseCase @Inject constructor(
    private val repository: MediaRecognitionRepository,
) {
    suspend operator fun invoke(
        items: List<MediaItem>,
        onProgress: (completed: Int, total: Int) -> Unit,
    ) = repository.analyze(items, onProgress)
}
