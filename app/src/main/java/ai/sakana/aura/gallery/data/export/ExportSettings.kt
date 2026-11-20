package ai.sakana.aura.gallery.data.export

import android.content.Context

/** Where exported copies go. Existing behaviour (Pictures/Aura Edits) stays the default. */
object ExportLocation {
    const val DEFAULT: String = "Pictures/Aura Edits"
    const val AURA_GALLERY: String = "Pictures/AuraGallery"
    private val allowedRoots = listOf("Pictures", "DCIM")
    private const val MAX_LENGTH = 100

    /** MediaStore only accepts folders under Pictures/ or DCIM/; anything unsafe falls back to the default. */
    fun sanitize(raw: String?): String {
        val parts = raw.orEmpty().replace('\\', '/').split('/').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty() || parts.any { it == "." || it == ".." }) return DEFAULT
        val rooted = if (parts.first() in allowedRoots) parts else listOf("Pictures") + parts
        val cleaned = rooted.map { seg -> seg.filter { it.isLetterOrDigit() || it in " _-." }.trim() }.filter { it.isNotEmpty() }
        if (cleaned.size < 2) return DEFAULT
        return cleaned.joinToString("/").take(MAX_LENGTH).trimEnd('/', ' ')
    }
}

data class ExportSettings(
    val quality: Int = DEFAULT_QUALITY,
    val relativePath: String = ExportLocation.DEFAULT,
    val includeEditHistory: Boolean = true,
) {
    fun normalized() = copy(quality = quality.coerceIn(MIN_QUALITY, MAX_QUALITY), relativePath = ExportLocation.sanitize(relativePath))

    companion object {
        const val MIN_QUALITY = 80
        const val MAX_QUALITY = 100
        const val DEFAULT_QUALITY = 94
    }
}

class ExportSettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("aura_export_settings", Context.MODE_PRIVATE)

    fun load(): ExportSettings = ExportSettings(
        quality = prefs.getInt(KEY_QUALITY, ExportSettings.DEFAULT_QUALITY),
        relativePath = prefs.getString(KEY_PATH, ExportLocation.DEFAULT) ?: ExportLocation.DEFAULT,
        includeEditHistory = prefs.getBoolean(KEY_HISTORY, true),
    ).normalized()

    fun save(settings: ExportSettings) {
        val s = settings.normalized()
        prefs.edit().putInt(KEY_QUALITY, s.quality).putString(KEY_PATH, s.relativePath).putBoolean(KEY_HISTORY, s.includeEditHistory).apply()
    }

    private companion object {
        const val KEY_QUALITY = "quality"
        const val KEY_PATH = "relative_path"
        const val KEY_HISTORY = "include_history"
    }
}
