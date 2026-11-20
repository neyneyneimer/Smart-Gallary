package ai.sakana.aura.gallery.diagnostics

import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticReportFormatterTest {
    @Test
    fun `report contains app device timestamp and app log context`() {
        val report = DiagnosticReportFormatter.format(
            packageName = "ai.sakana.aura.gallery",
            appVersion = "0.1.0",
            sdkInt = 36,
            manufacturer = "Google",
            model = "Pixel 9",
            generatedAt = "2026-11-19T01:00:00Z",
            logContents = "I/Aura: Gallery refresh started\nE/Aura: Refresh failed",
        )

        assertTrue(report.contains("ai.sakana.aura.gallery"))
        assertTrue(report.contains("0.1.0"))
        assertTrue(report.contains("Android SDK: 36"))
        assertTrue(report.contains("Google Pixel 9"))
        assertTrue(report.contains("2026-11-19T01:00:00Z"))
        assertTrue(report.contains("Gallery refresh started"))
        assertTrue(report.contains("Refresh failed"))
    }

    @Test
    fun `report explains when no app log entries have been captured`() {
        val report = DiagnosticReportFormatter.format(
            packageName = "ai.sakana.aura.gallery",
            appVersion = "0.1.0",
            sdkInt = 26,
            manufacturer = "Unknown",
            model = "Device",
            generatedAt = "2026-11-19T01:00:00Z",
            logContents = " ",
        )

        assertTrue(report.contains("No app log entries captured yet"))
    }
}
