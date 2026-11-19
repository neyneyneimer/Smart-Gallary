package ai.sakana.aura.gallery.domain.recognition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AiSetRulesTest {
    @Test
    fun `set names are trimmed before saving`() {
        assertEquals("Bedroom setup", AiSetRules.normalizeName("  Bedroom setup  "))
    }

    @Test
    fun `blank names are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            AiSetRules.normalizeName("   ")
        }
    }

    @Test
    fun `set names longer than sixty characters are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            AiSetRules.normalizeName("A".repeat(61))
        }
    }

    @Test
    fun `suggestion membership removes blank and repeated media ids while preserving order`() {
        assertEquals(
            listOf("media-a", "media-b"),
            AiSetRules.normalizeMembership(listOf("media-a", "", "media-a", " media-b ")),
        )
    }

    @Test
    fun `merging sets preserves target order then appends only new source members`() {
        assertEquals(
            listOf("a", "b", "c", "d"),
            AiSetRules.mergeMemberships(listOf("a", "b", "c"), listOf("b", "d", "a")),
        )
    }

    @Test
    fun `manually splitting a member removes only that media item`() {
        assertEquals(
            listOf("a", "c"),
            AiSetRules.removeMember(listOf("a", "b", "c", "b"), "b"),
        )
    }

    @Test
    fun `creating a saved set requires at least two distinct media items`() {
        assertThrows(IllegalArgumentException::class.java) {
            AiSetRules.requireSavableMembership(listOf("a", "a"))
        }
    }
}
