package ai.sakana.aura.gallery.domain.usecase

import android.net.Uri
import ai.sakana.aura.gallery.domain.repository.MediaRepository
import javax.inject.Inject

class ObserveMediaUseCase @Inject constructor(private val repository: MediaRepository) {
    operator fun invoke() = repository.observeMedia()
}
class RefreshMediaStoreUseCase @Inject constructor(private val repository: MediaRepository) {
    suspend operator fun invoke() = repository.refreshMediaStore()
}
class ImportPickedMediaUseCase @Inject constructor(private val repository: MediaRepository) {
    suspend operator fun invoke(uris: List<Uri>) = repository.importPickedMedia(uris)
}
class ToggleFavoriteUseCase @Inject constructor(private val repository: MediaRepository) {
    suspend operator fun invoke(contentUri: String) = repository.toggleFavorite(contentUri)
}

class ObserveTrashedMediaUseCase @Inject constructor(private val repository: MediaRepository) {
    operator fun invoke() = repository.observeTrashedMedia()
}
class MoveToTrashUseCase @Inject constructor(private val repository: MediaRepository) {
    suspend operator fun invoke(contentUri: String) = repository.moveToTrash(contentUri)
}
class RestoreFromTrashUseCase @Inject constructor(private val repository: MediaRepository) {
    suspend operator fun invoke(contentUri: String) = repository.restoreFromTrash(contentUri)
}
