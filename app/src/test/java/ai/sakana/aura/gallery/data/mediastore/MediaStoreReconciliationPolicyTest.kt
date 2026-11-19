package ai.sakana.aura.gallery.data.mediastore

import ai.sakana.aura.gallery.data.local.MediaItemEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaStoreReconciliationPolicyTest {
    @Test
    fun `failed video scan cannot hide existing videos or upsert partial rows`() {
        val image = mediaItem("image-1", "IMAGE")
        val partialVideo = mediaItem("video-partial", "VIDEO")

        val plan = MediaStoreReconciliationPolicy.createPlan(
            MediaStoreScanResult(
                items = listOf(image, partialVideo),
                completedMediaTypes = setOf("IMAGE"),
            ),
        )

        assertEquals(setOf("IMAGE"), plan.mediaTypesToReconcile)
        assertEquals(listOf(image), plan.itemsToUpsert)
    }

    @Test
    fun `successfully scanned empty collection is still reconciled`() {
        val plan = MediaStoreReconciliationPolicy.createPlan(
            MediaStoreScanResult(items = emptyList(), completedMediaTypes = setOf("VIDEO")),
        )

        assertEquals(setOf("VIDEO"), plan.mediaTypesToReconcile)
        assertEquals(emptyList<MediaItemEntity>(), plan.itemsToUpsert)
    }

    @Test
    fun `unknown collection types are ignored`() {
        val unsupported = mediaItem("other-1", "AUDIO")
        val plan = MediaStoreReconciliationPolicy.createPlan(
            MediaStoreScanResult(
                items = listOf(unsupported),
                completedMediaTypes = setOf("AUDIO", "IMAGE"),
            ),
        )

        assertEquals(setOf("IMAGE"), plan.mediaTypesToReconcile)
        assertEquals(emptyList<MediaItemEntity>(), plan.itemsToUpsert)
    }

    private fun mediaItem(uri: String, mediaType: String) = MediaItemEntity(
        volumeName = "external",
        mediaStoreId = null,
        contentUri = uri,
        displayName = null,
        mimeType = null,
        sizeBytes = 0L,
        dateTakenEpochMillis = 0L,
        width = 0,
        height = 0,
        sourceType = "MEDIA_STORE",
        mediaType = mediaType,
    )
}
