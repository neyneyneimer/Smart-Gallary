package ai.sakana.aura.gallery.domain.recognition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneSimilarityClustererTest {
    @Test
    fun `groups similar scene embeddings even when capture dates are unrelated`() {
        val first = embedding("first", listOf(1f, 0f, 0f, 0f), "scene-v1")
        val related = embedding("related", listOf(0.99f, 0.1f, 0f, 0f), "scene-v1")
        val unrelated = embedding("unrelated", listOf(0f, 0f, 1f, 0f), "scene-v1")
        val result = SceneSimilarityClusterer(threshold = 0.90f).cluster(listOf(first, related, unrelated))
        assertEquals(1, result.size)
        assertEquals(setOf("first", "related"), result.single().mediaUris.toSet())
        assertTrue(result.single().averageSimilarity >= 0.90f)
    }

    @Test
    fun `does not compare embeddings created by different model versions`() {
        val result = SceneSimilarityClusterer(threshold = 0.8f).cluster(
            listOf(embedding("one", listOf(1f, 0f), "mobile-v1"), embedding("two", listOf(1f, 0f), "mobile-v2")),
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `ignores invalid and zero magnitude embeddings`() {
        val result = SceneSimilarityClusterer().cluster(
            listOf(
                embedding("empty", listOf(0f, 0f), "scene-v1"),
                embedding("nan", listOf(Float.NaN, 1f), "scene-v1"),
                embedding("valid", listOf(1f, 0f), "scene-v1"),
            ),
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `same input produces deterministic cluster ordering`() {
        val data = listOf(
            embedding("c", listOf(0f, 1f, 0f, 0f), "scene-v1"),
            embedding("a", listOf(1f, 0f, 0f, 0f), "scene-v1"),
            embedding("b", listOf(0.99f, 0.1f, 0f, 0f), "scene-v1"),
        )
        val clusterer = SceneSimilarityClusterer(threshold = 0.9f)
        assertEquals(clusterer.cluster(data), clusterer.cluster(data.reversed()))
    }

    @Test
    fun `large candidate buckets retain every similar item`() {
        val embeddings = (0 until 300).map { index -> embedding("media-$index", listOf(1f, 0f, 0f, 0f), "scene-v1") }
        val result = SceneSimilarityClusterer(threshold = 0.99f).cluster(embeddings)
        assertEquals(1, result.size)
        assertEquals(300, result.single().mediaUris.size)
    }

    private fun embedding(uri: String, vector: List<Float>, model: String) =
        SceneEmbedding(mediaUri = uri, modelVersion = model, vector = vector.toFloatArray())
}
