package ai.sakana.aura.gallery.data.local

/**
 * Minimal existing row state needed to reconcile a MediaStore scan without loading
 * thumbnails, metadata, folder paths or other unused columns from the full media table.
 */
data class MediaItemRefreshState(
    val id: Long,
    val contentUri: String,
    val isFavorite: Boolean,
    val isTrashed: Boolean,
    val trashedAtEpochMillis: Long,
)
