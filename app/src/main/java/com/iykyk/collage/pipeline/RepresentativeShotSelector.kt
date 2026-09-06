package com.iykyk.collage.pipeline

import com.iykyk.collage.model.FaceObservation
import com.iykyk.collage.model.PersonCluster
import kotlin.math.abs

/**
 * Representative Shot Selection Engine per Section 4 of face_pipeline_improvement_plan.md:
 *
 * score(frame) = w1*frontality + w2*sharpness + w3*eyesOpen + w4*smile + w5*faceCompleteness
 *
 * - Frontality (0.30): derived from headEulerAngleY (yaw) and headEulerAngleX (pitch).
 * - Sharpness (0.30): variance of Laplacian on the face region, normalized against video max.
 * - Eyes Open (0.25): average of left and right eye probabilities. Blinks/squints heavily penalized.
 * - Face Completeness (0.10): hard-rejects/heavily penalizes clipped faces.
 * - Smile (0.05): pleasant expression tiebreaker.
 *
 * Picks the top-scoring frame across all appearances of each person.
 */
object RepresentativeShotSelector {

    private const val W_FRONTALITY = 0.30f
    private const val W_SHARPNESS = 0.30f
    private const val W_EYES_OPEN = 0.25f
    private const val W_COMPLETENESS = 0.10f
    private const val W_SMILE = 0.05f

    private const val CLIPPED_FACE_PENALTY = 0.40f
    private const val CLOSED_EYES_PENALTY = 0.35f

    fun pickBest(
        cluster: PersonCluster,
        maxSharpnessAcrossVideo: Float,
        maxFaceAreaAcrossVideo: Float = 0f,
    ): FaceObservation? {
        val all = cluster.appearances.flatMap { it.observations }
        if (all.isEmpty()) return null

        val maxArea = if (maxFaceAreaAcrossVideo > 0f) {
            maxFaceAreaAcrossVideo
        } else {
            all.maxOfOrNull { it.boundingBox.width() * it.boundingBox.height() } ?: 1f
        }

        return all.maxByOrNull { score(it, maxSharpnessAcrossVideo, maxArea) } ?: all.firstOrNull()
    }

    private fun score(obs: FaceObservation, maxSharpness: Float, maxFaceArea: Float): Float {
        // 1. Frontality: 1 - (|yaw|/maxYaw + |pitch|/maxPitch)/2
        val yaw = abs(obs.headEulerAngleY).coerceIn(0f, 45f)
        val pitch = abs(obs.headEulerAngleX).coerceIn(0f, 30f)
        val frontality = (1f - (yaw / 45f + pitch / 30f) / 2f).coerceIn(0f, 1f)

        // 2. Sharpness: normalized variance of Laplacian
        val normSharpness = if (maxSharpness > 0f) (obs.sharpness / maxSharpness).coerceIn(0f, 1f) else 0.5f

        // 3. Eyes Open: average probability
        val leftEye = obs.leftEyeOpenProbability ?: 0.6f
        val rightEye = obs.rightEyeOpenProbability ?: 0.6f
        val eyesOpen = ((leftEye + rightEye) / 2f).coerceIn(0f, 1f)

        // 4. Smile: minor tiebreaker
        val smile = (obs.smilingProbability ?: 0.3f).coerceIn(0f, 1f)

        // 5. Face completeness: fully in frame check
        val completeness = if (obs.isFullyInFrame) 1f else 0f

        // 6. Resolution weight bonus
        val faceArea = obs.boundingBox.width() * obs.boundingBox.height()
        val areaRatio = if (maxFaceArea > 0f) (faceArea / maxFaceArea).coerceIn(0f, 1f) else 0.5f

        var total = W_FRONTALITY * frontality +
            W_SHARPNESS * normSharpness +
            W_EYES_OPEN * eyesOpen +
            W_COMPLETENESS * completeness +
            W_SMILE * smile +
            0.10f * areaRatio

        if (!obs.isFullyInFrame) total -= CLIPPED_FACE_PENALTY
        if (leftEye < 0.35f || rightEye < 0.35f) total -= CLOSED_EYES_PENALTY

        return total
    }
}
