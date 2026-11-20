package ai.sakana.aura.gallery.design

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuraToneColorMatrixTest {
    @Test
    fun neutralAdjustmentsProduceIdentityMatrix() {
        val identity = floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
        assertArrayEquals(identity, AuraToneColorMatrix.create(0f, 0f, 0f, 0f).values, 0.0001f)
    }

    @Test
    fun brightnessAddsAnEqualRgbTranslation() {
        val values = AuraToneColorMatrix.create(0.5f, 0f, 0f, 0f).values
        assertTrue(values[4] > 0f)
        assertTrue(values[9] > 0f)
        assertTrue(values[14] > 0f)
        assertTrue(values[4] == values[9] && values[9] == values[14])
    }

    @Test
    fun warmthAdjustsRedAndBlueChannelsInOppositeDirections() {
        val warm = AuraToneColorMatrix.create(0f, 0f, 0f, 1f).values
        assertTrue(warm[0] > 1f)
        assertTrue(warm[12] < 1f)
    }
}
