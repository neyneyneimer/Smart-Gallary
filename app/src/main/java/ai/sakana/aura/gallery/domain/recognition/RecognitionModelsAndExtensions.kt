package ai.sakana.aura.gallery.domain.recognition

import kotlin.math.sqrt

const val MIN_CLUSTER_CONFIDENCE_THRESHOLD = 0.70f

data class PersonClusterCandidate(
    val clusterId: Long = 0L,
    val name: String? = null,
    val representativeFaceId: Long,
    val faceIds: List<Long>,
    val mediaUris: List<String>,
    val averageSimilarity: Float,
    val confidence: Float = averageSimilarity.coerceIn(0f, 1f),
    val centroid: FloatArray = FloatArray(0),
)

data class AiSetSuggestionCandidate(
    val id: String,
    val memberUris: List<String>,
    val confidence: Float,
    val isPossibleMatch: Boolean = confidence < MIN_CLUSTER_CONFIDENCE_THRESHOLD,
    val containsVideo: Boolean = false,
)

fun PersonCluster.toCandidate(
    clusterId: Long = 0L,
    name: String? = null,
    representativeFaceId: Long = faceIds.firstOrNull() ?: -1L,
    centroid: FloatArray = FloatArray(0),
): PersonClusterCandidate {
    val normConf = averageSimilarity.coerceIn(0f, 1f)
    return PersonClusterCandidate(
        clusterId = clusterId,
        name = name,
        representativeFaceId = representativeFaceId,
        faceIds = faceIds,
        mediaUris = mediaUris,
        averageSimilarity = averageSimilarity,
        confidence = normConf,
        centroid = centroid,
    )
}

fun FaceIdentityClusterer.clusterIncremental(
    existingClusters: List<PersonClusterCandidate>,
    newEmbeddings: List<FaceIdentityEmbedding>,
    threshold: Float = 0.65f,
): Pair<List<PersonClusterCandidate>, List<FaceIdentityEmbedding>> {
    val updated = existingClusters.map {
        it.copy(
            faceIds = it.faceIds.toMutableList(),
            mediaUris = it.mediaUris.toMutableList(),
        )
    }.toMutableList()
    val unmatched = ArrayList<FaceIdentityEmbedding>()

    newEmbeddings.forEach { emb ->
        var bestIdx = -1
        var bestSim = threshold
        updated.forEachIndexed { idx, cluster ->
            if (cluster.centroid.size == emb.vector.size && cluster.centroid.isNotEmpty() && !cluster.mediaUris.contains(emb.mediaUri)) {
                val sim = cosineSim(emb.vector, cluster.centroid)
                if (sim >= bestSim) {
                    bestSim = sim
                    bestIdx = idx
                }
            }
        }
        if (bestIdx >= 0) {
            val target = updated[bestIdx]
            val mergedFaces = (target.faceIds + emb.faceId).distinct().sorted()
            val mergedUris = (target.mediaUris + emb.mediaUri).distinct().sorted()
            val newConf = ((target.confidence * target.faceIds.size + bestSim) / mergedFaces.size).coerceIn(0f, 1f)
            updated[bestIdx] = target.copy(
                faceIds = mergedFaces,
                mediaUris = mergedUris,
                averageSimilarity = newConf,
                confidence = newConf,
            )
        } else {
            unmatched += emb
        }
    }

    val freshClusters = cluster(unmatched).map { it.toCandidate() }
    val combined = (updated + freshClusters).filter { it.confidence >= MIN_CLUSTER_CONFIDENCE_THRESHOLD }
    return combined to unmatched
}

private fun cosineSim(a: FloatArray, b: FloatArray): Float {
    if (a.size != b.size || a.isEmpty()) return 0f
    var dot = 0.0
    var normA = 0.0
    var normB = 0.0
    for (i in a.indices) {
        val x = a[i].toDouble()
        val y = b[i].toDouble()
        dot += x * y
        normA += x * x
        normB += y * y
    }
    val denom = sqrt(normA) * sqrt(normB)
    return if (denom <= 1e-8) 0f else (dot / denom).toFloat().coerceIn(0f, 1f)
}
