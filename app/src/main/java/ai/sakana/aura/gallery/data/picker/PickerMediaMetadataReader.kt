package ai.sakana.aura.gallery.data.picker

import android.content.Context
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

internal data class PickedMediaMetadata(
    val displayName: String?,
    val mimeType: String?,
    val sizeBytes: Long,
    val dateTakenEpochMillis: Long,
    val width: Int,
    val height: Int,
    val durationMillis: Long,
    val dateModifiedEpochMillis: Long,
    val bucketName: String,
    val relativePath: String,
)

/**
 * Reads metadata exposed by the selected content provider and falls back to on-device
 * EXIF/video metadata. A missing capture date remains unknown instead of being replaced
 * with the time the user imported the file.
 */
@Singleton
class PickerMediaMetadataReader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val resolver get() = context.contentResolver

    internal fun read(uri: Uri): PickedMediaMetadata {
        val mimeType = runCatching { resolver.getType(uri) }.getOrNull()
        val isVideo = mimeType.orEmpty().startsWith("video/", ignoreCase = true)
        val provider = readProviderMetadata(uri, isVideo)
        val exif = if (isVideo) null else readExifMetadata(uri)
        val video = if (isVideo && (
                provider.durationMillis == null ||
                    provider.width == null || provider.width <= 0 ||
                    provider.height == null || provider.height <= 0
                )
        ) readVideoMetadata(uri) else null
        val hasUsableExifDimensions = (exif?.width ?: 0) > 0 && (exif?.height ?: 0) > 0
        val imageBounds = if (!isVideo && (
                provider.width == null || provider.width <= 0 ||
                    provider.height == null || provider.height <= 0
                ) && !hasUsableExifDimensions
        ) readImageBounds(uri)?.let { bounds ->
            orientedImageDimensions(bounds.first, bounds.second, exif?.orientation ?: ExifInterface.ORIENTATION_NORMAL)
        } else null

        val exifDimensions = exif?.let { orientedImageDimensions(it.width, it.height, it.orientation) }
        val videoDimensions = video?.let { orientedVideoDimensions(it.width, it.height, it.rotation) }
        val width = provider.width?.takeIf { it > 0 }
            ?: (if (isVideo) videoDimensions?.first else exifDimensions?.first)
                ?.takeIf { it > 0 }
            ?: imageBounds?.first?.takeIf { it > 0 }
            ?: 0
        val height = provider.height?.takeIf { it > 0 }
            ?: (if (isVideo) videoDimensions?.second else exifDimensions?.second)
                ?.takeIf { it > 0 }
            ?: imageBounds?.second?.takeIf { it > 0 }
            ?: 0

        val relativePath = provider.relativePath?.trim('/')?.takeIf(String::isNotBlank) ?: "Picked"
        val bucketName = relativePath.substringAfterLast('/').ifBlank { "Picked" }
        val dateTaken = provider.dateTakenEpochMillis
            ?: exif?.dateTakenEpochMillis
            ?: provider.dateAddedEpochMillis
            ?: 0L

        return PickedMediaMetadata(
            displayName = provider.displayName,
            mimeType = mimeType,
            sizeBytes = provider.sizeBytes?.coerceAtLeast(0L) ?: 0L,
            dateTakenEpochMillis = dateTaken.coerceAtLeast(0L),
            width = width,
            height = height,
            durationMillis = (provider.durationMillis ?: video?.durationMillis ?: 0L).coerceAtLeast(0L),
            dateModifiedEpochMillis = provider.dateModifiedEpochMillis?.coerceAtLeast(0L) ?: 0L,
            bucketName = bucketName,
            relativePath = relativePath,
        )
    }

    private fun readProviderMetadata(uri: Uri, isVideo: Boolean): ProviderMetadata {
        val projection = buildList {
            add(OpenableColumns.DISPLAY_NAME)
            add(OpenableColumns.SIZE)
            add(MediaStore.MediaColumns.DATE_TAKEN)
            add(MediaStore.MediaColumns.DATE_ADDED)
            add(MediaStore.MediaColumns.DATE_MODIFIED)
            add(MediaStore.MediaColumns.WIDTH)
            add(MediaStore.MediaColumns.HEIGHT)
            if (Build.VERSION.SDK_INT >= 29) add(MediaStore.MediaColumns.RELATIVE_PATH)
            if (isVideo) add(MediaStore.Video.Media.DURATION)
        }.toTypedArray()

        val rich = runCatching {
            resolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) {
                    null
                } else {
                    fun long(column: String): Long? {
                        val index = cursor.getColumnIndex(column)
                        return if (index >= 0 && !cursor.isNull(index)) cursor.getLong(index).coerceAtLeast(0L) else null
                    }
                    fun int(column: String): Int? {
                        val index = cursor.getColumnIndex(column)
                        return if (index >= 0 && !cursor.isNull(index)) cursor.getInt(index).coerceAtLeast(0) else null
                    }
                    fun string(column: String): String? {
                        val index = cursor.getColumnIndex(column)
                        return if (index >= 0 && !cursor.isNull(index)) cursor.getString(index) else null
                    }

                    ProviderMetadata(
                        displayName = string(OpenableColumns.DISPLAY_NAME),
                        sizeBytes = long(OpenableColumns.SIZE),
                        dateTakenEpochMillis = long(MediaStore.MediaColumns.DATE_TAKEN)?.takeIf { it > 0L },
                        dateAddedEpochMillis = long(MediaStore.MediaColumns.DATE_ADDED)?.takeIf { it > 0L }?.times(1_000L),
                        dateModifiedEpochMillis = long(MediaStore.MediaColumns.DATE_MODIFIED)?.takeIf { it > 0L }?.times(1_000L),
                        width = int(MediaStore.MediaColumns.WIDTH),
                        height = int(MediaStore.MediaColumns.HEIGHT),
                        durationMillis = if (isVideo) long(MediaStore.Video.Media.DURATION) else null,
                        relativePath = if (Build.VERSION.SDK_INT >= 29) string(MediaStore.MediaColumns.RELATIVE_PATH) else null,
                    )
                }
            }
        }.getOrNull() ?: ProviderMetadata()

        if (rich.displayName != null && rich.sizeBytes != null) return rich
        val openable = runCatching {
            resolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null,
            )?.use { cursor ->
                if (!cursor.moveToFirst()) {
                    ProviderMetadata()
                } else {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    ProviderMetadata(
                        displayName = nameIndex.takeIf { it >= 0 }?.let(cursor::getString),
                        sizeBytes = sizeIndex.takeIf { it >= 0 && !cursor.isNull(it) }
                            ?.let(cursor::getLong)?.coerceAtLeast(0L),
                    )
                }
            }
        }.getOrNull() ?: ProviderMetadata()
        return rich.copy(
            displayName = rich.displayName ?: openable.displayName,
            sizeBytes = rich.sizeBytes ?: openable.sizeBytes,
        )
    }

    private fun readExifMetadata(uri: Uri): ExifMetadata? = runCatching {
        resolver.openInputStream(uri)?.use { input ->
            val exif = ExifInterface(input)
            val offset = exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL)
            val dateTaken = listOf(
                ExifInterface.TAG_DATETIME_ORIGINAL,
                ExifInterface.TAG_DATETIME_DIGITIZED,
                ExifInterface.TAG_DATETIME,
            ).firstNotNullOfOrNull { tag ->
                ExifCaptureDateParser.parse(exif.getAttribute(tag), offset)
            }
            ExifMetadata(
                dateTakenEpochMillis = dateTaken,
                width = exif.getAttributeInt(
                    ExifInterface.TAG_PIXEL_X_DIMENSION,
                    exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0),
                ).coerceAtLeast(0),
                height = exif.getAttributeInt(
                    ExifInterface.TAG_PIXEL_Y_DIMENSION,
                    exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0),
                ).coerceAtLeast(0),
                orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL),
            )
        }
    }.getOrNull()

    private fun readImageBounds(uri: Uri): Pair<Int, Int>? = runCatching {
        resolver.openInputStream(uri)?.use { input ->
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(input, null, options)
            if (options.outWidth > 0 && options.outHeight > 0) options.outWidth to options.outHeight else null
        }
    }.getOrNull()

    private fun readVideoMetadata(uri: Uri): VideoMetadata? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            VideoMetadata(
                durationMillis = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()?.coerceAtLeast(0L),
                width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                    ?.toIntOrNull()?.coerceAtLeast(0) ?: 0,
                height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                    ?.toIntOrNull()?.coerceAtLeast(0) ?: 0,
                rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                    ?.toIntOrNull() ?: 0,
            )
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun orientedImageDimensions(width: Int, height: Int, orientation: Int): Pair<Int, Int> =
        if (orientation in setOf(
                ExifInterface.ORIENTATION_TRANSPOSE,
                ExifInterface.ORIENTATION_ROTATE_90,
                ExifInterface.ORIENTATION_TRANSVERSE,
                ExifInterface.ORIENTATION_ROTATE_270,
            )
        ) height to width else width to height

    private fun orientedVideoDimensions(width: Int, height: Int, rotationDegrees: Int): Pair<Int, Int> =
        if (rotationDegrees % 180 != 0) height to width else width to height

    private data class ProviderMetadata(
        val displayName: String? = null,
        val sizeBytes: Long? = null,
        val dateTakenEpochMillis: Long? = null,
        val dateAddedEpochMillis: Long? = null,
        val dateModifiedEpochMillis: Long? = null,
        val width: Int? = null,
        val height: Int? = null,
        val durationMillis: Long? = null,
        val relativePath: String? = null,
    )

    private data class ExifMetadata(
        val dateTakenEpochMillis: Long?,
        val width: Int,
        val height: Int,
        val orientation: Int,
    )

    private data class VideoMetadata(
        val durationMillis: Long?,
        val width: Int,
        val height: Int,
        val rotation: Int,
    )
}

internal object ExifCaptureDateParser {
    private val formatter = DateTimeFormatter.ofPattern("uuuu:MM:dd HH:mm:ss", Locale.US)

    fun parse(
        dateTime: String?,
        offsetTime: String?,
        fallbackZone: ZoneId = ZoneId.systemDefault(),
    ): Long? {
        if (dateTime.isNullOrBlank()) return null
        val local = runCatching { LocalDateTime.parse(dateTime.trim(), formatter) }.getOrNull() ?: return null
        val offset = offsetTime?.takeIf(String::isNotBlank)
            ?.let { value -> runCatching { ZoneOffset.of(value.trim()) }.getOrNull() }
        return if (offset != null) {
            local.toInstant(offset).toEpochMilli()
        } else {
            local.atZone(fallbackZone).toInstant().toEpochMilli()
        }
    }
}
