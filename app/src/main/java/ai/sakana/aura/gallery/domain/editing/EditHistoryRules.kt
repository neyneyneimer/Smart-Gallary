package ai.sakana.aura.gallery.domain.editing

import ai.sakana.aura.gallery.data.local.EditHistoryEntity
import kotlin.math.roundToInt

data class EditParams(
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val warmth: Float = 0f,
) {
    val isIdentity: Boolean get() = brightness == 0f && contrast == 0f && saturation == 0f && warmth == 0f
}

/** Pure rules for the edit stack. Persistence lives in EditHistoryDao. */
object EditHistoryRules {
    const val MAX_NAME_LENGTH: Int = 40

    fun params(preset: EditHistoryEntity) = EditParams(preset.brightness, preset.contrast, preset.saturation, preset.warmth)

    fun normalizeName(raw: String): String = raw.trim().replace(Regex("\\s+"), " ").take(MAX_NAME_LENGTH).trim()

    fun defaultName(existingCount: Int): String = "Edit " + (existingCount + 1)

    /** e.g. "B +10 · C 0 · S -5 · W +20" (slider values are -1..1, shown as percent). */
    fun summary(params: EditParams): String {
        fun pct(v: Float): String { val n = (v * 100f).roundToInt(); return if (n > 0) "+$n" else n.toString() }
        return "B " + pct(params.brightness) + " · C " + pct(params.contrast) + " · S " + pct(params.saturation) + " · W " + pct(params.warmth)
    }

    fun summary(preset: EditHistoryEntity): String = summary(params(preset))

    /** Null when deletable; otherwise a user-facing reason. The active preset cannot be deleted. */
    fun deleteBlockedReason(preset: EditHistoryEntity): String? =
        if (preset.isActive) "This preset is active. Switch to another preset or reset first." else null

    /** New saved preset from the current slider values. */
    fun newPreset(contentUri: String, name: String, params: EditParams, now: Long, makeActive: Boolean = true) = EditHistoryEntity(
        contentUri = contentUri,
        name = normalizeName(name).ifBlank { "Untitled edit" },
        brightness = params.brightness, contrast = params.contrast, saturation = params.saturation, warmth = params.warmth,
        appliedAt = now, isActive = makeActive,
    )

    /** Tapping a preset applies it by creating a NEW active entry; the source stays untouched. */
    fun reapply(source: EditHistoryEntity, now: Long): EditHistoryEntity = source.copy(id = 0, appliedAt = now, isActive = true)

    /** Long-press "Save as new": an inactive copy with a custom name and fresh timestamp. */
    fun branch(source: EditHistoryEntity, newName: String, now: Long): EditHistoryEntity =
        source.copy(id = 0, name = normalizeName(newName).ifBlank { source.name + " copy" }, appliedAt = now, isActive = false)
}
