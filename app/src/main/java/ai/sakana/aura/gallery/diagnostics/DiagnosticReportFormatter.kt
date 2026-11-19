package ai.sakana.aura.gallery.diagnostics

internal object DiagnosticReportFormatter {
    fun format(
        packageName: String,
        appVersion: String,
        sdkInt: Int,
        manufacturer: String,
        model: String,
        generatedAt: String,
        logContents: String,
    ): String = buildString {
        appendLine("Aura AI Gallery — Diagnostic Report")
        appendLine("Generated: $generatedAt")
        appendLine("Package: $packageName")
        appendLine("App version: $appVersion")
        appendLine("Device: ${manufacturer.ifBlank { "Unknown" }} ${model.ifBlank { "Unknown" }}")
        appendLine("Android SDK: $sdkInt")
        appendLine()
        appendLine("Privacy: This report contains app diagnostic messages and basic device/build details. It does not intentionally include photo or video contents, media filenames, or content URIs. Review the report before sharing. It is only shared when you choose a destination.")
        appendLine()
        appendLine("--- App logs ---")
        if (logContents.isBlank()) {
            appendLine("No app log entries captured yet")
        } else {
            appendLine(logContents.trimEnd())
        }
    }
}
