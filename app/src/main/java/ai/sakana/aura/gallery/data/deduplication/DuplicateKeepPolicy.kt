package ai.sakana.aura.gallery.data.deduplication

import ai.sakana.aura.gallery.data.local.MediaItemEntity

/**
 * Picks the copy to RECOMMEND keeping. This is only a default selection in the review UI;
 * nothing is ever deleted without an explicit user action.
 * Order: highest resolution, then most complete metadata, then most recently modified, then lowest id.
 */
object DuplicateKeepPolicy {
    fun metadataScore(item: MediaItemEntity): Int =
        listOf(
            item.dateTakenEpochMillis > 0L,
            !item.displayName.isNullOrBlank(),
            !item.mimeType.isNullOrBlank(),
            item.width > 0 && item.height > 0,
            item.relativePath.isNotBlank(),
            item.bucketName != "Other",
        ).count { it }

    private val comparator: Comparator<MediaItemEntity> =
        compareByDescending<MediaItemEntity> { it.width.toLong() * it.height.toLong() }
            .thenByDescending { metadataScore(it) }
            .thenByDescending { it.dateModifiedEpochMillis }
            .thenBy { it.id }
            .thenBy { it.contentUri }

    fun recommend(items: List<MediaItemEntity>): MediaItemEntity {
        require(items.isNotEmpty()) { "Cannot recommend a copy from an empty group." }
        return items.sortedWith(comparator).first()
    }
}
