package ai.sakana.aura.gallery.data.deduplication

import ai.sakana.aura.gallery.data.local.MediaItemEntity
import kotlin.math.abs
import kotlin.math.max

data class HashedMedia(val item: MediaItemEntity, val hash: Long)

data class SimilarMember(val item: MediaItemEntity, val distanceToKeep: Int)

data class SimilarGroup(
    val members: List<SimilarMember>,
    val recommendedKeepUri: String,
) {
    val count: Int get() = members.size
    val maxDistance: Int get() = members.maxOf { it.distanceToKeep }
    /** True when every member is within the "duplicate" threshold of the recommended copy. */
    val isNearExact: Boolean get() = maxDistance <= HammingDistance.DUPLICATE_MAX
}

/** Disjoint-set with path compression (iterative) and union by size. */
class DisjointSet(size: Int) {
    private val parent = IntArray(size) { it }
    private val sizes = IntArray(size) { 1 }

    fun find(x: Int): Int {
        var root = x
        while (parent[root] != root) root = parent[root]
        var node = x
        while (parent[node] != root) { val next = parent[node]; parent[node] = root; node = next }
        return root
    }

    fun union(a: Int, b: Int) {
        var ra = find(a)
        var rb = find(b)
        if (ra == rb) return
        if (sizes[ra] < sizes[rb]) { val t = ra; ra = rb; rb = t }
        parent[rb] = ra
        sizes[ra] += sizes[rb]
    }
}

/**
 * Groups perceptually similar media transitively: if A~B and A~C then A, B and C share a group even
 * when B and C are farther apart than the threshold.
 */
object SimilarPhotoGrouper {
    /** Aspect ratios must agree within 20%. Absolute size is NOT compared, so resized copies still match. */
    const val MAX_ASPECT_DIFFERENCE: Double = 0.20

    private fun isVideo(item: MediaItemEntity) = item.mediaType == "VIDEO" || item.mimeType.orEmpty().startsWith("video/")

    fun canCompare(a: MediaItemEntity, b: MediaItemEntity): Boolean {
        if (isVideo(a) != isVideo(b)) return false
        if (a.width <= 0 || a.height <= 0 || b.width <= 0 || b.height <= 0) return false
        val ra = a.width.toDouble() / a.height
        val rb = b.width.toDouble() / b.height
        return abs(ra - rb) / max(ra, rb) <= MAX_ASPECT_DIFFERENCE
    }

    fun group(hashed: List<HashedMedia>, threshold: Int = HammingDistance.SIMILAR_MAX): List<SimilarGroup> {
        val usable = hashed.filterNot { DHash.isDegenerate(it.hash) }
        val sets = DisjointSet(usable.size)
        // Images and videos are partitioned up front so cross-type pairs are never compared.
        val partitions = usable.indices.groupBy { isVideo(usable[it].item) }.values
        for (indices in partitions) {
            for (i in indices.indices) {
                for (j in i + 1 until indices.size) {
                    val a = usable[indices[i]]
                    val b = usable[indices[j]]
                    if (HammingDistance.distance(a.hash, b.hash) <= threshold && canCompare(a.item, b.item)) {
                        sets.union(indices[i], indices[j])
                    }
                }
            }
        }
        return usable.indices.groupBy { sets.find(it) }.values
            .filter { it.size > 1 }
            .map { indices ->
                val members = indices.map { usable[it] }
                val keep = DuplicateKeepPolicy.recommend(members.map { it.item })
                val keepHash = members.first { it.item.contentUri == keep.contentUri }.hash
                val ordered = members
                    .map { SimilarMember(it.item, HammingDistance.distance(it.hash, keepHash)) }
                    .sortedWith(compareBy<SimilarMember> { it.item.contentUri != keep.contentUri }.thenBy { it.distanceToKeep }.thenBy { it.item.id })
                SimilarGroup(ordered, keep.contentUri)
            }
            .sortedWith(compareByDescending<SimilarGroup> { it.count }.thenBy { it.recommendedKeepUri })
    }
}
