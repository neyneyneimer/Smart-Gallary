package ai.sakana.aura.gallery.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GalleryErrorReporterTest {
    @Test
    fun `reports a safe gallery error and clears it`() {
        val reporter = GalleryErrorReporter()

        reporter.report("refresh media", android.database.sqlite.SQLiteException("/private/path/aura.db"))

        assertEquals(
            "Aura couldn't access its local media library. Your original photos and videos are unchanged. Try again.",
            reporter.message.value,
        )
        assertEquals(false, reporter.message.value!!.contains("/private/path"))

        reporter.clear()

        assertNull(reporter.message.value)
    }
}
