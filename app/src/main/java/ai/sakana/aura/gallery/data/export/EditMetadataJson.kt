package ai.sakana.aura.gallery.data.export

import ai.sakana.aura.gallery.domain.editing.EditParams
import java.util.Locale

/** Builds the EXIF UserComment payload: {"editor":"Aura","preset":"name","params":{...}}. ASCII only. */
object EditMetadataJson {
    fun build(presetName: String?, params: EditParams): String {
        fun num(v: Float) = String.format(Locale.US, "%.3f", v)
        return "{\"editor\":\"Aura\",\"preset\":" + quote(presetName ?: "") +
            ",\"params\":{\"brightness\":" + num(params.brightness) +
            ",\"contrast\":" + num(params.contrast) +
            ",\"saturation\":" + num(params.saturation) +
            ",\"warmth\":" + num(params.warmth) + "}}"
    }

    fun quote(value: String): String {
        val out = StringBuilder("\"")
        for (ch in value) {
            when {
                ch == '"' -> out.append("\\\"")
                ch == '\\' -> out.append("\\\\")
                ch == '\n' -> out.append("\\n")
                ch == '\r' -> out.append("\\r")
                ch == '\t' -> out.append("\\t")
                ch.code < 0x20 || ch.code > 0x7E -> out.append(String.format(Locale.US, "\\u%04x", ch.code))
                else -> out.append(ch)
            }
        }
        return out.append('"').toString()
    }
}
