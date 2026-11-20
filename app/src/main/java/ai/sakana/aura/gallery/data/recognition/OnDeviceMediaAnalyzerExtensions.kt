package ai.sakana.aura.gallery.data.recognition

import android.graphics.Bitmap
import ai.sakana.aura.gallery.domain.model.MediaItem

data class VideoFrameEmbeddingResult(
    val videoUri: String,
    val timestampMillis: Long,
    val vector: FloatArray,
)

fun OnDeviceMediaAnalyzer.analyzeVideoFramesWithDedup(
    item: MediaItem,
    videoFrameExtractor: VideoFrameExtractor,
    embedSceneBitmap: (Bitmap) -> FloatArray,
    allowLargeVideos: Boolean = false,
): List<VideoFrameEmbeddingResult> {
    val sampled = videoFrameExtractor.extractFrames(item, allowLargeVideos)
    val accepted = ArrayList<VideoFrameEmbeddingResult>()
    var previousVector: FloatArray? = null

    sampled.forEach { frame ->
        try {
            val vector = embedSceneBitmap(frame.bitmap224)
            val prev = previousVector
            val isDuplicate = prev != null && cosineDot(prev, vector) > VideoFrameExtractor.TEMPORAL_DEDUP_COSINE_THRESHOLD
            if (!isDuplicate) {
                accepted += VideoFrameEmbeddingResult(
                    videoUri = item.contentUri,
                    timestampMillis = frame.timestampMillis,
                    vector = vector,
                )
                previousVector = vector
            }
        } finally {
            frame.bitmap224.recycle()
        }
    }
    return accepted
}

internal fun SFaceFaceEmbedder.embedFacesInBatches(
    faceRequests: List<Pair<Bitmap, FaceLandmarks>>,
    batchSize: Int = 16,
): List<FloatArray?> {
    val results = ArrayList<FloatArray?>(faceRequests.size)
    faceRequests.chunked(batchSize).forEach { batch ->
        batch.forEach { (bitmap, landmarks) ->
            results += embed(bitmap, landmarks)
        }
    }
    return results
}

private fun cosineDot(a: FloatArray, b: FloatArray): Float {
    if (a.size != b.size || a.isEmpty()) return 0f
    var dot = 0.0
    for (i in a.indices) dot += a[i].toDouble() * b[i].toDouble()
    return dot.toFloat()
}
