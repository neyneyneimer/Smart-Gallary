package ai.sakana.aura.gallery.data.recognition

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SceneVectorCodecTest {
    @Test
    fun `encodes and decodes a feature vector without changing float values`() {
        val original = floatArrayOf(1f, -0.25f, 0.5f, 0f, 0.00390625f)
        assertArrayEquals(original, SceneVectorCodec.decode(SceneVectorCodec.encode(original), original.size), 0f)
    }

    @Test
    fun `rejects a vector with no components`() {
        assertThrows(IllegalArgumentException::class.java) { SceneVectorCodec.encode(floatArrayOf()) }
    }

    @Test
    fun `rejects non finite feature values`() {
        assertThrows(IllegalArgumentException::class.java) { SceneVectorCodec.encode(floatArrayOf(1f, Float.NaN)) }
    }

    @Test
    fun `rejects corrupted byte lengths`() {
        assertThrows(IllegalArgumentException::class.java) { SceneVectorCodec.decode(byteArrayOf(1, 2), 1) }
    }
}
