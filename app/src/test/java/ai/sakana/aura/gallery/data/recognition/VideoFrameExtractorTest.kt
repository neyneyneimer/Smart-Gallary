package ai.sakana.aura.gallery.data.recognition

import android.app.Application
import ai.sakana.aura.gallery.domain.model.MediaItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoFrameExtractorTest {
    private val extractor = VideoFrameExtractor(Application())

    @Test
    fun `short video under 10s produces 5 evenly spaced timestamps`() {
        val timestamps = extractor.computeSampleTimestampsMillis(5_000L)
        assertEquals(5, timestamps.size)
        assertEquals(listOf(500L, 1500L, 2500L, 3500L, 4500L), timestamps)
    }

    @Test
    fun `long video produces 1 frame per second`() {
        val timestamps = extractor.computeSampleTimestampsMillis(15_000L)
        assertEquals(15, timestamps.size)
        assertEquals(0L, timestamps.first())
        assertEquals(14_000L, timestamps.last())
    }

    @Test
    fun `skips oversized or overlong video initially unless enabled`() {
        val bigVideo = MediaItem(
            id = 1L,
            contentUri = "content://media/external/video/media/1",
            displayName = "big.mp4",
            mimeType = "video/mp4",
            sizeBytes = 600L * 1024L * 1024L,
            dateTakenEpochMillis = 0L,
            dateModifiedEpochMillis = 0L,
            width = 1920,
            height = 1080,
            durationMillis = 60_000L,
            isFavorite = false,
        )
        assertFalse(extractor.isEligibleForInitialAnalysis(bigVideo, allowLargeVideos = false))
        assertTrue(extractor.isEligibleForInitialAnalysis(bigVideo, allowLargeVideos = true))
    }
}
