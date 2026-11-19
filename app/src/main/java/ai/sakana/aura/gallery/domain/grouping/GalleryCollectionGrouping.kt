package ai.sakana.aura.gallery.domain.grouping

import ai.sakana.aura.gallery.domain.model.MediaItem

data class MediaAlbum(val title: String, val items: List<MediaItem>, val isVirtual: Boolean = false)

object GalleryCollectionGrouping {
    fun buildAlbums(items: List<MediaItem>): List<MediaAlbum> {
        val recent = items.sortedByDescending(MediaItem::dateTakenEpochMillis)
        val camera = recent.filter { it.relativePath.contains("DCIM/Camera", ignoreCase = true) || it.bucketName.equals("Camera", true) }
        val videos = recent.filter(MediaItem::isVideo)
        val screenshots = recent.filter { it.relativePath.contains("Screenshots", true) || it.bucketName.contains("Screenshot", true) }
        val albums = mutableListOf(
            MediaAlbum("Recent", recent, isVirtual = true),
            MediaAlbum("Camera", camera, isVirtual = true),
            MediaAlbum("Videos", videos, isVirtual = true),
            MediaAlbum("Screenshots", screenshots, isVirtual = true),
        )
        val reserved = setOf("camera", "screenshots")
        val folderGroups = recent.groupBy { item ->
            item.relativePath.trim('/').ifBlank { item.bucketName.trim().ifBlank { "Other" } }
        }.toSortedMap(String.CASE_INSENSITIVE_ORDER)
        val duplicateNames = folderGroups.keys.groupingBy { it.substringAfterLast('/').ifBlank { "Other" }.lowercase() }
            .eachCount().filterValues { it > 1 }.keys
        folderGroups.forEach { (folderPath, contents) ->
            val folderName = folderPath.substringAfterLast('/').ifBlank { "Other" }
            if (folderName.lowercase() !in reserved && contents.isNotEmpty()) {
                val title = if (folderName.lowercase() in duplicateNames) {
                    "$folderName · ${folderPath.substringBeforeLast('/', "Storage") }"
                } else folderName
                albums += MediaAlbum(title, contents)
            }
        }
        return albums
    }
}
