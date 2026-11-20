package ai.sakana.aura.gallery.diagnostics

import android.content.Context
import android.os.Build
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

internal object DiagnosticReportExporter {
    fun create(context: Context): File {
        val diagnosticsDirectory = File(context.filesDir, "diagnostics")
        val currentLog = File(diagnosticsDirectory, "aura-debug.log")
        val previousLog = File(diagnosticsDirectory, "aura-debug.1.log")
        val logs = listOf(previousLog, currentLog)
            .filter { it.isFile }
            .joinToString(separator = "\n") { file -> file.readText(Charsets.UTF_8) }

        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val appVersion = packageInfo.versionName ?: "unknown"
        val generatedAt = DateTimeFormatter.ISO_INSTANT.format(Instant.now().atOffset(ZoneOffset.UTC))
        val report = DiagnosticReportFormatter.format(
            packageName = context.packageName,
            appVersion = appVersion,
            sdkInt = Build.VERSION.SDK_INT,
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            generatedAt = generatedAt,
            logContents = logs,
        )

        val exportDirectory = File(context.cacheDir, "diagnostics").apply { mkdirs() }
        val timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC).format(Instant.now())
        return File(exportDirectory, "aura-diagnostic-report-$timestamp.txt").apply {
            writeText(report, Charsets.UTF_8)
        }
    }
}
