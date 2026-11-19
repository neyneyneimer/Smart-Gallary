package ai.sakana.aura.gallery.platform

/** Describes the actual visibility Android granted for image and video collections. */
enum class MediaAccessLevel { FULL, PARTIAL, DENIED }

/** Pure policy for Android's versioned shared-media permission model. */
object MediaPermissionPolicy {
    const val READ_EXTERNAL_STORAGE = "android.permission.READ_EXTERNAL_STORAGE"
    const val READ_MEDIA_IMAGES = "android.permission.READ_MEDIA_IMAGES"
    const val READ_MEDIA_VIDEO = "android.permission.READ_MEDIA_VIDEO"
    const val READ_MEDIA_VISUAL_USER_SELECTED = "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"

    fun requiredPermissions(apiLevel: Int): List<String> = when {
        apiLevel >= 34 -> listOf(READ_MEDIA_IMAGES, READ_MEDIA_VIDEO, READ_MEDIA_VISUAL_USER_SELECTED)
        apiLevel >= 33 -> listOf(READ_MEDIA_IMAGES, READ_MEDIA_VIDEO)
        else -> listOf(READ_EXTERNAL_STORAGE)
    }

    fun resolveAccess(apiLevel: Int, grantedPermissions: Set<String>): MediaAccessLevel {
        if (apiLevel < 33) {
            return if (READ_EXTERNAL_STORAGE in grantedPermissions) MediaAccessLevel.FULL else MediaAccessLevel.DENIED
        }
        val hasImages = READ_MEDIA_IMAGES in grantedPermissions
        val hasVideos = READ_MEDIA_VIDEO in grantedPermissions
        if (hasImages && hasVideos) return MediaAccessLevel.FULL
        if (apiLevel >= 34 && READ_MEDIA_VISUAL_USER_SELECTED in grantedPermissions) return MediaAccessLevel.PARTIAL
        return if (hasImages || hasVideos) MediaAccessLevel.PARTIAL else MediaAccessLevel.DENIED
    }
}
