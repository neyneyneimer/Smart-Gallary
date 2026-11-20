package ai.sakana.aura.gallery.domain.usecase

import ai.sakana.aura.gallery.domain.repository.AiSetRepository
import javax.inject.Inject

class ObserveAiSetsUseCase @Inject constructor(private val repository: AiSetRepository) {
    operator fun invoke() = repository.observeAiSets()
}

class SaveAiSetUseCase @Inject constructor(private val repository: AiSetRepository) {
    suspend operator fun invoke(name: String, mediaUris: List<String>) = repository.saveAiSet(name, mediaUris)
}

class RenameAiSetUseCase @Inject constructor(private val repository: AiSetRepository) {
    suspend operator fun invoke(setId: String, name: String) = repository.renameAiSet(setId, name)
}

class MergeAiSetsUseCase @Inject constructor(private val repository: AiSetRepository) {
    suspend operator fun invoke(targetSetId: String, sourceSetId: String) = repository.mergeAiSets(targetSetId, sourceSetId)
}

class RemoveMediaFromAiSetUseCase @Inject constructor(private val repository: AiSetRepository) {
    suspend operator fun invoke(setId: String, contentUri: String) = repository.removeMediaFromAiSet(setId, contentUri)
}

class DeleteAiSetUseCase @Inject constructor(private val repository: AiSetRepository) {
    suspend operator fun invoke(setId: String) = repository.deleteAiSet(setId)
}
