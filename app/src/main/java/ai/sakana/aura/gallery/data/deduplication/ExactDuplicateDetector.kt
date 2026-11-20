package ai.sakana.aura.gallery.data.deduplication

import ai.sakana.aura.gallery.data.local.MediaItemEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException

/** A set of byte-identical items. All members have the same size and the same SHA-256. */
data class ExactDuplicateGroup(
    val sha256: String,
    val items: List<MediaItemEntity>,
    val recommendedKeepUri: String,
) {
    val count: Int get() = items.size
    val sizeBytesEach: Long get() = items.first().sizeBytes
    /** Space reclaimed if every copy except one is removed. */
    val wastedBytes: Long get() = sizeBytesEach * (items.size - 1)
}

data class HashProgress(val done: Int, val total: Int)

data class ExactDuplicateScanResult(
    val groups: List<ExactDuplicateGroup>,
    val hashedCount: Int,
    val reusedHashCount: Int,
    /** PICKER items are content URIs without a stable file; they are never hashed. */
    val skippedPickerCount: Int,
    val unreadableUris: List<String>,
) {
    val totalWastedBytes: Long get() = groups.sumOf { it.wastedBytes }
}

/**
 * Finds byte-identical media by SHA-256, regardless of file name.
 *
 * Only MEDIA_STORE items are considered. Zero-byte files are never grouped (nothing to reclaim).
 * Byte-identical files must have equal size, so only items sharing a size with another item are
 * hashed. Largest files go first, so the biggest space savings appear earliest.
 */
class ExactDuplicateDetector(
    private val source: MediaByteSource,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    /**
     * @param knownHashes contentUri -> previously computed SHA-256. The caller must drop entries whose
     * file changed (e.g. dateModified differs), otherwise a stale hash would be trusted.
     * @param onHashComputed called for each newly computed hash so the caller can persist it.
     */
    suspend fun detect(
        items: List<MediaItemEntity>,
        knownHashes: Map<String, String> = emptyMap(),
        onHashComputed: suspend (contentUri: String, sha256: String) -> Unit = { _, _ -> },
        onProgress: (HashProgress) -> Unit = {},
    ): ExactDuplicateScanResult = withContext(ioDispatcher) {
        val mediaStore = items.filter { it.sourceType == SOURCE_MEDIA_STORE && it.isAvailable && !it.isTrashed }
        val skippedPicker = items.count { it.sourceType != SOURCE_MEDIA_STORE }
        val candidates = mediaStore
            .filter { it.sizeBytes > 0L }
            .groupBy { it.sizeBytes }
            .filterValues { it.size > 1 }
            .values
            .flatten()
            .sortedWith(compareByDescending<MediaItemEntity> { it.sizeBytes }.thenBy { it.id })

        val hashes = HashMap<String, String>(candidates.size)
        val unreadable = ArrayList<String>()
        var computed = 0
        var reused = 0
        candidates.forEachIndexed { index, item ->
            ensureActive()
            val known = knownHashes[item.contentUri]
            if (known != null) {
                hashes[item.contentUri] = known
                reused++
            } else {
                val hash = try {
                    source.open(item.contentUri)?.use { Sha256Hasher.hash(it) }
                } catch (_: IOException) {
                    null
                } catch (_: SecurityException) {
                    null
                }
                if (hash == null) {
                    unreadable += item.contentUri
                } else {
                    hashes[item.contentUri] = hash
                    computed++
                    onHashComputed(item.contentUri, hash)
                }
            }
            onProgress(HashProgress(index + 1, candidates.size))
        }

        val groups = candidates
            .filter { hashes.containsKey(it.contentUri) }
            .groupBy { hashes.getValue(it.contentUri) }
            .filterValues { it.size > 1 }
            .map { (hash, members) ->
                val ordered = members.sortedBy { it.id }
                ExactDuplicateGroup(hash, ordered, DuplicateKeepPolicy.recommend(ordered).contentUri)
            }
            .sortedWith(compareByDescending<ExactDuplicateGroup> { it.wastedBytes }.thenBy { it.sha256 })

        ExactDuplicateScanResult(groups, computed, reused, skippedPicker, unreadable)
    }

    companion object {
        const val SOURCE_MEDIA_STORE = "MEDIA_STORE"
    }
}
