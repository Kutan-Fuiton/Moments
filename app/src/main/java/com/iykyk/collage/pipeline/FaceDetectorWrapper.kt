package com.iykyk.collage.pipeline

import android.graphics.Bitmap
import android.graphics.RectF
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.iykyk.collage.util.SharpnessUtil
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * High-recall coroutine wrapper around ML Kit face detector.
 *
 * Config:
 * - ACCURATE performance mode
 * - All landmarks + classifications enabled
 * - Low minFaceSize (0.06f) to reliably detect all subjects even if standing further away
 * - Built-in tracking enabled for intra-appearance temporal consistency
 */
class FaceDetectorWrapper {

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.05f) // High recall to capture everyone in frame, even further away
            .enableTracking()
            .build()
    )

    data class RawFace(
        val box: RectF,
        val yaw: Float,
        val roll: Float,
        val pitch: Float,
        val trackingId: Int?,
        val leftEyeOpen: Float?,
        val rightEyeOpen: Float?,
        val smiling: Float?,
        val sharpness: Float,
        val isFullyInFrame: Boolean,
    )

    suspend fun detect(bitmap: Bitmap): List<RawFace> = suspendCancellableCoroutine { cont ->
        val image = InputImage.fromBitmap(bitmap, 0)
        detector.process(image)
            .addOnSuccessListener { faces: List<Face> ->
                val result = faces.map { f -> toRawFace(f, bitmap) }
                cont.resume(result)
            }
            .addOnFailureListener { e -> cont.resumeWithException(e) }
    }

    private fun toRawFace(face: Face, bitmap: Bitmap): RawFace {
        val box = RectF(face.boundingBox)
        val margin = 0.15f
        val padded = RectF(box).apply {
            val dx = width() * margin
            val dy = height() * margin
            inset(-dx, -dy)
        }
        val isFullyInFrame = box.left > 2 && box.top > 2 &&
            box.right < bitmap.width - 2 && box.bottom < bitmap.height - 2

        return RawFace(
            box = box,
            yaw = face.headEulerAngleY,
            roll = face.headEulerAngleZ,
            pitch = face.headEulerAngleX,
            trackingId = face.trackingId,
            leftEyeOpen = face.leftEyeOpenProbability,
            rightEyeOpen = face.rightEyeOpenProbability,
            smiling = face.smilingProbability,
            sharpness = SharpnessUtil.laplacianVariance(bitmap, padded),
            isFullyInFrame = isFullyInFrame,
        )
    }

    fun close() = detector.close()
}
