package ai.sakana.aura.gallery.data.mediastore

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import ai.sakana.aura.gallery.data.local.MediaItemEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class MediaStoreDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun readImages(): List<MediaItemEntity> {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
        )
        val images = mutableListOf<MediaItemEntity>()
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            MediaStore.Images.Media.DATE_ADDED + " DESC",
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val takenColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val addedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            while (cursor.moveToNext()) {
                val mediaStoreId = cursor.getLong(idColumn)
                val dateTaken = cursor.getLong(takenColumn)
                val dateAddedSeconds = cursor.getLong(addedColumn)
                val dateMillis = dateTaken.takeIf { it > 0L } ?: (dateAddedSeconds * 1_000L)
                val uri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    mediaStoreId,
                )
                images += MediaItemEntity(
                    volumeName = "external",
                    mediaStoreId = mediaStoreId,
                    contentUri = uri.toString(),
                    displayName = cursor.getString(nameColumn),
                    mimeType = cursor.getString(mimeColumn),
                    sizeBytes = cursor.getLong(sizeColumn).coerceAtLeast(0L),
                    dateTakenEpochMillis = dateMillis,
                    width = cursor.getInt(widthColumn).coerceAtLeast(0),
                    height = cursor.getInt(heightColumn).coerceAtLeast(0),
                    sourceType = "MEDIA_STORE",
                )
            }
        }
        return images
    }
}
