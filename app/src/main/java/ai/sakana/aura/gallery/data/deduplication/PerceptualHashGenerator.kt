package ai.sakana.aura.gallery.data.deduplication

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import ai.sakana.aura.gallery.data.local.MediaItemEntity
import kotlin.math.max

/** 64-bit difference hash (dHash). Pure math, no Android types, fully unit-testable. */
object DHash {
    const val WIDTH: Int = 9
    const val HEIGHT: Int = 8

    /**
     * [gray] is a row-major 9x8 luminance grid (0..255). For every row, each pixel is compared with
     * its right neighbour; the bit is 1 when the pixel is brighter. 8 rows x 8 comparisons = 64 bits.
     */
    fun compute(gray: IntArray): Long {
        require(gray.size == WIDTH * HEIGHT) { "dHash needs a ${WIDTH}x$HEIGHT grid." }
        var hash = 0L
        var bit = 0
        for (y in 0 until HEIGHT) {
            for (x in 0 until WIDTH - 1) {
                if (gray[y * WIDTH + x] > gray[y * WIDTH + x + 1]) hash = hash or (1L shl bit)
                bit++
            }
        }
        return hash
    }

    /** Solid colours and fully transparent images hash to 0 and would falsely match each other. */
    fun isDegenerate(hash: Long): Boolean = hash == 0L

    /** ARGB -> luminance 0..255, composited over black so transparent pixels carry no signal. */
    fun luminance(argb: Int): Int {
        val a = (argb ushr 24) and 0xFF
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        val lum = (299 * r + 587 * g + 114 * b) / 1000
        return lum * a / 255
    }

    /** Box-filter any-size luminance image down to the 9x8 grid. */
    fun downsample(gray: IntArray, width: Int, height: Int): IntArray {
        require(width > 0 && height > 0 && gray.size == width * height) { "Image buffer does not match its size." }
        val out = IntArray(WIDTH * HEIGHT)
        for (ty in 0 until HEIGHT) {
            val y0 = ty * height / HEIGHT
            val y1 = max(y0 + 1, (ty + 1) * height / HEIGHT)
            for (tx in 0 until WIDTH) {
                val x0 = tx * width / WIDTH
                val x1 = max(x0 + 1, (tx + 1) * width / WIDTH)
                var sum = 0L
                for (y in y0 until y1) for (x in x0 until x1) sum += gray[y * width + x]
                out[ty * WIDTH + tx] = (sum / ((y1 - y0) * (x1 - x0))).toInt()
            }
        }
        return out
    }
}

/** Produces the 9x8 luminance grid for a media item, or null when it cannot be decoded. */
fun interface GrayThumbnailSource {
    fun load(item: MediaItemEntity): IntArray?
}

class AndroidGrayThumbnailSource(private val context: Context) : GrayThumbnailSource {
    override fun load(item: MediaItemEntity): IntArray? = try {
        if (item.mediaType == "VIDEO" || item.mimeType.orEmpty().startsWith("video/")) loadVideoFrame(item) else loadImage(item)
    } catch (_: Exception) {
        null
    }

    private fun loadImage(item: MediaItemEntity): IntArray? {
        val resolver = context.contentResolver
        val uri = Uri.parse(item.contentUri)
        val bounds = resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.Options().apply { inJustDecodeBounds = true }.also { BitmapFactory.decodeStream(stream, null, it) }
        } ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply { inSampleSize = max(1, max(bounds.outWidth, bounds.outHeight) / TARGET_EDGE) }
        val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null
        return try { toGrid(bitmap) } finally { bitmap.recycle() }
    }

    private fun loadVideoFrame(item: MediaItemEntity): IntArray? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, Uri.parse(item.contentUri))
            val frame = retriever.getFrameAtTime(item.durationMillis * 1000L / 2L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: return null
            try { toGrid(frame) } finally { frame.recycle() }
        } finally {
            retriever.release()
        }
    }

    private fun toGrid(bitmap: Bitmap): IntArray {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        return DHash.downsample(IntArray(pixels.size) { DHash.luminance(pixels[it]) }, width, height)
    }

    private companion object { const val TARGET_EDGE = 256 }
}

class PerceptualHashGenerator(private val source: GrayThumbnailSource) {
    /** Returns the 64-bit dHash, or null when the item cannot be decoded. */
    fun hash(item: MediaItemEntity): Long? = source.load(item)?.let(DHash::compute)
}
