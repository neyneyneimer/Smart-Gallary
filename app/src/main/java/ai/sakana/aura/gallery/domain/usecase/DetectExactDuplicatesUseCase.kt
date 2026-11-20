package ai.sakana.aura.gallery.domain.usecase

import android.content.Context
import ai.sakana.aura.gallery.data.deduplication.ContentResolverByteSource
import ai.sakana.aura.gallery.data.deduplication.ExactDuplicateDetector
import ai.sakana.aura.gallery.data.deduplication.ExactDuplicateScanResult
import ai.sakana.aura.gallery.data.deduplication.HashProgress
import ai.sakana.aura.gallery.data.local.MediaItemEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Scans for byte-identical media. Read-only: it never deletes or modifies anything.
 * Account 1 wires this into GalleryViewModel and supplies [items] (one-shot list of available
 * MediaItemEntity rows) plus any persisted hashes once the sha256Hash column exists.
 */
class DetectExactDuplicatesUseCase @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val detector = ExactDuplicateDetector(ContentResolverByteSource(context.contentResolver))

    suspend operator fun invoke(
        items: List<MediaItemEntity>,
        knownHashes: Map<String, String> = emptyMap(),
        onHashComputed: suspend (contentUri: String, sha256: String) -> Unit = { _, _ -> },
        onProgress: (HashProgress) -> Unit = {},
    ): ExactDuplicateScanResult = detector.detect(items, knownHashes, onHashComputed, onProgress)
}
