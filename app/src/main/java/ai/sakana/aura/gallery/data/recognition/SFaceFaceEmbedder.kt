package ai.sakana.aura.gallery.data.recognition

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.FloatBuffer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * SFace inference runs locally. The input bitmap and aligned crop never leave the device;
 * callers receive only a normalized feature vector for local storage and clustering.
 */
@Singleton
class SFaceFaceEmbedder @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val inferenceLock = Any()

    private val environment: OrtEnvironment by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        OrtEnvironment.getEnvironment()
    }

    private val session: OrtSession by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        val modelBytes = context.assets.open(MODEL_ASSET).use { it.readBytes() }
        val options = OrtSession.SessionOptions()
        try {
            environment.createSession(modelBytes, options)
        } finally {
            options.close()
        }
    }

    fun embed(bitmap: Bitmap, landmarks: FaceLandmarks): FloatArray? {
        val aligned = SFaceFaceAligner.align(bitmap, landmarks) ?: return null
        return try {
            synchronized(inferenceLock) { runModel(aligned) }
        } finally {
            aligned.recycle()
        }
    }

    private fun runModel(bitmap: Bitmap): FloatArray {
        val width = SFaceFaceAligner.INPUT_SIZE
        val pixels = IntArray(width * width)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, width)

        // SFace's OpenCV reference uses blobFromImage(scale=1, swapRB=true, mean=0).
        // The model therefore receives RGB NCHW floats in the original 0..255 range.
        val planeSize = pixels.size
        val inputValues = FloatArray(planeSize * 3)
        pixels.forEachIndexed { index, pixel ->
            inputValues[index] = Color.red(pixel).toFloat()
            inputValues[planeSize + index] = Color.green(pixel).toFloat()
            inputValues[(planeSize * 2) + index] = Color.blue(pixel).toFloat()
        }

        val activeSession = session
        val inputName = activeSession.inputNames.singleOrNull()
            ?: error("SFace model must expose exactly one input tensor.")
        val tensor = OnnxTensor.createTensor(
            environment,
            FloatBuffer.wrap(inputValues),
            longArrayOf(1, 3, width.toLong(), width.toLong()),
        )
        tensor.use { input ->
            activeSession.run(mapOf(inputName to input)).use { result ->
                val output = result.get(0) as? OnnxTensor
                    ?: error("SFace model did not return a tensor.")
                normalize(flatten(output.value))
            }
        }
    }

    private fun flatten(value: Any?): FloatArray = when (value) {
        is FloatArray -> value
        is Array<*> -> value.flatMap { flatten(it) }.toFloatArray()
        else -> error("Unsupported SFace output type: ${value?.javaClass?.name ?: "null"}")
    }

    private fun normalize(vector: FloatArray): FloatArray {
        require(vector.isNotEmpty() && vector.all(Float::isFinite)) {
            "SFace returned an empty or invalid face feature."
        }
        var normSquared = 0.0
        vector.forEach { normSquared += it.toDouble() * it.toDouble() }
        val norm = sqrt(normSquared).toFloat()
        require(norm > 1e-8f && norm.isFinite()) {
            "SFace returned a zero-length face feature."
        }
        return FloatArray(vector.size) { vector[it] / norm }
    }

    companion object {
        const val MODEL_VERSION = "opencv-sface-2021dec-int8-v1"
        private const val MODEL_ASSET = "sface_2021dec_int8.onnx"
    }
}
