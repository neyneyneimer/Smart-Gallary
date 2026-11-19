package ai.sakana.aura.gallery.data.recognition

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Stable little-endian float storage format for persisted scene vectors. */
object SceneVectorCodec {
    fun encode(vector: FloatArray): ByteArray {
        require(vector.isNotEmpty()) { "A scene vector cannot be empty." }
        require(vector.all(Float::isFinite)) { "A scene vector must contain only finite values." }
        val buffer = ByteBuffer.allocate(vector.size * Float.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        vector.forEach(buffer::putFloat)
        return buffer.array()
    }

    fun decode(bytes: ByteArray, dimensions: Int): FloatArray {
        require(dimensions > 0 && bytes.size == dimensions * Float.SIZE_BYTES) {
            "Stored scene vector has an invalid length."
        }
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(dimensions) { buffer.float }.also { vector ->
            require(vector.all(Float::isFinite)) { "Stored scene vector contains invalid values." }
        }
    }
}
