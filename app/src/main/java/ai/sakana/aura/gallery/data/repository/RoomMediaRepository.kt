package ai.sakana.aura.gallery.data.repository

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.room.withTransaction
import ai.sakana.aura.gallery.data.local.AuraGalleryDatabase
import ai.sakana.aura.gallery.data.local.MediaItemDao
import ai.sakana.aura.gallery.data.local.MediaItemEntity
import ai.sakana.aura.gallery.data.mediastore.MediaStoreDataSource
import ai.sakana.aura.gallery.domain.model.MediaItem
import ai.sakana.aura.gallery.domain.repository.MediaRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
class RoomMediaRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AuraGalleryDatabase,
    private val dao: MediaItemDao,
    private val mediaStore: MediaStoreDataSource,
) : MediaRepository {
    override fun observeMedia(): Flow<List<MediaItem>> =
        dao.observeAvailable().map { entities -> entities.map(MediaItemEntity::toDomain) }

    override suspend fun refreshMediaStore() = withContext(Dispatchers.IO) {
        val scanned = mediaStore.readImages()
        database.withTransaction {
            val existingByUri = dao.getAllSnapshot().associateBy(MediaItemEntity::contentUri)
            dao.markMediaStoreUnavailable()
            val updated = scanned.map { fresh ->
                val existing = existingByUri[fresh.contentUri]
                fresh.copy(
                    id = existing?.id ?: 0L,
                    isFavorite = existing?.isFavorite ?: false,
                    isAvailable = true,
                )
            }
            if (updated.isNotEmpty()) dao.upsertAll(updated)
        }
    }

    override suspend fun importPickedMedia(uris: List<Uri>) = withContext(Dispatchers.IO) {
        val picked = uris
            .filter { it.scheme == ContentResolver.SCHEME_CONTENT }
            .distinctBy(Uri::toString)
            .map(::mapPickedUri)
        if (picked.isNotEmpty()) dao.insertPicked(picked)
    }

    private fun mapPickedUri(uri: Uri): MediaItemEntity {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (_: SecurityException) {
            // Some providers grant temporary read access only.
        } catch (_: UnsupportedOperationException) {
            // Not all picker providers support persistable grants.
        }
        var name: String? = null
        var size = 0L
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0) name = cursor.getString(nameIndex)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                    size = cursor.getLong(sizeIndex).coerceAtLeast(0L)
                }
            }
        }
        return MediaItemEntity(
            volumeName = "picker",
            mediaStoreId = null,
            contentUri = uri.toString(),
            displayName = name,
            mimeType = context.contentResolver.getType(uri),
            sizeBytes = size,
            dateTakenEpochMillis = System.currentTimeMillis(),
            width = 0,
            height = 0,
            sourceType = "PICKER",
            isAvailable = true,
        )
    }

    override suspend fun toggleFavorite(contentUri: String) {
        withContext(Dispatchers.IO) { dao.toggleFavorite(contentUri) }
    }
}

private fun MediaItemEntity.toDomain() = MediaItem(
    id = id,
    contentUri = contentUri,
    displayName = displayName,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    dateTakenEpochMillis = dateTakenEpochMillis,
    width = width,
    height = height,
    isFavorite = isFavorite,
)
