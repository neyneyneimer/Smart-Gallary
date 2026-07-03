package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.database.PhotoEntity
import com.example.data.database.PhotoEditVersionEntity
import com.example.data.repository.PhotoRepository
import com.example.data.repository.ImportResult
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class GalleryViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val repository = PhotoRepository(application, database.photoDao())

    val allPhotos: StateFlow<List<PhotoEntity>> = repository.allItemsStateFlow()
    
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scanProgress = MutableStateFlow(0f)
    val scanProgress: StateFlow<Float> = _scanProgress.asStateFlow()

    private val _selectedPhoto = MutableStateFlow<PhotoEntity?>(null)
    val selectedPhoto: StateFlow<PhotoEntity?> = _selectedPhoto.asStateFlow()

    val selectedPhotoVersions: StateFlow<List<PhotoEditVersionEntity>> = _selectedPhoto
        .filterNotNull()
        .flatMapLatest { repository.getVersionsForPhoto(it.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncProgress = MutableStateFlow(0f)
    val syncProgress: StateFlow<Float> = _syncProgress.asStateFlow()

    private val _syncStats = MutableStateFlow(Pair(0, 0)) // current, total
    val syncStats: StateFlow<Pair<Int, Int>> = _syncStats.asStateFlow()

    private val _activeBackupCloud = MutableStateFlow("AuraCloud Drive")
    val activeBackupCloud: StateFlow<String> = _activeBackupCloud.asStateFlow()

    private val _importStatus = MutableStateFlow<ImportStatus?>(null)
    val importStatus: StateFlow<ImportStatus?> = _importStatus.asStateFlow()

    init {
        // Run prepopulate on background thread
        viewModelScope.launch {
            repository.checkAndPrepopulate()
        }
    }

    fun selectPhoto(photo: PhotoEntity?) {
        _selectedPhoto.value = photo
    }

    fun runFaceGrouping() {
        if (_isScanning.value) return
        _isScanning.value = true
        _scanProgress.value = 0f
        
        viewModelScope.launch {
            try {
                repository.runAIFaceScanner { progress ->
                    _scanProgress.value = progress
                }
            } finally {
                _isScanning.value = false
                // If a photo was selected, refresh its details from DB
                _selectedPhoto.value?.let { current ->
                    _selectedPhoto.value = repository.getPhotoById(current.id)
                }
            }
        }
    }

    fun applyFilterToSelected(filterName: String) {
        val photo = _selectedPhoto.value ?: return
        viewModelScope.launch {
            repository.applyFilter(photo, filterName)
            // Refresh selection state
            _selectedPhoto.value = repository.getPhotoById(photo.id)
        }
    }

    fun revertToVersion(version: PhotoEditVersionEntity) {
        val photo = _selectedPhoto.value ?: return
        viewModelScope.launch {
            repository.revertToVersion(photo, version)
            // Refresh selection state
            _selectedPhoto.value = repository.getPhotoById(photo.id)
        }
    }

    fun revertToOriginal() {
        val photo = _selectedPhoto.value ?: return
        viewModelScope.launch {
            repository.revertToOriginal(photo)
            // Refresh selection state
            _selectedPhoto.value = repository.getPhotoById(photo.id)
        }
    }

    fun deletePhoto(photo: PhotoEntity) {
        viewModelScope.launch {
            repository.deletePhoto(photo)
            if (_selectedPhoto.value?.id == photo.id) {
                _selectedPhoto.value = null
            }
        }
    }

    fun changeBackupCloud(cloudName: String) {
        _activeBackupCloud.value = cloudName
    }

    fun runCloudSync() {
        if (_isSyncing.value) return
        _isSyncing.value = true
        _syncProgress.value = 0f
        
        viewModelScope.launch {
            try {
                repository.syncBackup(_activeBackupCloud.value) { current, total ->
                    _syncStats.value = Pair(current, total)
                    _syncProgress.value = current.toFloat() / total.toFloat()
                }
            } finally {
                _isSyncing.value = false
                // Refresh active selection to show syncing label
                _selectedPhoto.value?.let { current ->
                    _selectedPhoto.value = repository.getPhotoById(current.id)
                }
            }
        }
    }

    fun tryImportNewPhoto(
        resName: String,
        title: String,
        sizeBytes: Long,
        isFaceOf: String? = null,
        mimeType: String = "image/jpeg",
        durationSeconds: Int = 0,
        attire: String? = null,
        environment: String? = null,
        lighting: String? = null
    ) {
        viewModelScope.launch {
            val result = repository.importPhoto(
                resName,
                title,
                sizeBytes,
                isFaceOf,
                mimeType,
                durationSeconds,
                attire,
                environment,
                lighting
            )
            _importStatus.value = when (result) {
                is ImportResult.Success -> ImportStatus.Success(result.photo)
                is ImportResult.Duplicate -> ImportStatus.DuplicatePrevented(result.existingPhoto)
            }
        }
    }

    fun clearImportStatus() {
        _importStatus.value = null
    }

    fun getDrawableResId(resName: String): Int {
        return repository.getDrawableResId(resName)
    }

    // Helper extension to map Flow to StateFlow
    private fun PhotoRepository.allItemsStateFlow(): StateFlow<List<PhotoEntity>> {
        return allPhotos.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    }
}

sealed class ImportStatus {
    data class Success(val photo: PhotoEntity) : ImportStatus()
    data class DuplicatePrevented(val duplicateOf: PhotoEntity) : ImportStatus()
}
