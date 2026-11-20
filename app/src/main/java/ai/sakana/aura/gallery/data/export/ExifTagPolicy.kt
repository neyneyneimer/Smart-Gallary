package ai.sakana.aura.gallery.data.export

/** Which EXIF tags survive an export. Software/processing tags are deliberately NOT copied. */
object ExifTagPolicy {
    val copiedTags: List<String> = listOf(
        "DateTimeOriginal", "Orientation", "Make", "Model",
        "GPSLatitude", "GPSLatitudeRef", "GPSLongitude", "GPSLongitudeRef",
        "GPSAltitude", "GPSAltitudeRef", "GPSTimeStamp", "GPSDateStamp",
    )
    val neverCopied: Set<String> = setOf("Software", "ProcessingSoftware", "UserComment", "ImageDescription")
    const val USER_COMMENT: String = "UserComment"

    /** Copies every present, allowed tag from [read] into [write]; returns the tag names written. */
    fun copy(read: (String) -> String?, write: (String, String) -> Unit): List<String> {
        val written = ArrayList<String>()
        for (tag in copiedTags) {
            if (tag in neverCopied) continue
            val value = read(tag) ?: continue
            write(tag, value)
            written += tag
        }
        return written
    }
}
