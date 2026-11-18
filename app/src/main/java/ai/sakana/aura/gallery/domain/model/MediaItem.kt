package ai.sakana.aura.gallery.domain.model

data class MediaItem(
    val id: Long,
    val contentUri: String,
    val displayName: String?,
    val mimeType: String?,
    val sizeBytes: Long,
    val dateTakenEpochMillis: Long,
    val width: Int,
    val height: Int,
    val isFavorite: Boolean,
)
