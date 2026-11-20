package ai.sakana.aura.gallery.data.deduplication

import ai.sakana.aura.gallery.data.local.MediaItemEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

class ExactDuplicateDetectorTest {
    private val emptySha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"

    private class FakeSource(private val files: Map<String, ByteArray>) : MediaByteSource {
        val opened = mutableListOf<String>()
        override fun open(contentUri: String): InputStream? {
            opened += contentUri
            return files[contentUri]?.let { ByteArrayInputStream(it) }
        }
    }

    /** Deterministic stream of [length] bytes that never holds the whole content in memory. */
    private class GeneratedStream(private val length: Long, private val seed: Int) : InputStream() {
        private var position = 0L
        override fun read(): Int {
            if (position >= length) return -1
            return ((position * 31 + seed) and 0xFF).toInt().also { position++ }
        }
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (position >= length) return -1
            val n = minOf(len.toLong(), length - position).toInt()
            for (i in 0 until n) b[off + i] = ((position + i) * 31 + seed).toByte()
            position += n
            return n
        }
    }

    private fun item(
        id: Long,
        size: Long,
        uri: String = "content://media/external/images/media/$id",
        width: Int = 1000,
        height: Int = 1000,
        sourceType: String = "MEDIA_STORE",
        name: String? = "img_$id.jpg",
    ) = MediaItemEntity(
        id = id, volumeName = "external", mediaStoreId = id, contentUri = uri, displayName = name,
        mimeType = "image/jpeg", sizeBytes = size, dateTakenEpochMillis = 1_700_000_000_000L,
        width = width, height = height, sourceType = sourceType,
    )

    private fun detect(source: MediaByteSource, items: List<MediaItemEntity>, known: Map<String, String> = emptyMap()) =
        runBlocking { ExactDuplicateDetector(source, Dispatchers.Unconfined).detect(items, known) }

    @Test
    fun identicalFilesWithDifferentNamesAreGrouped() {
        val bytes = ByteArray(2048) { (it % 251).toByte() }
        val a = item(1, bytes.size.toLong(), name = "holiday.jpg")
        val b = item(2, bytes.size.toLong(), name = "IMG_9999 (copy).jpg")
        val source = FakeSource(mapOf(a.contentUri to bytes, b.contentUri to bytes.copyOf()))
        val result = detect(source, listOf(a, b))
        assertEquals(1, result.groups.size)
        assertEquals(2, result.groups.single().count)
        assertEquals(bytes.size.toLong(), result.totalWastedBytes)
    }

    @Test
    fun differentFilesOfSameSizeAreNotGrouped() {
        val a = item(1, 1024)
        val b = item(2, 1024)
        val source = FakeSource(mapOf(a.contentUri to ByteArray(1024) { 1 }, b.contentUri to ByteArray(1024) { 2 }))
        val result = detect(source, listOf(a, b))
        assertTrue(result.groups.isEmpty())
        assertEquals(2, result.hashedCount)
    }

    @Test
    fun filesOfDifferentSizeAreNeverHashed() {
        val a = item(1, 10)
        val b = item(2, 11)
        val source = FakeSource(emptyMap())
        val result = detect(source, listOf(a, b))
        assertTrue(result.groups.isEmpty())
        assertTrue(source.opened.isEmpty())
    }

    @Test
    fun emptyFileHashesCorrectlyAndIsNeverGrouped() {
        assertEquals(emptySha256, Sha256Hasher.hash(ByteArrayInputStream(ByteArray(0))))
        val a = item(1, 0)
        val b = item(2, 0)
        val source = FakeSource(mapOf(a.contentUri to ByteArray(0), b.contentUri to ByteArray(0)))
        val result = detect(source, listOf(a, b))
        assertTrue(result.groups.isEmpty())
        assertTrue(source.opened.isEmpty())
    }

    @Test
    fun largeFileStreamsAndMatchesReferenceDigest() {
        val length = 8L * 1024 * 1024 + 123
        val expected = MessageDigest.getInstance("SHA-256").run {
            val buffer = ByteArray(8192)
            GeneratedStream(length, 7).use { s ->
                while (true) { val n = s.read(buffer); if (n < 0) break; update(buffer, 0, n) }
            }
            digest().joinToString("") { "%02x".format(it) }
        }
        assertEquals(expected, Sha256Hasher.hash(GeneratedStream(length, 7)))
        assertNotEquals(expected, Sha256Hasher.hash(GeneratedStream(length, 8)))
    }

    @Test
    fun largeIdenticalFilesAreGroupedAndLargestIsHashedFirst() {
        val big = 3L * 1024 * 1024
        val small = 1024L
        val b1 = item(1, big); val b2 = item(2, big)
        val s1 = item(3, small); val s2 = item(4, small)
        val source = object : MediaByteSource {
            val order = mutableListOf<String>()
            override fun open(contentUri: String): InputStream {
                order += contentUri
                return if (contentUri == b1.contentUri || contentUri == b2.contentUri) GeneratedStream(big, 1) else GeneratedStream(small, 2)
            }
        }
        val result = runBlocking { ExactDuplicateDetector(source, Dispatchers.Unconfined).detect(listOf(s1, s2, b1, b2)) }
        assertEquals(2, result.groups.size)
        assertEquals(big, result.groups.first().sizeBytesEach)
        assertEquals(setOf(b1.contentUri, b2.contentUri), source.order.take(2).toSet())
    }

    @Test
    fun pickerItemsAreSkippedAndNeverOpened() {
        val a = item(1, 500, sourceType = "PICKER")
        val b = item(2, 500, sourceType = "PICKER")
        val source = FakeSource(emptyMap())
        val result = detect(source, listOf(a, b))
        assertEquals(2, result.skippedPickerCount)
        assertTrue(source.opened.isEmpty())
        assertTrue(result.groups.isEmpty())
    }

    @Test
    fun knownHashesAreReusedWithoutReadingFiles() {
        val a = item(1, 100); val b = item(2, 100)
        val source = FakeSource(emptyMap())
        val result = detect(source, listOf(a, b), mapOf(a.contentUri to "h", b.contentUri to "h"))
        assertEquals(1, result.groups.size)
        assertEquals(2, result.reusedHashCount)
        assertTrue(source.opened.isEmpty())
    }

    @Test
    fun unreadableItemsAreReportedNotGrouped() {
        val a = item(1, 100); val b = item(2, 100); val c = item(3, 100)
        val data = ByteArray(100) { 9 }
        val source = object : MediaByteSource {
            override fun open(contentUri: String): InputStream? = when (contentUri) {
                a.contentUri -> ByteArrayInputStream(data)
                b.contentUri -> throw IOException("gone")
                else -> null
            }
        }
        val result = detect(source, listOf(a, b, c))
        assertEquals(setOf(b.contentUri, c.contentUri), result.unreadableUris.toSet())
        assertTrue(result.groups.isEmpty())
    }

    @Test
    fun keepPolicyRecommendsHighestResolution() {
        val low = item(1, 100, width = 640, height = 480)
        val high = item(2, 100, width = 4000, height = 3000)
        assertEquals(high.contentUri, DuplicateKeepPolicy.recommend(listOf(low, high)).contentUri)
    }

    @Test
    fun keepPolicyBreaksResolutionTiesByMetadata() {
        val bare = item(1, 100, name = null).copy(mimeType = null, dateTakenEpochMillis = 0L)
        val rich = item(2, 100)
        assertEquals(rich.contentUri, DuplicateKeepPolicy.recommend(listOf(bare, rich)).contentUri)
    }

    @Test
    fun sha256CollisionProbabilityIsNegligible() {
        // Birthday bound: p ~= n^2 / 2^257. Even for a billion files it is astronomically small.
        val n = 1.0e9
        val p = n * n / Math.pow(2.0, 257.0)
        assertTrue("p=$p", p < 1.0e-55)
        // Sanity: 5,000 distinct small inputs yield 5,000 distinct digests.
        val digests = (0 until 5000).map { Sha256Hasher.hash(ByteArrayInputStream(("file-$it").toByteArray())) }.toSet()
        assertEquals(5000, digests.size)
    }
}
