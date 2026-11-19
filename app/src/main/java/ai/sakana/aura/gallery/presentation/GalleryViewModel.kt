package ai.sakana.aura.gallery.presentation

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.sakana.aura.gallery.domain.model.MediaItem
import ai.sakana.aura.gallery.domain.usecase.ImportPickedMediaUseCase
import ai.sakana.aura.gallery.domain.usecase.ObserveMediaUseCase
import ai.sakana.aura.gallery.domain.usecase.RefreshMediaStoreUseCase
import ai.sakana.aura.gallery.domain.usecase.ToggleFavoriteUseCase
import ai.sakana.aura.gallery.domain.usecase.ObserveTrashedMediaUseCase
import ai.sakana.aura.gallery.domain.usecase.MoveToTrashUseCase
import ai.sakana.aura.gallery.domain.usecase.RestoreFromTrashUseCase
import ai.sakana.aura.gallery.domain.usecase.AnalyzeMediaLibraryUseCase
import ai.sakana.aura.gallery.domain.usecase.ObserveSceneClustersUseCase
import ai.sakana.aura.gallery.domain.usecase.ObserveDetectedFacesUseCase
import ai.sakana.aura.gallery.domain.usecase.ObservePersonClustersUseCase
import ai.sakana.aura.gallery.domain.recognition.AiSet
import ai.sakana.aura.gallery.domain.usecase.ObserveAiSetsUseCase
import ai.sakana.aura.gallery.domain.usecase.SaveAiSetUseCase
import ai.sakana.aura.gallery.domain.usecase.RenameAiSetUseCase
import ai.sakana.aura.gallery.domain.usecase.MergeAiSetsUseCase
import ai.sakana.aura.gallery.domain.usecase.RemoveMediaFromAiSetUseCase
import ai.sakana.aura.gallery.domain.usecase.DeleteAiSetUseCase
import ai.sakana.aura.gallery.domain.recognition.SceneCluster
import ai.sakana.aura.gallery.domain.recognition.DetectedFace
import ai.sakana.aura.gallery.domain.recognition.PersonCluster
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel
class GalleryViewModel @Inject constructor(
    observeMedia: ObserveMediaUseCase,
    private val refreshMediaStore: RefreshMediaStoreUseCase,
    private val importPickedMedia: ImportPickedMediaUseCase,
    private val toggleFavorite: ToggleFavoriteUseCase,
    observeTrashedMedia: ObserveTrashedMediaUseCase,
    private val moveToTrash: MoveToTrashUseCase,
    private val restoreFromTrash: RestoreFromTrashUseCase,
    private val analyzeMediaLibrary: AnalyzeMediaLibraryUseCase,
    observeSceneClusters: ObserveSceneClustersUseCase,
    observeDetectedFaces: ObserveDetectedFacesUseCase,
    observePersonClusters: ObservePersonClustersUseCase,
    observeAiSets: ObserveAiSetsUseCase,
    private val saveAiSet: SaveAiSetUseCase,
    private val renameAiSet: RenameAiSetUseCase,
    private val mergeAiSets: MergeAiSetsUseCase,
    private val removeMediaFromAiSet: RemoveMediaFromAiSetUseCase,
    private val deleteAiSet: DeleteAiSetUseCase,
) : ViewModel() {
    val sceneClusters: StateFlow<List<SceneCluster>> = observeSceneClusters().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )
    val aiSets: StateFlow<List<AiSet>> = observeAiSets().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )
    val detectedFaces: StateFlow<List<DetectedFace>> = observeDetectedFaces().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    val personClusters: StateFlow<List<PersonCluster>> = observePersonClusters().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )
    val trashedItems = observeTrashedMedia().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )
    val mediaItems = observeMedia().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _recognitionProgress = MutableStateFlow(RecognitionProgress())
    val recognitionProgress: StateFlow<RecognitionProgress> = _recognitionProgress.asStateFlow()

    private val refreshRequestCoalescer = RefreshRequestCoalescer()
    private var refreshJob: Job? = null

    fun refresh() {
        if (refreshJob?.isActive == true) {
            refreshRequestCoalescer.requestAnotherPass()
            Timber.d("Gallery refresh queued until the active scan finishes")
            return
        }

        refreshRequestCoalescer.reset()
        refreshJob = viewModelScope.launch {
            _isRefreshing.value = true
            try {
                do {
                    _errorMessage.value = null
                    try {
                        Timber.i("Gallery refresh started")
                        refreshMediaStore()
                        Timber.i("Gallery refresh completed")
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        Timber.e(error, "Unable to refresh device gallery")
                        _errorMessage.value = "Aura couldn't refresh your gallery. Check access and try again."
                    }
                } while (refreshRequestCoalescer.consumeQueuedPass())
            } finally {
                _isRefreshing.value = false
                refreshJob = null
            }
        }
    }

    fun addPickedMedia(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            try {
                Timber.i("Import of %d user-selected media items started", uris.size)
                importPickedMedia(uris)
                Timber.i("User-selected media import completed")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Timber.e(error, "Unable to import selected photos")
                _errorMessage.value = "Some selected photos couldn't be added. Please try again."
            }
        }
    }

    fun toggleFavorite(contentUri: String) {
        viewModelScope.launch {
            try {
                toggleFavorite.invoke(contentUri)
                Timber.i("Favourite state updated")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Timber.e(error, "Unable to update favourite")
                _errorMessage.value = "The favourite couldn't be saved. Please try again."
            }
        }
    }

    fun moveToTrash(contentUri: String) {
        viewModelScope.launch {
            try {
                moveToTrash.invoke(contentUri)
                Timber.i("Media item moved to Aura trash")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Timber.e(error, "Unable to move media to Aura trash")
                _errorMessage.value = "This item couldn't be moved to Recently Deleted."
            }
        }
    }

    fun restoreFromTrash(contentUri: String) {
        viewModelScope.launch {
            try {
                restoreFromTrash.invoke(contentUri)
                Timber.i("Media item restored from Aura trash")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Timber.e(error, "Unable to restore media item")
                _errorMessage.value = "This item couldn't be restored."
            }
        }
    }

    fun analyzeLibrary(items: List<MediaItem>) {
        if (_recognitionProgress.value.isRunning || items.isEmpty()) return
        viewModelScope.launch {
            _recognitionProgress.value = RecognitionProgress(isRunning = true, total = items.size)
            try {
                val summary = analyzeMediaLibrary(items) { completed, total ->
                    _recognitionProgress.value = _recognitionProgress.value.copy(
                        completed = completed,
                        total = total,
                    )
                }
                _recognitionProgress.value = RecognitionProgress(
                    completed = items.size,
                    total = items.size,
                    processed = summary.processed,
                    alreadyAnalyzed = summary.alreadyAnalyzed,
                    failed = summary.failed,
                    isRunning = false,
                )
                Timber.i("On-device media analysis finished: processed=%d cached=%d failed=%d", summary.processed, summary.alreadyAnalyzed, summary.failed)
            } catch (cancelled: CancellationException) {
                _recognitionProgress.value = _recognitionProgress.value.copy(isRunning = false)
                throw cancelled
            } catch (error: Exception) {
                Timber.e(error, "On-device media analysis failed")
                _recognitionProgress.value = _recognitionProgress.value.copy(isRunning = false, error = "Analysis stopped. Check media access and try again.")
            }
        }
    }

    fun saveAiSet(name: String, mediaUris: List<String>) = launchAiSetOperation("save AI Set") {
        saveAiSet.invoke(name, mediaUris)
    }

    fun renameAiSet(setId: String, name: String) = launchAiSetOperation("rename AI Set") {
        renameAiSet.invoke(setId, name)
    }

    fun mergeAiSets(targetSetId: String, sourceSetId: String) = launchAiSetOperation("merge AI Sets") {
        mergeAiSets.invoke(targetSetId, sourceSetId)
    }

    fun removeMediaFromAiSet(setId: String, contentUri: String) = launchAiSetOperation("remove AI Set member") {
        removeMediaFromAiSet.invoke(setId, contentUri)
    }

    fun deleteAiSet(setId: String) = launchAiSetOperation("delete AI Set") {
        deleteAiSet.invoke(setId)
    }

    private fun launchAiSetOperation(label: String, operation: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                operation()
                Timber.i("AI Set operation completed: %s", label)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Timber.w(error, "AI Set operation failed: %s", label)
                _errorMessage.value = error.message ?: "The AI Set couldn't be updated."
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}

data class RecognitionProgress(
    val completed: Int = 0,
    val total: Int = 0,
    val processed: Int = 0,
    val alreadyAnalyzed: Int = 0,
    val failed: Int = 0,
    val isRunning: Boolean = false,
    val error: String? = null,
)

/** Coalesces any number of overlapping refresh requests into one follow-up scan. */
internal class RefreshRequestCoalescer {
    private var queued = false

    @Synchronized
    fun requestAnotherPass() {
        queued = true
    }

    @Synchronized
    fun consumeQueuedPass(): Boolean = queued.also { queued = false }

    @Synchronized
    fun reset() {
        queued = false
    }
}
