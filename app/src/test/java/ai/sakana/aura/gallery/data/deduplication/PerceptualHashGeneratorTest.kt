package ai.sakana.aura.gallery.data.deduplication

import ai.sakana.aura.gallery.data.local.MediaItemEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Images are synthetic luminance buffers (no android.graphics in plain JVM tests). A "scene" is a
 * 9x8 table of block values; rendering it at different resolutions simulates resizing.
 */
class PerceptualHashGeneratorTest {
    /** Block values 20..220, no two horizontal neighbours equal, deterministic. */
    private val scene: IntArray = IntArray(DHash.WIDTH * DHash.HEIGHT) { i ->
        var x = (i * 2654435761L + 12345L) and 0x7fffffffL
        x = (x * 1103515245L + 12345L) and 0x7fffffffL
        20 + ((x shr 8) % 200).toInt()
    }.also { grid ->
        for (y in 0 until DHash.HEIGHT) for (x in 0 until DHash.WIDTH - 1) {
            if (grid[y * DHash.WIDTH + x] == grid[y * DHash.WIDTH + x + 1]) grid[y * DHash.WIDTH + x + 1] += 7
        }
    }

    private fun render(grid: IntArray, width: Int, height: Int, noise: (Int) -> Int = { 0 }, shift: Int = 0): IntArray =
        IntArray(width * height) { i ->
            val x = i % width
            val y = i / width
            val value = grid[(y * DHash.HEIGHT / height) * DHash.WIDTH + (x * DHash.WIDTH / width)]
            (value + noise(i) + shift).coerceIn(0, 255)
        }

    private fun hash(gray: IntArray, w: Int, h: Int) = DHash.compute(DHash.downsample(gray, w, h))

    @Test fun identicalImagesHaveZeroDistance() {
        val a = hash(render(scene, 90, 80), 90, 80)
        assertEquals(0, HammingDistance.distance(a, hash(render(scene, 90, 80), 90, 80)))
    }

    @Test fun resizedHalfStaysWithinDuplicateThreshold() {
        val full = hash(render(scene, 90, 80), 90, 80)
        val half = hash(render(scene, 45, 40), 45, 40)
        assertTrue(HammingDistance.distance(full, half) <= HammingDistance.DUPLICATE_MAX)
    }

    @Test fun recompressionNoiseStaysWithinDuplicateThreshold() {
        val clean = hash(render(scene, 90, 80), 90, 80)
        val noisy = hash(render(scene, 90, 80, noise = { i -> (i % 5) - 2 }), 90, 80)
        assertTrue(HammingDistance.distance(clean, noisy) <= HammingDistance.DUPLICATE_MAX)
    }

    @Test fun brightnessShiftBarelyChangesHash() {
        val dim = hash(render(scene, 90, 80), 90, 80)
        val bright = hash(render(scene, 90, 80, shift = 10), 90, 80)
        assertTrue(HammingDistance.distance(dim, bright) <= HammingDistance.DUPLICATE_MAX)
    }

    @Test fun unrelatedImageIsFarAway() {
        val inverted = IntArray(scene.size) { 240 - scene[it] } // every left>right relation flips
        val a = hash(render(scene, 90, 80), 90, 80)
        val b = hash(render(inverted, 90, 80), 90, 80)
        assertTrue(HammingDistance.distance(a, b) > HammingDistance.SIMILAR_MAX)
        assertEquals(HammingDistance.Match.UNRELATED, HammingDistance.classify(HammingDistance.distance(a, b)))
    }

    @Test fun solidColourIsDegenerate() {
        val solid = hash(IntArray(90 * 80) { 128 }, 90, 80)
        assertEquals(0L, solid)
        assertTrue(DHash.isDegenerate(solid))
    }

    @Test fun transparentPixelsCarryNoSignal() {
        assertEquals(0, DHash.luminance(0x00FFFFFF))
        assertEquals(255, DHash.luminance(0xFFFFFFFF.toInt()))
        val transparent = IntArray(90 * 80) { DHash.luminance(0x00FFFFFF) }
        assertTrue(DHash.isDegenerate(hash(transparent, 90, 80)))
    }

    @Test fun imagesSmallerThanTheGridStillHash() {
        val tiny = DHash.downsample(IntArray(3 * 2) { it * 40 }, 3, 2)
        assertEquals(DHash.WIDTH * DHash.HEIGHT, tiny.size)
        DHash.compute(tiny)
    }

    @Test fun mismatchedBufferIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { DHash.downsample(IntArray(10), 4, 4) }
        assertThrows(IllegalArgumentException::class.java) { DHash.compute(IntArray(5)) }
    }

    @Test fun hammingThresholds() {
        assertEquals(HammingDistance.Match.DUPLICATE, HammingDistance.classify(5))
        assertEquals(HammingDistance.Match.SIMILAR, HammingDistance.classify(6))
        assertEquals(HammingDistance.Match.SIMILAR, HammingDistance.classify(10))
        assertEquals(HammingDistance.Match.UNRELATED, HammingDistance.classify(11))
        assertEquals(64, HammingDistance.distance(0L, -1L))
    }

    @Test fun detectorReusesCachedHashesAndReportsUndecodable() {
        fun item(id: Long) = MediaItemEntity(
            id = id, volumeName = "external", mediaStoreId = id, contentUri = "content://x/$id", displayName = "p$id.jpg",
            mimeType = "image/jpeg", sizeBytes = 10, dateTakenEpochMillis = 1L, width = 900, height = 800, sourceType = "MEDIA_STORE",
        )
        val sceneGrid = DHash.downsample(render(scene, 90, 80), 90, 80)
        val calls = mutableListOf<String>()
        val generator = PerceptualHashGenerator { item -> calls += item.contentUri; if (item.id == 3L) null else sceneGrid }
        val result = runBlocking {
            SimilarPhotoDetector(generator, Dispatchers.Unconfined).detect(
                items = listOf(item(1), item(2), item(3)),
                knownHashes = mapOf("content://x/1" to DHash.compute(sceneGrid)),
            )
        }
        assertEquals(listOf("content://x/2", "content://x/3"), calls)
        assertEquals(listOf("content://x/3"), result.failedUris)
        assertEquals(1, result.reusedHashCount)
        assertEquals(1, result.groups.size)
        assertEquals(2, result.groups.single().count)
        assertNotNull(result.groups.single().members.first())
        assertNull(result.failedUris.firstOrNull { it == "content://x/1" })
        assertFalse(result.groups.single().members.any { it.distanceToKeep > 0 })
    }
}
