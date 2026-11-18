package ai.sakana.aura.gallery.presentation

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.sakana.aura.gallery.domain.usecase.ImportPickedMediaUseCase
import ai.sakana.aura.gallery.domain.usecase.ObserveMediaUseCase
import ai.sakana.aura.gallery.domain.usecase.RefreshMediaStoreUseCase
import ai.sakana.aura.gallery.domain.usecase.ToggleFavoriteUseCase
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
) : ViewModel() {
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
                refreshMediaStore()
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
                importPickedMedia(uris)
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
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Timber.e(error, "Unable to update favourite")
                _errorMessage.value = "The favourite couldn't be saved. Please try again."
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
