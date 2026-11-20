package ai.sakana.aura.gallery.data.export

import ai.sakana.aura.gallery.domain.editing.EditParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream

class MediaEditorExporterTest {
    private class FakeSink(private val failOpen: Boolean = false) : PendingMediaSink {
        val events = mutableListOf<String>()
        val bytes = ByteArrayOutputStream()
        override fun createPending(displayName: String, mimeType: String, relativePath: String): String? {
            events += "pending:$relativePath"; return "h1"
        }
        override fun openOutput(handle: String): OutputStream? = if (failOpen) null else bytes
        override fun finalize(handle: String) { events += "finalize" }
        override fun discard(handle: String) { events += "discard" }
    }

    @Test fun successfulWriteGoesPendingThenFinal() {
        val sink = FakeSink()
        val progress = mutableListOf<Int>()
        val handle = AtomicMediaWriter.write(sink, "a.jpg", "image/jpeg", "Pictures/Aura Edits", { it.write(byteArrayOf(1, 2, 3)) }, onProgress = { progress += it })
        assertEquals("h1", handle)
        assertEquals(listOf("pending:Pictures/Aura Edits", "finalize"), sink.events)
        assertEquals(3, sink.bytes.size())
        assertEquals(100, progress.last())
        assertEquals(progress.sorted(), progress)
    }

    @Test fun writerFailureDeletesPendingRecordAndNeverFinalizes() {
        val sink = FakeSink()
        assertThrows(IOException::class.java) {
            AtomicMediaWriter.write(sink, "a.jpg", "image/jpeg", "Pictures/X", { throw IOException("disk full") })
        }
        assertEquals(listOf("pending:Pictures/X", "discard"), sink.events)
    }

    @Test fun unopenableOutputDeletesPendingRecord() {
        val sink = FakeSink(failOpen = true)
        assertThrows(IllegalStateException::class.java) { AtomicMediaWriter.write(sink, "a.jpg", "image/jpeg", "Pictures/X", {}) }
        assertTrue("discard" in sink.events)
        assertFalse("finalize" in sink.events)
    }

    @Test fun postProcessFailureDeletesPendingRecord() {
        val sink = FakeSink()
        assertThrows(IllegalArgumentException::class.java) {
            AtomicMediaWriter.write(sink, "a.jpg", "image/jpeg", "Pictures/X", { it.write(1) }, postProcess = { throw IllegalArgumentException("exif") })
        }
        assertEquals("discard", sink.events.last())
    }

    @Test fun exifCopyKeepsAllowedTagsAndSkipsSoftware() {
        val source = mapOf(
            "DateTimeOriginal" to "2026:01:02 03:04:05", "Orientation" to "6", "Make" to "Acme", "Model" to "Cam 1",
            "GPSLatitude" to "10/1,0/1,0/1", "GPSLatitudeRef" to "N", "Software" to "EditorX", "UserComment" to "old",
        )
        val target = mutableMapOf<String, String>()
        val written = ExifTagPolicy.copy({ source[it] }, { k, v -> target[k] = v })
        assertEquals("6", target["Orientation"])
        assertEquals("2026:01:02 03:04:05", target["DateTimeOriginal"])
        assertEquals("N", target["GPSLatitudeRef"])
        assertFalse(target.containsKey("Software"))
        assertFalse(target.containsKey("UserComment"))
        assertEquals(target.keys.toList(), written)
    }

    @Test fun exifCopyOfPhotoWithoutExifWritesNothing() {
        assertTrue(ExifTagPolicy.copy({ null }, { _, _ -> error("must not write") }).isEmpty())
    }

    @Test fun editHistoryJsonHasExpectedShape() {
        val json = EditMetadataJson.build("Warm \"sunset\"", EditParams(0.1f, -0.25f, 0f, 0.5f))
        assertEquals(
            "{\"editor\":\"Aura\",\"preset\":\"Warm \\\"sunset\\\"\",\"params\":{\"brightness\":0.100,\"contrast\":-0.250,\"saturation\":0.000,\"warmth\":0.500}}",
            json,
        )
    }

    @Test fun editHistoryJsonStaysAscii() {
        val json = EditMetadataJson.build("Caf\u00e9", EditParams())
        assertTrue(json.all { it.code in 0x20..0x7E })
        assertTrue(json.contains("\\u00e9"))
    }

    @Test fun exportLocationIsSanitized() {
        assertEquals("Pictures/Aura Edits", ExportLocation.sanitize(null))
        assertEquals("Pictures/AuraGallery", ExportLocation.sanitize("Pictures/AuraGallery"))
        assertEquals("Pictures/My Trip", ExportLocation.sanitize("My Trip"))
        assertEquals("DCIM/Aura", ExportLocation.sanitize("/DCIM//Aura/"))
        assertEquals(ExportLocation.DEFAULT, ExportLocation.sanitize("Pictures/../../etc"))
        assertEquals(ExportLocation.DEFAULT, ExportLocation.sanitize("Pictures"))
        assertEquals("Pictures/ab", ExportLocation.sanitize("Pictures/a*b?"))
    }

    @Test fun qualityIsClampedToSupportedRange() {
        assertEquals(80, ExportSettings(quality = 10).normalized().quality)
        assertEquals(100, ExportSettings(quality = 500).normalized().quality)
        assertEquals(94, ExportSettings().normalized().quality)
    }
}
