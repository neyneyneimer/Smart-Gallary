package ai.sakana.aura.gallery.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaPermissionPolicyTest {
    @Test
    fun `api 32 requests legacy external storage permission`() {
        assertEquals(
            listOf("android.permission.READ_EXTERNAL_STORAGE"),
            MediaPermissionPolicy.requiredPermissions(32),
        )
    }

    @Test
    fun `api 33 requests images permission`() {
        assertEquals(
            listOf("android.permission.READ_MEDIA_IMAGES"),
            MediaPermissionPolicy.requiredPermissions(33),
        )
    }

    @Test
    fun `api 34 and newer request full and selected photo access together`() {
        assertEquals(
            listOf(
                "android.permission.READ_MEDIA_IMAGES",
                "android.permission.READ_MEDIA_VISUAL_USER_SELECTED",
            ),
            MediaPermissionPolicy.requiredPermissions(34),
        )
        assertTrue(MediaPermissionPolicy.requiredPermissions(36).contains(
            "android.permission.READ_MEDIA_VISUAL_USER_SELECTED",
        ))
    }

    @Test
    fun `full media access takes precedence when both grants exist`() {
        assertEquals(
            MediaAccessLevel.FULL,
            MediaPermissionPolicy.resolveAccess(34, setOf(
                "android.permission.READ_MEDIA_IMAGES",
                "android.permission.READ_MEDIA_VISUAL_USER_SELECTED",
            )),
        )
    }

    @Test
    fun `selected media access is a supported partial state`() {
        assertEquals(
            MediaAccessLevel.PARTIAL,
            MediaPermissionPolicy.resolveAccess(34, setOf(
                "android.permission.READ_MEDIA_VISUAL_USER_SELECTED",
            )),
        )
    }

    @Test
    fun `no grant is denied`() {
        assertEquals(MediaAccessLevel.DENIED, MediaPermissionPolicy.resolveAccess(36, emptySet()))
    }
}
