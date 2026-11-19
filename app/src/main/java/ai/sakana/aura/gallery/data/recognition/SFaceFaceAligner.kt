package ai.sakana.aura.gallery.data.recognition

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import kotlin.math.sqrt

internal data class FacePoint(val x: Double, val y: Double)

internal data class FaceLandmarks(
    val leftEye: FacePoint,
    val rightEye: FacePoint,
    val nose: FacePoint,
    val leftMouth: FacePoint,
    val rightMouth: FacePoint,
) {
    /** Order landmarks from the left edge of the image to the right edge. */
    fun orderedForSFace(): List<FacePoint> {
        val eyes = listOf(leftEye, rightEye).sortedBy { it.x }
        val mouth = listOf(leftMouth, rightMouth).sortedBy { it.x }
        return listOf(eyes[0], eyes[1], nose, mouth[0], mouth[1])
    }
}

internal data class FaceSimilarityTransform(
    val scale: Double,
    val cosine: Double,
    val sine: Double,
    val translateX: Double,
    val translateY: Double,
) {
    fun map(point: FacePoint): FacePoint = FacePoint(
        x = scale * (cosine * point.x - sine * point.y) + translateX,
        y = scale * (sine * point.x + cosine * point.y) + translateY,
    )

    fun toAndroidMatrix(): Matrix = Matrix().apply {
        setValues(
            floatArrayOf(
                (scale * cosine).toFloat(),
                (-scale * sine).toFloat(),
                translateX.toFloat(),
                (scale * sine).toFloat(),
                (scale * cosine).toFloat(),
                translateY.toFloat(),
                0f, 0f, 1f,
            ),
        )
    }
}

/**
 * Five-point similarity alignment to the 112x112 SFace reference template.
 * This is intentionally kept separate from scene embeddings.
 */
internal object SFaceFaceAligner {
    const val INPUT_SIZE = 112

    private val targetPoints = listOf(
        FacePoint(38.2946, 51.6963),
        FacePoint(73.5318, 51.5014),
        FacePoint(56.0252, 71.7366),
        FacePoint(41.5493, 92.3655),
        FacePoint(70.7299, 92.2041),
    )

    fun estimateTransform(sourcePoints: List<FacePoint>): FaceSimilarityTransform? {
        if (sourcePoints.size != targetPoints.size ||
            sourcePoints.any { !it.x.isFinite() || !it.y.isFinite() }
        ) return null

        val sourceMean = FacePoint(
            sourcePoints.sumOf { it.x } / targetPoints.size,
            sourcePoints.sumOf { it.y } / targetPoints.size,
        )
        val targetMean = FacePoint(
            targetPoints.sumOf { it.x } / targetPoints.size,
            targetPoints.sumOf { it.y } / targetPoints.size,
        )

        var a = 0.0
        var b = 0.0
        var c = 0.0
        var d = 0.0
        var sourceEnergy = 0.0
        sourcePoints.indices.forEach { index ->
            val sx = sourcePoints[index].x - sourceMean.x
            val sy = sourcePoints[index].y - sourceMean.y
            val tx = targetPoints[index].x - targetMean.x
            val ty = targetPoints[index].y - targetMean.y
            a += tx * sx
            b += tx * sy
            c += ty * sx
            d += ty * sy
            sourceEnergy += sx * sx + sy * sy
        }
        if (sourceEnergy <= EPSILON) return null

        val cosineTerm = a + d
        val sineTerm = c - b
        val magnitude = sqrt(cosineTerm * cosineTerm + sineTerm * sineTerm)
        if (!magnitude.isFinite() || magnitude <= EPSILON) return null

        val cosine = cosineTerm / magnitude
        val sine = sineTerm / magnitude
        val scale = magnitude / sourceEnergy
        if (!scale.isFinite() || scale <= 0.0) return null

        val translateX = targetMean.x -
            scale * (cosine * sourceMean.x - sine * sourceMean.y)
        val translateY = targetMean.y -
            scale * (sine * sourceMean.x + cosine * sourceMean.y)
        return FaceSimilarityTransform(scale, cosine, sine, translateX, translateY)
    }

    fun align(bitmap: Bitmap, landmarks: FaceLandmarks): Bitmap? {
        val transform = estimateTransform(landmarks.orderedForSFace()) ?: return null
        return Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888).also { output ->
            Canvas(output).drawBitmap(
                bitmap,
                transform.toAndroidMatrix(),
                Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG),
            )
        }
    }

    private const val EPSILON = 1e-8
}
