package ai.sakana.aura.gallery.presentation

import org.junit.Assert.assertFalse
import org.junit.Test

class FeatureFlagsTest {
    @Test
    fun `unfinished integrations remain disabled by default`() {
        assertFalse(FeatureFlags.cloudBackupEnabled)
        assertFalse(FeatureFlags.megaEnabled)
        assertFalse(FeatureFlags.teraboxEnabled)
        assertFalse(FeatureFlags.videoFrameAnalysisEnabled)
    }
}
