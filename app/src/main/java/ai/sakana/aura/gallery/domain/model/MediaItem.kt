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
    val mediaType: String = "IMAGE",
    val bucketName: String = "Other",
    val relativePath: String = "",
    val durationMillis: Long = 0L,
    val isTrashed: Boolean = false,
    val trashedAtEpochMillis: Long = 0L,
) {
    val isVideo: Boolean get() = mediaType == "VIDEO" || mimeType.orEmpty().startsWith("video/")
}
