package ai.sakana.aura.gallery.domain.recognition

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

data class ThresholdEvaluationMetrics(
    val threshold: Float,
    val precision: Float,
    val recall: Float,
    val f1: Float,
)

class SceneSimilarityThresholdEvaluationTest {
    private val thresholds = listOf(0.75f, 0.80f, 0.85f, 0.90f)

    private fun syntheticSceneVector(sceneId: Int, dims: Int = 64, noise: Float = 0.05f): FloatArray {
        val vec = FloatArray(dims) { idx ->
            val base = kotlin.math.sin((idx + 1) * (sceneId + 1) * 0.37)
            val jitter = kotlin.math.cos((idx + 3) * (sceneId + 7) * 0.19) * noise
            (base + jitter).toFloat()
        }
        val norm = sqrt(vec.sumOf { (it * it).toDouble() }).toFloat().coerceAtLeast(1e-6f)
        return FloatArray(dims) { vec[it] / norm }
    }

    @Test
    fun `evaluates precision recall and f1 across candidate thresholds`() {
        val sceneLabels = (0 until 12).flatMap { sceneId ->
            List(18) { sceneId to syntheticSceneVector(sceneId, noise = 0.03f * (it % 4)) }
        }
        val results = thresholds.map { threshold ->
            var tp = 0
            var fp = 0
            var fn = 0
            for (i in sceneLabels.indices) {
                for (j in i + 1 until sceneLabels.size) {
                    val sameScene = sceneLabels[i].first == sceneLabels[j].first
                    val sim = dotProduct(sceneLabels[i].second, sceneLabels[j].second)
                    val predictedSame = sim >= threshold
                    if (predictedSame && sameScene) tp++
                    else if (predictedSame && !sameScene) fp++
                    else if (!predictedSame && sameScene) fn++
                }
            }
            val precision = if (tp + fp == 0) 0f else tp.toFloat() / (tp + fp)
            val recall = if (tp + fn == 0) 0f else tp.toFloat() / (tp + fn)
            val f1 = if (precision + recall == 0f) 0f else 2f * precision * recall / (precision + recall)
            ThresholdEvaluationMetrics(threshold, precision, recall, f1)
        }
        assertTrue(results.any { it.f1 > 0f })
    }

    private fun dotProduct(a: FloatArray, b: FloatArray): Float {
        var sum = 0.0
        for (i in a.indices) sum += a[i].toDouble() * b[i].toDouble()
        return sum.toFloat()
    }
}
