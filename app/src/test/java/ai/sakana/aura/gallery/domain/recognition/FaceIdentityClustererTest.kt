package ai.sakana.aura.gallery.domain.recognition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceIdentityClustererTest {
    private val clusterer = FaceIdentityClusterer(threshold = 0.45f)

    @Test
    fun `groups similar faces across different media items`() {
        val clusters = clusterer.cluster(
            listOf(
                feature(1, "photo-a", vector = floatArrayOf(1f, 0f, 0f)),
                feature(2, "photo-b", vector = floatArrayOf(0.98f, 0.18f, 0f)),
                feature(3, "photo-c", vector = floatArrayOf(0f, 1f, 0f)),
            ),
        )

        assertEquals(1, clusters.size)
        assertEquals(listOf(1L, 2L), clusters.single().faceIds)
        assertEquals(listOf("photo-a", "photo-b"), clusters.single().mediaUris)
        assertTrue(clusters.single().averageSimilarity > 0.95f)
    }

    @Test
    fun `does not merge two faces from the same media item`() {
        val clusters = clusterer.cluster(
            listOf(
                feature(1, "group-photo", vector = floatArrayOf(1f, 0f)),
                feature(2, "group-photo", vector = floatArrayOf(0.95f, 0.31f)),
                feature(3, "portrait", vector = floatArrayOf(0.999f, 0.01f)),
            ),
        )

        assertEquals(1, clusters.size)
        assertEquals(listOf(1L, 3L), clusters.single().faceIds)
        assertEquals(2, clusters.single().mediaUris.size)
    }

    @Test
    fun `does not mix model versions or vector dimensions`() {
        val clusters = clusterer.cluster(
            listOf(
                feature(1, "photo-a", "sface-v1", floatArrayOf(1f, 0f)),
                feature(2, "photo-b", "sface-v2", floatArrayOf(1f, 0f)),
                feature(3, "photo-c", "sface-v1", floatArrayOf(1f, 0f, 0f)),
            ),
        )

        assertTrue(clusters.isEmpty())
    }

    @Test
    fun `ignores invalid and zero-length feature vectors`() {
        val clusters = clusterer.cluster(
            listOf(
                feature(1, "photo-a", vector = floatArrayOf(0f, 0f)),
                feature(2, "photo-b", vector = floatArrayOf(Float.NaN, 1f)),
                feature(3, "photo-c", vector = floatArrayOf(1f, 0f)),
            ),
        )

        assertTrue(clusters.isEmpty())
    }

    @Test
    fun `large partitions use LSH and still discover highly similar faces`() {
        val embeddings = (1L..513L).map { index ->
            feature(index, "media-$index", vector = floatArrayOf(1f, 0.01f, 0.02f))
        }

        val clusters = clusterer.cluster(embeddings)

        assertEquals(1, clusters.size)
        assertEquals(513, clusters.single().mediaUris.size)
    }

    private fun feature(
        id: Long,
        uri: String,
        modelVersion: String = "sface-v1",
        vector: FloatArray,
    ) = FaceIdentityEmbedding(
        faceId = id,
        mediaUri = uri,
        modelVersion = modelVersion,
        vector = vector,
    )
}
