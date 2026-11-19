package ai.sakana.aura.gallery.data.local

/** Flat projection for a saved set and its (possibly empty) membership. */
data class AiSetMemberRow(
    val setId: String,
    val name: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val contentUri: String?,
)
