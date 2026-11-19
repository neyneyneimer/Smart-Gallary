package ai.sakana.aura.gallery.data.mediastore

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import ai.sakana.aura.gallery.data.local.MediaItemEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

class MediaStoreDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun readMedia(): List<MediaItemEntity> =
        readCollection(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "IMAGE") +
            readCollection(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "VIDEO")

    private fun readCollection(collection: android.net.Uri, mediaType: String): List<MediaItemEntity> {
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
        try {
            context.contentResolver.query(
                collection,
                projection,
                null,
                null,
                MediaStore.MediaColumns.DATE_ADDED + " DESC",
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                val mimeIndex = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
                val sizeIndex = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                val takenIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_TAKEN)
                val addedIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)
                val modifiedIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
                val widthIndex = cursor.getColumnIndex(MediaStore.MediaColumns.WIDTH)
                val heightIndex = cursor.getColumnIndex(MediaStore.MediaColumns.HEIGHT)
                val bucketIndex = cursor.getColumnIndex(bucketColumn)
                val folderIndex = cursor.getColumnIndex(folderColumn)
                val durationIndex = if (mediaType == "VIDEO") cursor.getColumnIndex(MediaStore.Video.Media.DURATION) else -1
                while (cursor.moveToNext()) {
                    val mediaStoreId = cursor.getLong(idIndex)
                    val dateTaken = if (takenIndex >= 0 && !cursor.isNull(takenIndex)) cursor.getLong(takenIndex) else 0L
                    val dateAddedSeconds = if (addedIndex >= 0 && !cursor.isNull(addedIndex)) cursor.getLong(addedIndex) else 0L
                    val dateMillis = dateTaken.takeIf { it > 0L } ?: dateAddedSeconds * 1_000L
                    val rawFolder = if (folderIndex >= 0 && !cursor.isNull(folderIndex)) cursor.getString(folderIndex).orEmpty() else ""
                    val relativePath = if (Build.VERSION.SDK_INT >= 29) {
                        rawFolder.trimStart('/')
                    } else {
                        File(rawFolder).parentFile?.path.orEmpty()
                            .substringAfter("/storage/emulated/0/", File(rawFolder).parent.orEmpty())
                            .trim('/')
                    }
                    val bucket = if (bucketIndex >= 0 && !cursor.isNull(bucketIndex)) cursor.getString(bucketIndex) else null
                    val folderName = bucket?.takeIf(String::isNotBlank)
                        ?: relativePath.trimEnd('/').substringAfterLast('/').ifBlank { "Other" }
                    val contentUri = ContentUris.withAppendedId(collection, mediaStoreId)
                    val modifiedSeconds = if (modifiedIndex >= 0 && !cursor.isNull(modifiedIndex)) cursor.getLong(modifiedIndex) else 0L
                    result += MediaItemEntity(
                        volumeName = "external",
                        mediaStoreId = mediaStoreId,
                        contentUri = contentUri.toString(),
                        displayName = nameIndex.takeIf { it >= 0 }?.let(cursor::getString),
                        mimeType = mimeIndex.takeIf { it >= 0 }?.let(cursor::getString),
                        sizeBytes = sizeIndex.takeIf { it >= 0 && !cursor.isNull(it) }?.let(cursor::getLong)?.coerceAtLeast(0L) ?: 0L,
                        dateTakenEpochMillis = dateMillis,
                        width = widthIndex.takeIf { it >= 0 && !cursor.isNull(it) }?.let(cursor::getInt)?.coerceAtLeast(0) ?: 0,
                        height = heightIndex.takeIf { it >= 0 && !cursor.isNull(it) }?.let(cursor::getInt)?.coerceAtLeast(0) ?: 0,
                        sourceType = "MEDIA_STORE",
                        mediaType = mediaType,
                        bucketName = folderName,
                        relativePath = relativePath,
                        durationMillis = durationIndex.takeIf { it >= 0 && !cursor.isNull(it) }?.let(cursor::getLong)?.coerceAtLeast(0L) ?: 0L,
                        dateModifiedEpochMillis = modifiedSeconds.coerceAtLeast(0L) * 1_000L,
                    )
                }
            }
        } catch (_: SecurityException) {
            // Android can grant image and video access separately; an unreadable collection is omitted.
        } catch (_: IllegalArgumentException) {
            // Some vendor MediaStore providers omit optional columns. Retry is handled by the next refresh.
        }
        return result
    }
}
