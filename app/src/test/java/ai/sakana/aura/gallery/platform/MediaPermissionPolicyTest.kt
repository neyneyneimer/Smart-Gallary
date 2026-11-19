package ai.sakana.aura.gallery.platform

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaPermissionPolicyTest {
    @Test
    fun `api 32 requests legacy external storage permission`() {
        assertEquals(listOf("android.permission.READ_EXTERNAL_STORAGE"), MediaPermissionPolicy.requiredPermissions(32))
    }

    @Test
    fun `api 33 requests both image and video permissions`() {
        assertEquals(
            listOf("android.permission.READ_MEDIA_IMAGES", "android.permission.READ_MEDIA_VIDEO"),
            MediaPermissionPolicy.requiredPermissions(33),
        )
    }

    @Test
    fun `api 34 and newer request image video and selected media permissions`() {
        assertEquals(
            listOf(
                "android.permission.READ_MEDIA_IMAGES",
                "android.permission.READ_MEDIA_VIDEO",
                "android.permission.READ_MEDIA_VISUAL_USER_SELECTED",
            ),
            MediaPermissionPolicy.requiredPermissions(34),
        )
    }

    @Test
    fun `both image and video grants mean full access`() {
        assertEquals(
            MediaAccessLevel.FULL,
            MediaPermissionPolicy.resolveAccess(34, setOf(
                "android.permission.READ_MEDIA_IMAGES",
                "android.permission.READ_MEDIA_VIDEO",
            )),
        )
    }

    @Test
    fun `one media grant without selected media grant is partial access`() {
        assertEquals(
            MediaAccessLevel.PARTIAL,
            MediaPermissionPolicy.resolveAccess(33, setOf("android.permission.READ_MEDIA_IMAGES")),
        )
    }

    @Test
    fun `selected media grant is partial access`() {
        assertEquals(
            MediaAccessLevel.PARTIAL,
            MediaPermissionPolicy.resolveAccess(34, setOf("android.permission.READ_MEDIA_VISUAL_USER_SELECTED")),
        )
    }

    @Test
    fun `no grant is denied`() {
        assertEquals(MediaAccessLevel.DENIED, MediaPermissionPolicy.resolveAccess(36, emptySet()))
    }
}
