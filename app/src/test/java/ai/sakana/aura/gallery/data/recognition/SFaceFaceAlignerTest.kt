package ai.sakana.aura.gallery.data.recognition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class SFaceFaceAlignerTest {
    private val target = listOf(
        FacePoint(38.2946, 51.6963),
        FacePoint(73.5318, 51.5014),
        FacePoint(56.0252, 71.7366),
        FacePoint(41.5493, 92.3655),
        FacePoint(70.7299, 92.2041),
    )

    @Test
    fun `canonical landmarks map to the reference template`() {
        val transform = SFaceFaceAligner.estimateTransform(target)
        assertNotNull(transform)
        target.forEach { point ->
            val mapped = transform!!.map(point)
            assertEquals(point.x, mapped.x, 1e-3)
            assertEquals(point.y, mapped.y, 1e-3)
        }
    }

    @Test
    fun `translated scaled and rotated landmarks map to the reference template`() {
        val angle = 0.19
        val scale = 1.7
        val cosine = cos(angle)
        val sine = sin(angle)
        val source = target.map { point ->
            FacePoint(
                x = scale * (cosine * point.x - sine * point.y) + 240.0,
                y = scale * (sine * point.x + cosine * point.y) - 80.0,
            )
        }

        val transform = SFaceFaceAligner.estimateTransform(source)
        assertNotNull(transform)
        source.zip(target).forEach { (sourcePoint, targetPoint) ->
            val mapped = transform!!.map(sourcePoint)
            assertEquals(targetPoint.x, mapped.x, 1e-3)
            assertEquals(targetPoint.y, mapped.y, 1e-3)
        }
    }

    @Test
    fun `degenerate and non finite geometry is rejected`() {
        assertNull(SFaceFaceAligner.estimateTransform(List(5) { FacePoint(10.0, 10.0) }))
        assertNull(
            SFaceFaceAligner.estimateTransform(
                listOf(FacePoint(Double.NaN, 0.0)) + target.drop(1),
            ),
        )
    }

    @Test
    fun `eye and mouth landmarks are ordered by image coordinate`() {
        val landmarks = FaceLandmarks(
            leftEye = FacePoint(70.0, 20.0),
            rightEye = FacePoint(30.0, 20.0),
            nose = FacePoint(50.0, 40.0),
            leftMouth = FacePoint(68.0, 60.0),
            rightMouth = FacePoint(32.0, 60.0),
        )
        assertEquals(
            listOf(30.0, 70.0, 50.0, 32.0, 68.0),
            landmarks.orderedForSFace().map { it.x },
        )
    }
}
