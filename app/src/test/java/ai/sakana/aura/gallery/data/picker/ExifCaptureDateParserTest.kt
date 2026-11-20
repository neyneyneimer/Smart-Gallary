package ai.sakana.aura.gallery.data.picker

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExifCaptureDateParserTest {
    @Test
    fun `uses original EXIF offset when supplied`() {
        val expected = LocalDateTime.of(2021, 7, 8, 9, 10, 11)
            .toInstant(ZoneOffset.ofHours(6))
            .toEpochMilli()

        assertEquals(
            expected,
            ExifCaptureDateParser.parse("2021:07:08 09:10:11", "+06:00", ZoneOffset.UTC),
        )
    }

    @Test
    fun `uses device zone when EXIF offset is absent`() {
        val zone = ZoneId.of("Asia/Dhaka")
        val expected = LocalDateTime.of(2020, 1, 2, 3, 4, 5).atZone(zone).toInstant().toEpochMilli()

        assertEquals(
            expected,
            ExifCaptureDateParser.parse("2020:01:02 03:04:05", null, zone),
        )
    }

    @Test
    fun `malformed or missing EXIF timestamps stay unknown`() {
        assertNull(ExifCaptureDateParser.parse("not-a-date", null, ZoneOffset.UTC))
        assertNull(ExifCaptureDateParser.parse(null, null, ZoneOffset.UTC))
    }
}
