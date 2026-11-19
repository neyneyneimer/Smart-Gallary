package ai.sakana.aura.gallery.presentation

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.sakana.aura.gallery.domain.usecase.ImportPickedMediaUseCase
import ai.sakana.aura.gallery.domain.usecase.ObserveMediaUseCase
import ai.sakana.aura.gallery.domain.usecase.RefreshMediaStoreUseCase
import ai.sakana.aura.gallery.domain.usecase.ToggleFavoriteUseCase
import ai.sakana.aura.gallery.domain.usecase.ObserveTrashedMediaUseCase
import ai.sakana.aura.gallery.domain.usecase.MoveToTrashUseCase
import ai.sakana.aura.gallery.domain.usecase.RestoreFromTrashUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
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
) : ViewModel() {
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

    fun refresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
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
            } finally {
                _isRefreshing.value = false
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

    fun clearError() {
        _errorMessage.value = null
    }
}
