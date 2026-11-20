package ai.sakana.aura.gallery.domain.usecase

import android.content.Context
import ai.sakana.aura.gallery.data.deduplication.AndroidGrayThumbnailSource
import ai.sakana.aura.gallery.data.deduplication.HashProgress
import ai.sakana.aura.gallery.data.deduplication.PerceptualHashGenerator
import ai.sakana.aura.gallery.data.deduplication.SimilarPhotoDetector
import ai.sakana.aura.gallery.data.deduplication.SimilarScanResult
import ai.sakana.aura.gallery.data.local.MediaItemEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Finds visually similar photos/videos (dHash, Hamming <= 10). Read-only.
 * Account 1 supplies [items] and persists hashes via [onHashComputed] once perceptualHash exists.
 */
class DetectSimilarPhotosUseCase @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val detector = SimilarPhotoDetector(PerceptualHashGenerator(AndroidGrayThumbnailSource(context)))

    suspend operator fun invoke(
        items: List<MediaItemEntity>,
        knownHashes: Map<String, Long> = emptyMap(),
        onHashComputed: suspend (contentUri: String, hash: Long) -> Unit = { _, _ -> },
        onProgress: (HashProgress) -> Unit = {},
    ): SimilarScanResult = detector.detect(items, knownHashes, onHashComputed, onProgress)
}
