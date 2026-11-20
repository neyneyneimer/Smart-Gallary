package ai.sakana.aura.gallery.domain.recognition

import kotlin.math.sqrt
import kotlin.random.Random

/** A face feature row from one detected face in one local media item. */
data class FaceIdentityEmbedding(
    val faceId: Long,
    val mediaUri: String,
    val modelVersion: String,
    val vector: FloatArray,
)

/** Unconfirmed candidate: one or more face observations across different media items. */
data class PersonCluster(
    val faceIds: List<Long>,
    val mediaUris: List<String>,
    val averageSimilarity: Float,
)

/**
 * Builds review suggestions from SFace features.
 *
 * Exact cosine similarity is checked after deterministic random-hyperplane LSH candidate
 * discovery. Union operations refuse to place two different faces from the same media item
 * in one cluster. The threshold is deliberately conservative but must still be calibrated
 * against a real library before treating any suggestion as a confirmed identity.
 */
class FaceIdentityClusterer(
    private val threshold: Float = DEFAULT_THRESHOLD,
    private val bands: Int = DEFAULT_BANDS,
    private val bitsPerBand: Int = DEFAULT_BITS_PER_BAND,
) {
    init {
        require(threshold in -1f..1f)
        require(bands > 0 && bitsPerBand in 1..16)
    }

    fun cluster(embeddings: List<FaceIdentityEmbedding>): List<PersonCluster> {
        val valid = embeddings.asSequence()
            .filter { it.faceId >= 0L && it.mediaUri.isNotBlank() && it.modelVersion.isNotBlank() }
            .filter { it.vector.isNotEmpty() && it.vector.all(Float::isFinite) }
            .filter { squaredMagnitude(it.vector) > EPSILON }
            .distinctBy(FaceIdentityEmbedding::faceId)
            .sortedBy(FaceIdentityEmbedding::faceId)
            .toList()
        if (valid.size < 2) return emptyList()

        val partitions = valid.indices.groupBy { index ->
            valid[index].modelVersion to valid[index].vector.size
        }
        val edges = ArrayList<SimilarityEdge>()
        partitions.forEach { (partition, indices) ->
            if (indices.size < 2) return@forEach
            candidatePairs(valid, indices, partition.second, partition.first).sorted().forEach pair@{ key ->
                val left = (key ushr 32).toInt()
                val right = (key and 0xFFFF_FFFFL).toInt()
                val similarity = cosine(valid[left].vector, valid[right].vector) ?: return@pair
                if (similarity >= threshold) edges += SimilarityEdge(left, right, similarity, key)
            }
        }
        if (edges.isEmpty()) return emptyList()

        // Strongest edges first reduces accidental chain-bridging from marginal matches.
        val union = DisjointSet(valid)
        edges.sortedWith(compareByDescending<SimilarityEdge> { it.similarity }.thenBy { it.key })
            .forEach { union.join(it.left, it.right) }

        return valid.indices.groupBy(union::root)
            .values
            .filter { members -> members.size >= MIN_CLUSTER_MEDIA }
            .map { members ->
                val sorted = members.sortedBy { valid[it].faceId }
                val centroid = FloatArray(valid[sorted.first()].vector.size)
                sorted.forEach { member ->
                    valid[member].vector.forEachIndexed { index, value ->
                        centroid[index] += value / sorted.size
                    }
                }
                normalizeInPlace(centroid)
                val averageSimilarity = sorted.mapNotNull { member ->
                    cosine(valid[member].vector, centroid)
                }.average().takeIf { it.isFinite() }?.toFloat() ?: 0f
                PersonCluster(
                    faceIds = sorted.map { valid[it].faceId },
                    mediaUris = sorted.map { valid[it].mediaUri }.distinct().sorted(),
                    averageSimilarity = averageSimilarity,
                )
            }
            .sortedBy { it.mediaUris.firstOrNull().orEmpty() }
    }

    private fun candidatePairs(
        embeddings: List<FaceIdentityEmbedding>,
        indices: List<Int>,
        dimension: Int,
        modelVersion: String,
    ): Set<Long> {
        if (indices.size <= SMALL_PARTITION_LIMIT) {
            return buildSet {
                for (i in 0 until indices.lastIndex) {
                    for (j in i + 1 until indices.size) add(pairKey(indices[i], indices[j]))
                }
            }
        }

        val projections = projections(dimension, modelVersion)
        val buckets = HashMap<Long, MutableList<Int>>()
        indices.forEach { index ->
            signatures(embeddings[index].vector, projections).forEachIndexed { band, signature ->
                val key = (band.toLong() shl 32) or signature.toLong()
                buckets.getOrPut(key) { ArrayList() } += index
            }
        }
        return buildSet {
            buckets.values.forEach { bucket ->
                for (i in 0 until bucket.lastIndex) {
                    val end = minOf(bucket.size, i + MAX_NEIGHBORS_PER_BUCKET + 1)
                    for (j in i + 1 until end) add(pairKey(bucket[i], bucket[j]))
                }
            }
        }
    }

    private fun projections(dimension: Int, modelVersion: String): Array<FloatArray> {
        val seed = modelVersion.fold(0x5F41L) { acc, char -> acc * 31L + char.code }
        return Array(bands * bitsPerBand) { projection ->
            val random = Random(seed xor (projection.toLong() * GOLDEN_RATIO))
            FloatArray(dimension) { random.nextFloat() * 2f - 1f }
        }
    }

    private fun signatures(vector: FloatArray, projections: Array<FloatArray>): IntArray {
        val bits = IntArray(projections.size)
        projections.forEachIndexed { index, projection ->
            var dot = 0.0
            for (dimension in vector.indices) dot += vector[dimension] * projection[dimension]
            if (dot >= 0.0) bits[index] = 1
        }
        return IntArray(bands) { band ->
            var signature = 0
            for (bit in 0 until bitsPerBand) {
                if (bits[band * bitsPerBand + bit] == 1) signature = signature or (1 shl bit)
            }
            signature
        }
    }

    private class DisjointSet(private val embeddings: List<FaceIdentityEmbedding>) {
        private val parent = IntArray(embeddings.size) { it }
        private val rank = IntArray(embeddings.size)
        private val mediaByRoot = Array(embeddings.size) { index ->
            hashSetOf(embeddings[index].mediaUri)
        }

        fun root(value: Int): Int {
            if (parent[value] != value) parent[value] = root(parent[value])
            return parent[value]
        }

        fun join(left: Int, right: Int) {
            var leftRoot = root(left)
            var rightRoot = root(right)
            if (leftRoot == rightRoot) return
            if (mediaByRoot[leftRoot].any(mediaByRoot[rightRoot]::contains)) return
            if (rank[leftRoot] < rank[rightRoot]) {
                val swap = leftRoot
                leftRoot = rightRoot
                rightRoot = swap
            }
            parent[rightRoot] = leftRoot
            mediaByRoot[leftRoot].addAll(mediaByRoot[rightRoot])
            mediaByRoot[rightRoot].clear()
            if (rank[leftRoot] == rank[rightRoot]) rank[leftRoot]++
        }
    }

    private data class SimilarityEdge(
        val left: Int,
        val right: Int,
        val similarity: Float,
        val key: Long,
    )

    companion object {
        const val DEFAULT_THRESHOLD = 0.45f
        const val DEFAULT_BANDS = 32
        const val DEFAULT_BITS_PER_BAND = 4
        private const val SMALL_PARTITION_LIMIT = 512
        private const val MAX_NEIGHBORS_PER_BUCKET = 32
        private const val MIN_CLUSTER_MEDIA = 2
        private const val EPSILON = 1e-12
        private val GOLDEN_RATIO = 0x9E3779B97F4A7C15UL.toLong()

        fun cosine(left: FloatArray, right: FloatArray): Float? {
            if (left.isEmpty() || left.size != right.size) return null
            var dot = 0.0
            var leftNorm = 0.0
            var rightNorm = 0.0
            for (index in left.indices) {
                val a = left[index].toDouble()
                val b = right[index].toDouble()
                if (!a.isFinite() || !b.isFinite()) return null
                dot += a * b
                leftNorm += a * a
                rightNorm += b * b
            }
            if (leftNorm <= EPSILON || rightNorm <= EPSILON) return null
            return (dot / (sqrt(leftNorm) * sqrt(rightNorm))).coerceIn(-1.0, 1.0).toFloat()
        }

        private fun squaredMagnitude(vector: FloatArray): Double =
            vector.sumOf { value -> value.toDouble() * value.toDouble() }

        private fun normalizeInPlace(vector: FloatArray) {
            val magnitude = sqrt(squaredMagnitude(vector))
            if (magnitude > EPSILON) {
                vector.indices.forEach { vector[it] = (vector[it] / magnitude).toFloat() }
            }
        }

        private fun pairKey(left: Int, right: Int): Long {
            val first = minOf(left, right).toLong()
            val second = maxOf(left, right).toLong()
            return (first shl 32) or (second and 0xFFFF_FFFFL)
        }
    }
}
