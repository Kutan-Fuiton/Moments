package com.iykyk.collage.model

import android.graphics.Bitmap
import android.graphics.RectF

/**
 * One face detected in one sampled frame.
 *
 * [frameBitmap] is the FULL frame (never a tight face crop) — the spec explicitly calls
 * out that tight crops to the bounding box give low-res, ugly collage tiles. We keep the
 * whole frame around and only compute a generous crop lazily when building the collage.
 */
data class FaceObservation(
    val frameTimestampMs: Long,
    val frameBitmap: Bitmap,
    val boundingBox: RectF,          // face box in frameBitmap coordinates
    val embedding: FloatArray,       // 128-d (or 192-d) L2-normalized embedding
    val headEulerAngleY: Float,      // yaw: 0 = frontal
    val headEulerAngleZ: Float,      // roll
    val headEulerAngleX: Float = 0f, // pitch: 0 = frontal
    val trackingId: Int? = null,     // ML Kit tracking ID for intra-appearance tracking
    val leftEyeOpenProbability: Float?,
    val rightEyeOpenProbability: Float?,
    val smilingProbability: Float?,
    val sharpness: Float,            // variance-of-Laplacian on the face region, higher = sharper
    val isFullyInFrame: Boolean,     // false if box touches/clips a frame edge
) {
    /** 0..1, higher = more front-facing (accounting for both yaw and pitch). */
    val frontality: Float
        get() {
            val yaw = kotlin.math.abs(headEulerAngleY).coerceIn(0f, 45f)
            val pitch = kotlin.math.abs(headEulerAngleX).coerceIn(0f, 30f)
            return (1f - (yaw / 45f + pitch / 30f) / 2f).coerceIn(0f, 1f)
        }

    val eyesOpenScore: Float
        get() {
            val l = leftEyeOpenProbability ?: 0.5f
            val r = rightEyeOpenProbability ?: 0.5f
            return (l + r) / 2f
        }

    override fun equals(other: Any?) = this === other
    override fun hashCode() = System.identityHashCode(this)
}

/** A continuous visible run of the same identity, per the assignment's counting rule. */
data class Appearance(
    val startMs: Long,
    val endMs: Long,
    val observations: List<FaceObservation>,
)

/** One unique person detected in a video, with every appearance and a chosen best shot. */
data class PersonCluster(
    val id: Int,
    val centroidEmbedding: FloatArray,
    val appearances: MutableList<Appearance> = mutableListOf(),
    var representative: FaceObservation? = null,
) {
    val appearanceCount: Int get() = appearances.size
}

/** Progress reported from the background pipeline up to the UI. */
sealed class ProcessingStage(val label: String) {
    data object ExtractingFrames : ProcessingStage("Extracting frames")
    data object DetectingFaces : ProcessingStage("Finding faces")
    data object ComputingEmbeddings : ProcessingStage("Analyzing features")
    data object Clustering : ProcessingStage("Matching people")
    data object SelectingShots : ProcessingStage("Choosing best moments")
    data object BuildingCollage : ProcessingStage("Creating your collage")
    data object Done : ProcessingStage("Done")
}

data class ProcessingProgress(
    val stage: ProcessingStage,
    val current: Int = 0,
    val total: Int = 0,
) {
    val fraction: Float get() = if (total == 0) 0f else current.toFloat() / total
}

data class VideoResult(
    val videoLabel: String,
    val people: List<PersonCluster>,
    val collageBitmap: Bitmap,
)
