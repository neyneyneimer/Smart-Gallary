package ai.sakana.aura.gallery.domain.repository

import android.net.Uri
import ai.sakana.aura.gallery.domain.model.MediaItem
import kotlinx.coroutines.flow.Flow

interface MediaRepository {
    fun observeMedia(): Flow<List<MediaItem>>
    suspend fun refreshMediaStore()
    suspend fun importPickedMedia(uris: List<Uri>)
    suspend fun toggleFavorite(contentUri: String)
}
