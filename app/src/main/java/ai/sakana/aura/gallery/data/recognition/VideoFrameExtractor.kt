package ai.sakana.aura.gallery.data.recognition

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import ai.sakana.aura.gallery.domain.model.MediaItem
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

data class SampledVideoFrame(
    val timestampMillis: Long,
    val bitmap224: Bitmap,
)

@Singleton
class VideoFrameExtractor @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun isEligibleForInitialAnalysis(item: MediaItem, allowLargeVideos: Boolean = false): Boolean {
        if (!item.isVideo) return false
        if (allowLargeVideos) return true
        if (item.sizeBytes > MAX_INITIAL_VIDEO_BYTES) return false
        if (item.durationMillis > MAX_INITIAL_VIDEO_DURATION_MS) return false
        return true
    }

    fun computeSampleTimestampsMillis(durationMillis: Long): List<Long> {
        if (durationMillis <= 0L) return emptyList()
        return if (durationMillis < SHORT_VIDEO_THRESHOLD_MS) {
            val count = SHORT_VIDEO_SAMPLE_COUNT
            (0 until count).map { idx ->
                val fraction = (idx + 0.5) / count.toDouble()
                (durationMillis * fraction).toLong().coerceIn(0L, durationMillis - 1L)
            }.distinct()
        } else {
            val totalSeconds = (durationMillis / 1_000L).toInt().coerceAtLeast(1)
            (0 until totalSeconds).map { sec -> sec * 1_000L }
        }
    }

    fun extractFrames(
        item: MediaItem,
        allowLargeVideos: Boolean = false,
    ): List<SampledVideoFrame> {
        if (!isEligibleForInitialAnalysis(item, allowLargeVideos)) return emptyList()
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, Uri.parse(item.contentUri))
            val durationMs = item.durationMillis.takeIf { it > 0L }
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                ?: 0L
            if (durationMs <= 0L) throw IOException("Video duration could not be read.")

            val timestamps = computeSampleTimestampsMillis(durationMs)
            return timestamps.mapNotNull { timeMs ->
                val raw = if (Build.VERSION.SDK_INT >= 27) {
                    retriever.getScaledFrameAtTime(
                        timeMs * 1_000L,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                        SCENE_INPUT_SIZE,
                        SCENE_INPUT_SIZE,
                    )
                } else {
                    retriever.getFrameAtTime(
                        timeMs * 1_000L,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    )
                } ?: return@mapNotNull null

                val resized = if (raw.width == SCENE_INPUT_SIZE && raw.height == SCENE_INPUT_SIZE) {
                    raw
                } else {
                    Bitmap.createScaledBitmap(raw, SCENE_INPUT_SIZE, SCENE_INPUT_SIZE, true)
                        .also { if (it !== raw) raw.recycle() }
                }
                SampledVideoFrame(timestampMillis = timeMs, bitmap224 = resized)
            }
        } catch (error: SecurityException) {
            throw IOException("Aura no longer has access to this video.", error)
        } finally {
            runCatching { retriever.release() }
        }
    }

    companion object {
        const val SCENE_INPUT_SIZE = 224
        const val SHORT_VIDEO_THRESHOLD_MS = 10_000L
        const val SHORT_VIDEO_SAMPLE_COUNT = 5
        const val MAX_INITIAL_VIDEO_BYTES = 500L * 1024L * 1024L
        const val MAX_INITIAL_VIDEO_DURATION_MS = 10L * 60L * 1_000L
        const val TEMPORAL_DEDUP_COSINE_THRESHOLD = 0.95f
    }
}
