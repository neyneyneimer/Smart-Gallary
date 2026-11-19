package ai.sakana.aura.gallery.domain.grouping

import ai.sakana.aura.gallery.domain.model.MediaItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GalleryCollectionGroupingTest {
    private fun item(id: Long, folder: String, type: String = "IMAGE") = MediaItem(
        id = id,
        contentUri = "content://media/$id",
        displayName = "$id.jpg",
        mimeType = if (type == "VIDEO") "video/mp4" else "image/jpeg",
        sizeBytes = 100,
        dateTakenEpochMillis = 1_700_000_000_000L - id,
        width = 100,
        height = 100,
        isFavorite = false,
        mediaType = type,
        bucketName = folder.substringAfterLast('/'),
        relativePath = folder,
    )

    @Test
    fun `standard albums separate camera screenshots videos and all recent media`() {
        val camera = item(1, "DCIM/Camera")
        val screenshot = item(2, "Pictures/Screenshots")
        val video = item(3, "Movies/Exports", "VIDEO")
        val downloads = item(4, "Download")

        val albums = GalleryCollectionGrouping.buildAlbums(listOf(camera, screenshot, video, downloads))
            .associateBy { it.title }

        assertEquals(4, albums.getValue("Recent").items.size)
        assertEquals(listOf(camera), albums.getValue("Camera").items)
        assertEquals(listOf(video), albums.getValue("Videos").items)
        assertEquals(listOf(screenshot), albums.getValue("Screenshots").items)
        assertEquals(listOf(downloads), albums.getValue("Download").items)
    }

    @Test
    fun `video-only collection does not include photos`() {
        val photo = item(1, "DCIM/Camera")
        val video = item(2, "DCIM/Camera", "VIDEO")
        assertEquals(listOf(video), GalleryCollectionGrouping.buildAlbums(listOf(photo, video)).first { it.title == "Videos" }.items)
    }

    @Test
    fun `an empty media library still exposes the standard albums`() {
        val titles = GalleryCollectionGrouping.buildAlbums(emptyList()).map { it.title }
        assertTrue(titles.containsAll(listOf("Recent", "Camera", "Videos", "Screenshots")))
    }
    @Test
    fun `folders with the same leaf name remain separate albums`() {
        val first = item(1, "Pictures/Exports")
        val second = item(2, "Download/Exports")
        val albums = GalleryCollectionGrouping.buildAlbums(listOf(first, second)).filter { it.title.startsWith("Exports") }

        assertEquals(2, albums.size)
        assertEquals(setOf(first, second), albums.flatMap { it.items }.toSet())
        assertEquals(2, albums.map { it.title }.distinct().size)
    }

    @Test
    fun `video mime type is recognized even when media type defaults to image`() {
        val item = MediaItem(
            id = 8,
            contentUri = "content://media/8",
            displayName = "clip.mp4",
            mimeType = "video/mp4",
            sizeBytes = 123,
            dateTakenEpochMillis = 1_700_000_000_000L,
            width = 1920,
            height = 1080,
            isFavorite = false,
        )
        assertTrue(item.isVideo)
    }

}
