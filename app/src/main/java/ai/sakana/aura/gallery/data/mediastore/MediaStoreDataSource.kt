package ai.sakana.aura.gallery.data.mediastore

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import ai.sakana.aura.gallery.data.local.MediaItemEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

/**
 * Reads device-owned media without treating a failed collection query as an empty library.
 *
 * A collection is eligible for reconciliation only after its cursor was opened and fully read.
 * This distinction is important for partial permissions and MediaStore/provider failures.
 */
class MediaStoreDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun readMedia(): MediaStoreScanResult {
        val images = readCollection(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "IMAGE")
        val videos = readCollection(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "VIDEO")
        return MediaStoreScanResult(
            items = images.items + videos.items,
            completedMediaTypes = buildSet {
                if (images.completed) add("IMAGE")
                if (videos.completed) add("VIDEO")
            },
        )
    }

    private fun readCollection(collection: android.net.Uri, mediaType: String): CollectionScan {
        val bucketColumn = "bucket_display_name"
        val folderColumn = if (Build.VERSION.SDK_INT >= 29) {
            MediaStore.MediaColumns.RELATIVE_PATH
        } else {
            MediaStore.MediaColumns.DATA
        }
        val projection = buildList {
            add(MediaStore.MediaColumns._ID)
            add(MediaStore.MediaColumns.DISPLAY_NAME)
            add(MediaStore.MediaColumns.MIME_TYPE)
            add(MediaStore.MediaColumns.SIZE)
            add(MediaStore.MediaColumns.DATE_TAKEN)
            add(MediaStore.MediaColumns.DATE_ADDED)
            add(MediaStore.MediaColumns.DATE_MODIFIED)
            add(MediaStore.MediaColumns.WIDTH)
            add(MediaStore.MediaColumns.HEIGHT)
            add(bucketColumn)
            add(folderColumn)
            if (mediaType == "VIDEO") add(MediaStore.Video.Media.DURATION)
        }.toTypedArray()
        val result = mutableListOf<MediaItemEntity>()

        return try {
            val cursor = context.contentResolver.query(
                collection,
                projection,
                null,
                null,
                MediaStore.MediaColumns.DATE_ADDED + " DESC",
            ) ?: return CollectionScan(emptyList(), completed = false)

            cursor.use {
                val idIndex = it.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameIndex = it.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                val mimeIndex = it.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
                val sizeIndex = it.getColumnIndex(MediaStore.MediaColumns.SIZE)
                val takenIndex = it.getColumnIndex(MediaStore.MediaColumns.DATE_TAKEN)
                val addedIndex = it.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)
                val modifiedIndex = it.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
                val widthIndex = it.getColumnIndex(MediaStore.MediaColumns.WIDTH)
                val heightIndex = it.getColumnIndex(MediaStore.MediaColumns.HEIGHT)
                val bucketIndex = it.getColumnIndex(bucketColumn)
                val folderIndex = it.getColumnIndex(folderColumn)
                val durationIndex = if (mediaType == "VIDEO") {
                    it.getColumnIndex(MediaStore.Video.Media.DURATION)
                } else {
                    -1
                }

                while (it.moveToNext()) {
                    val mediaStoreId = it.getLong(idIndex)
                    val dateTaken = if (takenIndex >= 0 && !it.isNull(takenIndex)) it.getLong(takenIndex) else 0L
                    val dateAddedSeconds = if (addedIndex >= 0 && !it.isNull(addedIndex)) it.getLong(addedIndex) else 0L
                    val dateMillis = dateTaken.takeIf { value -> value > 0L } ?: dateAddedSeconds * 1_000L
                    val rawFolder = if (folderIndex >= 0 && !it.isNull(folderIndex)) it.getString(folderIndex).orEmpty() else ""
                    val relativePath = if (Build.VERSION.SDK_INT >= 29) {
                        rawFolder.trimStart('/')
                    } else {
                        File(rawFolder).parentFile?.path.orEmpty()
                            .substringAfter("/storage/emulated/0/", File(rawFolder).parent.orEmpty())
                            .trim('/')
                    }
                    val bucket = if (bucketIndex >= 0 && !it.isNull(bucketIndex)) it.getString(bucketIndex) else null
                    val folderName = bucket?.takeIf(String::isNotBlank)
                        ?: relativePath.trimEnd('/').substringAfterLast('/').ifBlank { "Other" }
                    val contentUri = ContentUris.withAppendedId(collection, mediaStoreId)
                    val modifiedSeconds = if (modifiedIndex >= 0 && !it.isNull(modifiedIndex)) it.getLong(modifiedIndex) else 0L

                    result += MediaItemEntity(
                        volumeName = "external",
                        mediaStoreId = mediaStoreId,
                        contentUri = contentUri.toString(),
                        displayName = nameIndex.takeIf { index -> index >= 0 }?.let(it::getString),
                        mimeType = mimeIndex.takeIf { index -> index >= 0 }?.let(it::getString),
                        sizeBytes = sizeIndex.takeIf { index -> index >= 0 && !it.isNull(index) }
                            ?.let(it::getLong)?.coerceAtLeast(0L) ?: 0L,
                        dateTakenEpochMillis = dateMillis,
                        width = widthIndex.takeIf { index -> index >= 0 && !it.isNull(index) }
                            ?.let(it::getInt)?.coerceAtLeast(0) ?: 0,
                        height = heightIndex.takeIf { index -> index >= 0 && !it.isNull(index) }
                            ?.let(it::getInt)?.coerceAtLeast(0) ?: 0,
                        sourceType = "MEDIA_STORE",
                        mediaType = mediaType,
                        bucketName = folderName,
                        relativePath = relativePath,
                        durationMillis = durationIndex.takeIf { index -> index >= 0 && !it.isNull(index) }
                            ?.let(it::getLong)?.coerceAtLeast(0L) ?: 0L,
                        dateModifiedEpochMillis = modifiedSeconds.coerceAtLeast(0L) * 1_000L,
                    )
                }
            }
            CollectionScan(result, completed = true)
        } catch (_: Exception) {
            // Keep the previous rows for this collection when access or the provider fails.
            // A successful empty cursor is different: it is a valid empty collection.
            CollectionScan(emptyList(), completed = false)
        }
    }

    private data class CollectionScan(
        val items: List<MediaItemEntity>,
        val completed: Boolean,
    )
}

/** Result of an image/video scan; only completed types may be reconciled in Room. */
internal data class MediaStoreScanResult(
    val items: List<MediaItemEntity>,
    val completedMediaTypes: Set<String>,
)
