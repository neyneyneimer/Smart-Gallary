package ai.sakana.aura.gallery.data.mediastore

import ai.sakana.aura.gallery.data.local.MediaItemEntity

internal data class MediaStoreReconciliationPlan(
    val itemsToUpsert: List<MediaItemEntity>,
    val mediaTypesToReconcile: Set<String>,
)

/**
 * Prevents partial or unsupported collection results from being used to hide existing media.
 * A successfully scanned empty collection is intentionally still reconciled.
 */
internal object MediaStoreReconciliationPolicy {
    private val supportedTypes = setOf("IMAGE", "VIDEO")

    fun createPlan(scan: MediaStoreScanResult): MediaStoreReconciliationPlan {
        val completedTypes = scan.completedMediaTypes.intersect(supportedTypes)
        return MediaStoreReconciliationPlan(
            itemsToUpsert = scan.items.filter {
                it.sourceType == "MEDIA_STORE" && it.mediaType in completedTypes
            },
            mediaTypesToReconcile = completedTypes,
        )
    }
}
