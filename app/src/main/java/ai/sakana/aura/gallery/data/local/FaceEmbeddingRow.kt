package ai.sakana.aura.gallery.data.local

/** Joined row used by local person-identity clustering and review suggestions. */
data class FaceEmbeddingRow(
    val faceId: Long,
    val contentUri: String,
    val frameTimeMillis: Long,
    val faceIndex: Int,
    val yawDegrees: Float,
    val rollDegrees: Float,
    val modelVersion: String,
    val vectorBytes: ByteArray,
    val dimensions: Int,
)
