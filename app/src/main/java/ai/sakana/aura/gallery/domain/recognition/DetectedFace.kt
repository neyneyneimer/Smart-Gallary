package ai.sakana.aura.gallery.domain.recognition

/** Normalized face rectangle from one image or sampled video frame; it is not a person identity. */
data class DetectedFace(
    val contentUri: String,
    val frameTimeMillis: Long,
    val faceIndex: Int,
    val leftNormalized: Float,
    val topNormalized: Float,
    val rightNormalized: Float,
    val bottomNormalized: Float,
    val yawDegrees: Float,
    val rollDegrees: Float,
    /** Unit-normalized on-device SFace features; null for missing landmarks or undersized faces. */
    val embeddingVector: FloatArray? = null,
)

data class MediaAnalysisResult(
    val sceneVector: FloatArray,
    val detectedFaces: List<DetectedFace>,
)

const val SCENE_EMBEDDING_MODEL_VERSION = "mediapipe-mobilenet-v3-small-float32-1"
