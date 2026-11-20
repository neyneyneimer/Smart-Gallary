package ai.sakana.aura.gallery.data.deduplication

import ai.sakana.aura.gallery.data.local.MediaItemEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class SimilarScanResult(
    val groups: List<SimilarGroup>,
    val hashedCount: Int,
    val reusedHashCount: Int,
    val failedUris: List<String>,
    val degenerateCount: Int,
)

/** Computes dHash for each item (reusing cached hashes) and groups the results. Read-only. */
class SimilarPhotoDetector(
    private val generator: PerceptualHashGenerator,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    /** [knownHashes]: contentUri -> cached dHash. The caller must drop entries for modified files. */
    suspend fun detect(
        items: List<MediaItemEntity>,
        knownHashes: Map<String, Long> = emptyMap(),
        onHashComputed: suspend (contentUri: String, hash: Long) -> Unit = { _, _ -> },
        onProgress: (HashProgress) -> Unit = {},
    ): SimilarScanResult = withContext(ioDispatcher) {
        val eligible = items.filter { it.isAvailable && !it.isTrashed }
        val hashed = ArrayList<HashedMedia>(eligible.size)
        val failed = ArrayList<String>()
        var computed = 0
        var reused = 0
        eligible.forEachIndexed { index, item ->
            ensureActive()
            val known = knownHashes[item.contentUri]
            if (known != null) {
                hashed += HashedMedia(item, known)
                reused++
            } else {
                val hash = generator.hash(item)
                if (hash == null) {
                    failed += item.contentUri
                } else {
                    hashed += HashedMedia(item, hash)
                    computed++
                    onHashComputed(item.contentUri, hash)
                }
            }
            onProgress(HashProgress(index + 1, eligible.size))
        }
        ensureActive()
        SimilarScanResult(
            groups = SimilarPhotoGrouper.group(hashed),
            hashedCount = computed,
            reusedHashCount = reused,
            failedUris = failed,
            degenerateCount = hashed.count { DHash.isDegenerate(it.hash) },
        )
    }
}
