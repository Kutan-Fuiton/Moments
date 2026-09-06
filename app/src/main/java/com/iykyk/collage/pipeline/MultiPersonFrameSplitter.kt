package com.iykyk.collage.pipeline

import android.graphics.Bitmap
import android.graphics.RectF
import com.iykyk.collage.model.FaceObservation
import com.iykyk.collage.model.PersonCluster
import kotlin.math.max
import kotlin.math.min

/**
 * Replaces each person's representative frame bitmap with a single-person crop
 * when the representative frame also contains other detected people.
 *
 * Problem this solves: the best-scored frame for Person A might be a group shot
 * where Person B is standing right next to them. The collage tile then shows two
 * faces (or a blurry combined region). This splitter detects multi-person frames
 * and surgically crops just the target person out.
 *
 * Algorithm:
 * 1. Build a map from (frameTimestampMs → all face observations in that frame).
 * 2. For each cluster's representative, if its frame has 2+ observations from
 *    different clusters, replace the representative's frameBitmap with a fresh
 *    single-person crop using the [CROP_FACTOR] generous head-and-shoulders margin.
 * 3. If the resulting crop would be too small (<80px) — e.g. a tiny face in the
 *    corner — keep the original to avoid producing a blurry postage stamp.
 */
object MultiPersonFrameSplitter {

    private const val CROP_FACTOR = 2.4f
    private const val MIN_CROP_PX = 80

    fun clean(
        clusters: List<PersonCluster>,
        observationsByFrame: List<Pair<Long, List<FaceObservation>>>,
    ) {
        // Build timestamp → observations map
        val frameMap = buildMap<Long, List<FaceObservation>> {
            for ((ts, obs) in observationsByFrame) {
                put(ts, obs)
            }
        }

        for (cluster in clusters) {
            val rep = cluster.representative ?: continue
            val frameObs = frameMap[rep.frameTimestampMs] ?: continue

            // Only act if there are 2+ face observations in this frame (multi-person)
            if (frameObs.size < 2) continue

            // Produce a generous single-person crop around this cluster's rep face
            val crop = singlePersonCrop(rep) ?: continue
            if (crop.width < MIN_CROP_PX || crop.height < MIN_CROP_PX) {
                crop.recycle()
                continue
            }

            // Replace the representative with a synthetic FaceObservation that
            // points to this isolated crop bitmap — bounding box fills the whole crop
            val newBox = RectF(0f, 0f, crop.width.toFloat(), crop.height.toFloat())
            cluster.representative = rep.copy(
                frameBitmap = crop,
                boundingBox = newBox,
                isFullyInFrame = true,
            )
        }
    }

    private fun singlePersonCrop(obs: FaceObservation): Bitmap? {
        val frame = obs.frameBitmap
        if (frame.isRecycled) return null

        val box = obs.boundingBox
        val cx = (box.left + box.right) / 2f
        val cy = (box.top + box.bottom) / 2f
        val faceSize = max(box.right - box.left, box.bottom - box.top)
        val side = min(
            min(frame.width.toFloat(), frame.height.toFloat()),
            faceSize * CROP_FACTOR,
        )

        val left = (cx - side / 2f).coerceIn(0f, max(0f, frame.width - side))
        val top  = (cy - side / 2f).coerceIn(0f, max(0f, frame.height - side))

        return try {
            Bitmap.createBitmap(frame, left.toInt(), top.toInt(), side.toInt(), side.toInt())
        } catch (e: Exception) {
            null
        }
    }
}
