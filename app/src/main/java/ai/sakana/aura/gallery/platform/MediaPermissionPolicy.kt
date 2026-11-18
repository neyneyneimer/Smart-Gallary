package ai.sakana.aura.gallery.platform

/** Describes the actual visibility Android granted for the image library. */
enum class MediaAccessLevel {
    FULL,
    PARTIAL,
    DENIED,
}

/** Pure policy for Android's versioned photo-library permission model. */
object MediaPermissionPolicy {
    const val READ_EXTERNAL_STORAGE = "android.permission.READ_EXTERNAL_STORAGE"
    const val READ_MEDIA_IMAGES = "android.permission.READ_MEDIA_IMAGES"
    const val READ_MEDIA_VISUAL_USER_SELECTED = "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"

    fun requiredPermissions(apiLevel: Int): List<String> = when {
        apiLevel >= 34 -> listOf(READ_MEDIA_IMAGES, READ_MEDIA_VISUAL_USER_SELECTED)
        apiLevel >= 33 -> listOf(READ_MEDIA_IMAGES)
        else -> listOf(READ_EXTERNAL_STORAGE)
    }

    fun resolveAccess(apiLevel: Int, grantedPermissions: Set<String>): MediaAccessLevel {
        val hasFullAccess = if (apiLevel >= 33) {
            READ_MEDIA_IMAGES in grantedPermissions
        } else {
            READ_EXTERNAL_STORAGE in grantedPermissions
        }
        if (hasFullAccess) return MediaAccessLevel.FULL

        val hasPartialAccess = apiLevel >= 34 &&
            READ_MEDIA_VISUAL_USER_SELECTED in grantedPermissions
        return if (hasPartialAccess) MediaAccessLevel.PARTIAL else MediaAccessLevel.DENIED
    }
}
