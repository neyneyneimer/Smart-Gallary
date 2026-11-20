package ai.sakana.aura.gallery.domain.editing

import ai.sakana.aura.gallery.data.local.EditHistoryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditHistoryRulesTest {
    private val preset = EditHistoryEntity(id = 7, contentUri = "content://m/1", name = "Warm", brightness = 0.1f, contrast = 0f, saturation = -0.05f, warmth = 0.2f, appliedAt = 100L, isActive = true)

    @Test fun namesAreTrimmedCollapsedAndCapped() {
        assertEquals("My edit", EditHistoryRules.normalizeName("  My    edit \n"))
        assertEquals(EditHistoryRules.MAX_NAME_LENGTH, EditHistoryRules.normalizeName("x".repeat(100)).length)
        assertEquals("Edit 3", EditHistoryRules.defaultName(2))
    }

    @Test fun summaryShowsSignedPercentages() {
        assertEquals("B +10 · C 0 · S -5 · W +20", EditHistoryRules.summary(preset))
    }

    @Test fun activePresetCannotBeDeleted() {
        assertNotNull(EditHistoryRules.deleteBlockedReason(preset))
        assertNull(EditHistoryRules.deleteBlockedReason(preset.copy(isActive = false)))
    }

    @Test fun reapplyCreatesNewActiveEntryAndLeavesSourceAlone() {
        val applied = EditHistoryRules.reapply(preset.copy(isActive = false), now = 500L)
        assertEquals(0L, applied.id)
        assertTrue(applied.isActive)
        assertEquals(500L, applied.appliedAt)
        assertEquals(preset.name, applied.name)
        assertEquals(EditHistoryRules.params(preset), EditHistoryRules.params(applied))
    }

    @Test fun branchCopiesParamsWithNewNameInactive() {
        val branch = EditHistoryRules.branch(preset, "  Cooler  ", now = 900L)
        assertEquals("Cooler", branch.name)
        assertFalse(branch.isActive)
        assertEquals(0L, branch.id)
        assertEquals(900L, branch.appliedAt)
        assertEquals(preset.warmth, branch.warmth, 0f)
        assertEquals("Warm copy", EditHistoryRules.branch(preset, "   ", 1L).name)
    }

    @Test fun newPresetFromSlidersFallsBackToUntitledName() {
        val p = EditHistoryRules.newPreset("content://m/2", "   ", EditParams(warmth = 0.3f), now = 5L)
        assertEquals("Untitled edit", p.name)
        assertTrue(p.isActive)
        assertEquals(0.3f, p.warmth, 0f)
    }

    @Test fun identityParamsAreDetected() {
        assertTrue(EditParams().isIdentity)
        assertFalse(EditParams(contrast = 0.01f).isIdentity)
    }
}
