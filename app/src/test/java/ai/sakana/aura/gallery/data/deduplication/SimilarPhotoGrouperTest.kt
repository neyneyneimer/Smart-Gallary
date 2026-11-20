package ai.sakana.aura.gallery.data.deduplication

import ai.sakana.aura.gallery.data.local.MediaItemEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SimilarPhotoGrouperTest {
    private fun item(id: Long, w: Int = 4000, h: Int = 3000, video: Boolean = false) = MediaItemEntity(
        id = id, volumeName = "external", mediaStoreId = id, contentUri = "content://m/$id", displayName = "f$id",
        mimeType = if (video) "video/mp4" else "image/jpeg", sizeBytes = 100, dateTakenEpochMillis = 1L,
        width = w, height = h, sourceType = "MEDIA_STORE", mediaType = if (video) "VIDEO" else "IMAGE",
    )

    private val base = 0x5555_5555_5555_5555L
    private fun flip(hash: Long, vararg bits: Int): Long = bits.fold(hash) { acc, b -> acc xor (1L shl b) }

    @Test fun groupingIsTransitive() {
        val a = HashedMedia(item(1), base)
        val b = HashedMedia(item(2), flip(base, 0, 1, 2))            // 3 from A
        val c = HashedMedia(item(3), flip(base, 10, 11, 12, 13, 14, 15, 16, 17)) // 8 from A, 11 from B
        assertEquals(11, HammingDistance.distance(b.hash, c.hash))
        val groups = SimilarPhotoGrouper.group(listOf(a, b, c))
        assertEquals(1, groups.size)
        assertEquals(3, groups.single().count)
    }

    @Test fun distantImagesStaySeparate() {
        val groups = SimilarPhotoGrouper.group(listOf(HashedMedia(item(1), base), HashedMedia(item(2), base.inv())))
        assertTrue(groups.isEmpty())
    }

    @Test fun imagesAndVideosAreNeverCompared() {
        val groups = SimilarPhotoGrouper.group(listOf(HashedMedia(item(1), base), HashedMedia(item(2, video = true), base)))
        assertTrue(groups.isEmpty())
    }

    @Test fun veryDifferentAspectRatiosAreNotCompared() {
        assertFalse(SimilarPhotoGrouper.canCompare(item(1, 4000, 3000), item(2, 3000, 4000)))
        assertTrue(SimilarPhotoGrouper.canCompare(item(1, 4000, 3000), item(2, 2000, 1500))) // resized copy
        assertFalse(SimilarPhotoGrouper.canCompare(item(1, 0, 0), item(2)))
    }

    @Test fun degenerateHashesAreExcluded() {
        val groups = SimilarPhotoGrouper.group(listOf(HashedMedia(item(1), 0L), HashedMedia(item(2), 0L)))
        assertTrue(groups.isEmpty())
    }

    @Test fun keepCopyHasZeroDistanceAndIsListedFirst() {
        val small = HashedMedia(item(1, 2000, 1500), flip(base, 3))
        val large = HashedMedia(item(2, 4000, 3000), base)
        val group = SimilarPhotoGrouper.group(listOf(small, large)).single()
        assertEquals("content://m/2", group.recommendedKeepUri)
        assertEquals(0, group.members.first().distanceToKeep)
        assertEquals(1, group.members.last().distanceToKeep)
        assertTrue(group.isNearExact)
    }

    @Test fun disjointSetMergesAndCompresses() {
        val set = DisjointSet(6)
        set.union(0, 1); set.union(1, 2); set.union(4, 5)
        assertEquals(set.find(0), set.find(2))
        assertFalse(set.find(0) == set.find(4))
        assertEquals(3, (0 until 6).map { set.find(it) }.toSet().size)
    }
}
