package ai.sakana.aura.gallery.data.repository

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.room.withTransaction
import ai.sakana.aura.gallery.data.local.AuraGalleryDatabase
import ai.sakana.aura.gallery.data.local.MediaItemDao
import ai.sakana.aura.gallery.data.local.MediaItemEntity
import ai.sakana.aura.gallery.data.local.MediaItemRefreshState
import ai.sakana.aura.gallery.data.mediastore.MediaStoreDataSource
import ai.sakana.aura.gallery.data.mediastore.MediaStoreReconciliationPolicy
import ai.sakana.aura.gallery.data.picker.PickerMediaMetadataReader
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
    private val pickerMetadataReader: PickerMediaMetadataReader,
) : MediaRepository {
    override fun observeMedia(): Flow<List<MediaItem>> =
        dao.observeAvailable().map { entities -> entities.map(MediaItemEntity::toDomain) }

    override suspend fun refreshMediaStore() = withContext(Dispatchers.IO) {
        val plan = MediaStoreReconciliationPolicy.createPlan(mediaStore.readMedia())
        database.withTransaction {
            val existingByUri = dao.getRefreshStateSnapshot().associateBy(MediaItemRefreshState::contentUri)
            for (mediaType in plan.mediaTypesToReconcile) {
                dao.markMediaStoreUnavailable(mediaType)
            }
            val updated = plan.itemsToUpsert.map { fresh ->
                val existing = existingByUri[fresh.contentUri]
                fresh.copy(
                    id = existing?.id ?: 0L,
                    isFavorite = existing?.isFavorite ?: false,
                    isAvailable = true,
                    isTrashed = existing?.isTrashed ?: false,
                    trashedAtEpochMillis = existing?.trashedAtEpochMillis ?: 0L,
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
        val metadata = pickerMetadataReader.read(uri)
        val mediaType = if (metadata.mimeType.orEmpty().startsWith("video/")) "VIDEO" else "IMAGE"
        return MediaItemEntity(
            volumeName = "picker",
            mediaStoreId = null,
            contentUri = uri.toString(),
            displayName = metadata.displayName,
            mimeType = metadata.mimeType,
            sizeBytes = metadata.sizeBytes,
            dateTakenEpochMillis = metadata.dateTakenEpochMillis,
            width = metadata.width,
            height = metadata.height,
            sourceType = "PICKER",
            isAvailable = true,
            mediaType = mediaType,
            bucketName = metadata.bucketName,
            relativePath = metadata.relativePath,
            durationMillis = metadata.durationMillis,
            dateModifiedEpochMillis = metadata.dateModifiedEpochMillis,
        )
    }

    override suspend fun toggleFavorite(contentUri: String) {
        withContext(Dispatchers.IO) { dao.toggleFavorite(contentUri) }
    }

    override fun observeTrashedMedia(): Flow<List<MediaItem>> =
        dao.observeTrashed().map { entities -> entities.map(MediaItemEntity::toDomain) }

    override suspend fun moveToTrash(contentUri: String) {
        withContext(Dispatchers.IO) { dao.moveToTrash(contentUri, System.currentTimeMillis()) }
    }

    override suspend fun restoreFromTrash(contentUri: String) {
        withContext(Dispatchers.IO) { dao.restoreFromTrash(contentUri) }
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
    mediaType = mediaType,
    bucketName = bucketName,
    relativePath = relativePath,
    durationMillis = durationMillis,
    isTrashed = isTrashed,
    trashedAtEpochMillis = trashedAtEpochMillis,
    dateModifiedEpochMillis = dateModifiedEpochMillis,
)
