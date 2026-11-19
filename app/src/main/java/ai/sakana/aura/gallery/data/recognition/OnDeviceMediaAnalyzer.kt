package ai.sakana.aura.gallery.data.recognition

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import ai.sakana.aura.gallery.domain.model.MediaItem
import ai.sakana.aura.gallery.domain.recognition.DetectedFace
import ai.sakana.aura.gallery.domain.recognition.MediaAnalysisResult
import ai.sakana.aura.gallery.domain.recognition.SCENE_EMBEDDING_MODEL_VERSION
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceLandmark
import com.google.android.gms.tasks.Tasks
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imageembedder.ImageEmbedder
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.sqrt

/** All pixels and model inference stay on-device. The only returned data are numeric features and face rectangles. */
@Singleton
class OnDeviceMediaAnalyzer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val faceEmbedder: SFaceFaceEmbedder,
) {
    private val inferenceLock = Any()
    private val embedder: ImageEmbedder by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        val options = ImageEmbedder.ImageEmbedderOptions.builder()
            .setBaseOptions(
                BaseOptions.builder()
                    .setModelAssetPath("mobilenet_v3_small.tflite")
                    .setDelegate(Delegate.CPU)
                    .build(),
            )
            .setRunningMode(RunningMode.IMAGE)
            .setL2Normalize(true)
            .setQuantize(false)
            .build()
        ImageEmbedder.createFromOptions(context, options)
    }
    private val faceDetector: FaceDetector by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .enableTracking()
            .build()
        FaceDetection.getClient(options)
    }

    fun analyze(item: MediaItem): MediaAnalysisResult {
        val frames = if (item.isVideo) readVideoFrames(item) else listOf(0L to readImage(item.contentUri))
        if (frames.isEmpty()) throw IOException("No decodable frames were found for this media item.")
        val vectors = ArrayList<FloatArray>(frames.size)
        val faces = ArrayList<DetectedFace>()
        frames.forEach { (frameTimeMillis, bitmap) ->
            try {
                val result = synchronized(inferenceLock) {
                    embedder.embed(BitmapImageBuilder(bitmap).build())
                }
                val vector = result.embeddingResult().embeddings().firstOrNull()?.floatEmbedding()
                    ?: throw IllegalStateException("The scene model returned no embedding vector.")
                vectors += vector
                val detected = Tasks.await(faceDetector.process(InputImage.fromBitmap(bitmap, 0)))
                detected.forEachIndexed { faceIndex, face ->
                    val bounds = face.boundingBox
                    val left = (bounds.left.toFloat() / bitmap.width).coerceIn(0f, 1f)
                    val top = (bounds.top.toFloat() / bitmap.height).coerceIn(0f, 1f)
                    val right = (bounds.right.toFloat() / bitmap.width).coerceIn(0f, 1f)
                    val bottom = (bounds.bottom.toFloat() / bitmap.height).coerceIn(0f, 1f)
                    if (right > left && bottom > top) {
                        val landmarks = faceLandmarks(face)
                        val faceVector = if (bounds.width() >= MIN_FACE_EDGE && bounds.height() >= MIN_FACE_EDGE) {
                            landmarks?.let { faceEmbedder.embed(bitmap, it) }
                        } else {
                            null
                        }
                        faces += DetectedFace(
                            contentUri = item.contentUri,
                            frameTimeMillis = frameTimeMillis,
                            faceIndex = faceIndex,
                            leftNormalized = left,
                            topNormalized = top,
                            rightNormalized = right,
                            bottomNormalized = bottom,
                            yawDegrees = face.headEulerAngleY,
                            rollDegrees = face.headEulerAngleZ,
                            embeddingVector = faceVector,
                        )
                    }
                }
            } finally {
                bitmap.recycle()
            }
        }
        return MediaAnalysisResult(sceneVector = averageAndNormalize(vectors), detectedFaces = faces)
    }

    private fun faceLandmarks(face: Face): FaceLandmarks? {
        fun point(type: Int): FacePoint? = face.getLandmark(type)?.position?.let {
            FacePoint(it.x.toDouble(), it.y.toDouble())
        }
        val leftEye = point(FaceLandmark.LEFT_EYE) ?: return null
        val rightEye = point(FaceLandmark.RIGHT_EYE) ?: return null
        val nose = point(FaceLandmark.NOSE_BASE) ?: return null
        val leftMouth = point(FaceLandmark.MOUTH_LEFT) ?: return null
        val rightMouth = point(FaceLandmark.MOUTH_RIGHT) ?: return null
        return FaceLandmarks(leftEye, rightEye, nose, leftMouth, rightMouth)
    }

    private fun readImage(contentUri: String): Bitmap {
        val uri = Uri.parse(contentUri)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsStream = context.contentResolver.openInputStream(uri)
            ?: throw IOException("The selected photo is no longer readable: $uri")
        boundsStream.use { input ->
            // Bounds-only decoding intentionally returns null; the stream itself is the success condition.
            BitmapFactory.decodeStream(input, null, bounds)
            Unit
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("The photo dimensions could not be read: $uri")
        val sampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight)
        val orientation = runCatching {
            context.contentResolver.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
                ?: ExifInterface.ORIENTATION_NORMAL
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val decoded = context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, BitmapFactory.Options().apply { inSampleSize = sampleSize })
        } ?: throw IOException("The selected photo could not be decoded.")
        return applyExifOrientation(decoded, orientation)
    }

    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.setRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.setRotate(-90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            .also { if (it !== bitmap) bitmap.recycle() }
    }

    private fun readVideoFrames(item: MediaItem): List<Pair<Long, Bitmap>> {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, Uri.parse(item.contentUri))
            val durationMs = item.durationMillis.takeIf { it > 0L }
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                ?: 0L
            if (durationMs <= 0L) throw IOException("The video duration could not be read.")
            val samplePoints = listOf(0.18, 0.5, 0.82).map { fraction -> (durationMs * fraction).toLong() }
            return samplePoints.distinct().mapNotNull { timeMs ->
                val frame = if (Build.VERSION.SDK_INT >= 27) {
                    retriever.getScaledFrameAtTime(timeMs * 1_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, MAX_FRAME_EDGE, MAX_FRAME_EDGE)
                } else {
                    retriever.getFrameAtTime(timeMs * 1_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let(::scaleFrame)
                }
                frame?.let { timeMs to it }
            }.also { if (it.isEmpty()) throw IOException("No readable video frames were found.") }
        } catch (error: SecurityException) {
            throw IOException("Aura no longer has access to this video.", error)
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun scaleFrame(frame: Bitmap): Bitmap {
        val scale = minOf(1f, MAX_FRAME_EDGE.toFloat() / max(frame.width, frame.height))
        if (scale >= 1f) return frame
        return Bitmap.createScaledBitmap(
            frame,
            (frame.width * scale).toInt().coerceAtLeast(1),
            (frame.height * scale).toInt().coerceAtLeast(1),
            true,
        ).also { frame.recycle() }
    }

    private fun averageAndNormalize(vectors: List<FloatArray>): FloatArray {
        val dimensions = vectors.firstOrNull()?.size ?: throw IllegalStateException("No scene vectors were produced.")
        require(vectors.all { it.size == dimensions }) { "The scene model returned inconsistent vector lengths." }
        val average = FloatArray(dimensions)
        vectors.forEach { vector -> vector.forEachIndexed { index, value -> average[index] += value / vectors.size } }
        var normSquared = 0.0
        average.forEach { value -> normSquared += value.toDouble() * value.toDouble() }
        val norm = sqrt(normSquared).toFloat()
        if (norm <= 1e-8f) throw IllegalStateException("The scene model returned an empty feature vector.")
        average.indices.forEach { index -> average[index] /= norm }
        return average
    }

    private fun calculateSampleSize(width: Int, height: Int): Int {
        var sample = 1
        while (max(width / (sample * 2), height / (sample * 2)) >= MAX_FRAME_EDGE) sample *= 2
        return sample
    }

    companion object {
        const val MODEL_VERSION = SCENE_EMBEDDING_MODEL_VERSION
        private const val MAX_FRAME_EDGE = 768
        private const val MIN_FACE_EDGE = 48
    }
}
