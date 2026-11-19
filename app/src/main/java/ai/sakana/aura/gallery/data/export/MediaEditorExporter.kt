package ai.sakana.aura.gallery.data.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.ColorMatrixColorFilter
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import ai.sakana.aura.gallery.domain.model.MediaItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

object MediaEditorExporter {
    /** Writes an edited JPEG copy to Pictures/Aura Edits; the source is never modified. */
    fun saveToneEdit(context: Context, item: MediaItem, matrixValues: FloatArray): Uri {
        require(!item.isVideo) { "Video editing is not available in the photo tone editor." }
        val resolver = context.contentResolver
        val boundsStream = resolver.openInputStream(Uri.parse(item.contentUri))
            ?: error("The original photo could not be opened.")
        val bounds = boundsStream.use {
            BitmapFactory.Options().apply { inJustDecodeBounds = true }.also { options ->
                BitmapFactory.decodeStream(it, null, options)
            }
        }
        val sample = max(1, max(bounds.outWidth, bounds.outHeight) / 4096)
        val decodeStream = resolver.openInputStream(Uri.parse(item.contentUri))
            ?: error("The original photo could not be reopened.")
        val decoded = decodeStream.use { stream ->
            BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("The original photo format could not be decoded.")

        val edited = Bitmap.createBitmap(decoded.width, decoded.height, Bitmap.Config.ARGB_8888)
        Canvas(edited).drawBitmap(
            decoded,
            0f,
            0f,
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                colorFilter = ColorMatrixColorFilter(android.graphics.ColorMatrix(matrixValues))
            },
        )
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "Aura_Edit_$stamp.jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Aura Edits")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val destination = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("The edited copy could not be created in device storage.")
        try {
            val output = resolver.openOutputStream(destination, "w")
                ?: error("The edited copy could not be written.")
            output.use { stream ->
                check(edited.compress(Bitmap.CompressFormat.JPEG, 94, stream)) {
                    "The edited copy could not be encoded."
                }
            }
            if (Build.VERSION.SDK_INT >= 29) {
                resolver.update(destination, ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }, null, null)
            }
            return destination
        } catch (error: Exception) {
            resolver.delete(destination, null, null)
            throw error
        } finally {
            decoded.recycle()
            edited.recycle()
        }
    }
}
