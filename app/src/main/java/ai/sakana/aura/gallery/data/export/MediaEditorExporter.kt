package ai.sakana.aura.gallery.data.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.media.ExifInterface
import android.net.Uri
import ai.sakana.aura.gallery.domain.editing.EditParams
import ai.sakana.aura.gallery.domain.model.MediaItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

/** Per-export choices. Defaults reproduce the original behaviour (JPEG 94, Pictures/Aura Edits). */
data class ExportOptions(
    val quality: Int = ExportSettings.DEFAULT_QUALITY,
    val relativePath: String = ExportLocation.DEFAULT,
    val includeEditHistory: Boolean = false,
    val presetName: String? = null,
    val params: EditParams = EditParams(),
) {
    companion object {
        fun from(settings: ExportSettings, presetName: String?, params: EditParams): ExportOptions {
            val s = settings.normalized()
            return ExportOptions(s.quality, s.relativePath, s.includeEditHistory, presetName, params)
        }
    }
}

object MediaEditorExporter {
    /** Original entry point, unchanged signature: writes Pictures/Aura Edits at JPEG quality 94. */
    fun saveToneEdit(context: Context, item: MediaItem, matrixValues: FloatArray): Uri =
        saveToneEdit(context, item, matrixValues, ExportOptions(), {}, {})

    /**
     * Writes an edited JPEG copy; the source is never modified. Atomic: the file is hidden
     * (IS_PENDING=1) until fully written, and deleted if anything fails.
     * [onProgress] receives 0..100; [onComplete] receives the final content URI.
     */
    fun saveToneEdit(
        context: Context,
        item: MediaItem,
        matrixValues: FloatArray,
        options: ExportOptions,
        onProgress: (Int) -> Unit,
        onComplete: (Uri) -> Unit,
    ): Uri {
        require(!item.isVideo) { "Video editing is not available in the photo tone editor." }
        val resolver = context.contentResolver
        val source = Uri.parse(item.contentUri)
        onProgress(0)
        val boundsStream = resolver.openInputStream(source) ?: error("The original photo could not be opened.")
        val bounds = boundsStream.use {
            BitmapFactory.Options().apply { inJustDecodeBounds = true }.also { o -> BitmapFactory.decodeStream(it, null, o) }
        }
        val sample = max(1, max(bounds.outWidth, bounds.outHeight) / 4096)
        val decodeStream = resolver.openInputStream(source) ?: error("The original photo could not be reopened.")
        val decoded = decodeStream.use { stream ->
            BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("The original photo format could not be decoded.")
        onProgress(20)

        val edited = Bitmap.createBitmap(decoded.width, decoded.height, Bitmap.Config.ARGB_8888)
        try {
            Canvas(edited).drawBitmap(
                decoded, 0f, 0f,
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                    colorFilter = ColorMatrixColorFilter(android.graphics.ColorMatrix(matrixValues))
                },
            )
            onProgress(45)
            val quality = options.quality.coerceIn(ExportSettings.MIN_QUALITY, ExportSettings.MAX_QUALITY)
            val path = ExportLocation.sanitize(options.relativePath)
            val fileName = "Aura_Edit_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".jpg"
            val handle = AtomicMediaWriter.write(
                sink = ResolverPendingSink(resolver),
                displayName = fileName,
                mimeType = "image/jpeg",
                relativePath = path,
                writer = { stream ->
                    check(edited.compress(Bitmap.CompressFormat.JPEG, quality, stream)) { "The edited copy could not be encoded." }
                },
                postProcess = { destination -> writeExif(context, source, Uri.parse(destination), options) },
                onProgress = onProgress,
            )
            val result = Uri.parse(handle)
            onComplete(result)
            return result
        } finally {
            decoded.recycle()
            edited.recycle()
        }
    }

    /** Best-effort: a failure to copy metadata must never lose the exported image itself. */
    private fun writeExif(context: Context, source: Uri, destination: Uri, options: ExportOptions) {
        try {
            val resolver = context.contentResolver
            val original = resolver.openInputStream(source)?.use { ExifInterface(it) }
            resolver.openFileDescriptor(destination, "rw")?.use { pfd ->
                val target = ExifInterface(pfd.fileDescriptor)
                if (original != null) {
                    ExifTagPolicy.copy({ tag -> original.getAttribute(tag) }, { tag, value -> target.setAttribute(tag, value) })
                }
                if (options.includeEditHistory) {
                    target.setAttribute(ExifTagPolicy.USER_COMMENT, EditMetadataJson.build(options.presetName, options.params))
                }
                target.saveAttributes()
            }
        } catch (_: Exception) {
            // Metadata is optional; the pixels are already written.
        }
    }
}
