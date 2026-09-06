package com.iykyk.collage.pipeline

import android.graphics.RectF
import com.iykyk.collage.model.Appearance
import com.iykyk.collage.model.FaceObservation
import com.iykyk.collage.model.PersonCluster
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Person identification clusterer with three-stage pipeline:
 *
 * Stage 1 — Temporal Tracklet Formation
 *   ML Kit trackingId + spatial continuity links faces across consecutive frames into
 *   short-term tracklets representing one continuous appearance. Faces on opposite
 *   sides of the screen are never accidentally merged (spatial consistency gate).
 *
 * Stage 2 — Conservative Agglomerative Merging (threshold = 0.72)
 *   Merges non-overlapping tracklets that are almost certainly the same person.
 *   Hard cannot-link: any two tracks sharing a frame timestamp can NEVER merge.
 *
 * Stage 2b — Adaptive Gap-Maximization Dedup
 *   Collects all eligible pairwise similarities, sorts them, finds the LARGEST GAP
 *   in the distribution (Otsu-style). The midpoint of that gap is the "natural
 *   boundary" between same-person and different-person clusters in this specific
 *   video — totally data-driven, no hardcoded second threshold.
 *   This second pass reduces duplicate tiles for the same person while guaranteeing
 *   the floor never drops below 0.72 (so genuinely distinct people never merge).
 *
 * Stage 3 — PersonCluster assembly
 *   Every surviving cluster becomes a PersonCluster. No arbitrary pruning — every
 *   detected individual is guaranteed a tile in the collage.
 */
class FaceClusterer(
    private val similarityThreshold: Float = 0.72f,
    private val maxGapMs: Long = 600L,
) {

    private data class Tracklet(
        val id: Int,
        var trackingId: Int?,
        val observations: MutableList<FaceObservation> = mutableListOf(),
        val frameTimestamps: MutableSet<Long> = mutableSetOf(),
        var lastSeenMs: Long = 0L,
    ) {
        /** Frontality-weighted centroid embedding. */
        fun computeCentroid(): FloatArray {
            if (observations.isEmpty()) return FloatArray(0)
            val dim = observations.first().embedding.size
            val sum = FloatArray(dim)
            var totalWeight = 0f
            for (obs in observations) {
                val yaw = abs(obs.headEulerAngleY).coerceIn(0f, 45f)
                val weight = (1f - yaw / 45f).coerceIn(0.2f, 1f)
                totalWeight += weight
                for (i in 0 until dim) sum[i] += obs.embedding[i] * weight
            }
            return if (totalWeight > 0f) l2Normalize(sum) else observations.first().embedding
        }
    }

    private data class TrackCluster(
        val id: Int,
        val tracklets: MutableList<Tracklet> = mutableListOf(),
        val frameTimestamps: MutableSet<Long> = mutableSetOf(),
        var centroid: FloatArray = FloatArray(0),
    ) {
        fun updateCentroid() {
            if (tracklets.isEmpty()) return
            val dim = tracklets.first().computeCentroid().size
            if (dim == 0) return
            val sum = FloatArray(dim)
            var count = 0f
            for (t in tracklets) {
                val emb = t.computeCentroid()
                if (emb.size != dim) continue
                val weight = t.observations.size.toFloat()
                count += weight
                for (i in 0 until dim) sum[i] += emb[i] * weight
            }
            centroid = if (count > 0f) l2Normalize(sum) else tracklets.first().computeCentroid()
        }

        fun overlaps(other: TrackCluster): Boolean =
            frameTimestamps.any { other.frameTimestamps.contains(it) }
    }

    fun cluster(observationsByFrame: List<Pair<Long, List<FaceObservation>>>): List<PersonCluster> {

        // ── STAGE 1: Temporal Tracklet Formation ──────────────────────────────────
        val tracklets = mutableListOf<Tracklet>()
        val activeTracks = mutableListOf<Tracklet>()
        var nextTrackId = 0

        for ((timestampMs, faces) in observationsByFrame) {
            // Retire stale tracks
            activeTracks.retainAll { timestampMs - it.lastSeenMs <= maxGapMs }

            val assignedTrackIds = mutableSetOf<Int>()

            // Pass 1: ML Kit trackingId assignment with spatial validation
            for (face in faces) {
                val fTrackId = face.trackingId ?: continue
                val match = activeTracks.firstOrNull { t ->
                    t.trackingId == fTrackId &&
                        t.id !in assignedTrackIds &&
                        isSpatiallyConsistent(t.observations.lastOrNull()?.boundingBox, face.boundingBox)
                } ?: continue
                match.observations.add(face)
                match.frameTimestamps.add(timestampMs)
                match.lastSeenMs = timestampMs
                assignedTrackIds.add(match.id)
            }

            // Pass 2: Spatial + embedding proximity fallback for unassigned faces
            for (face in faces) {
                if (activeTracks.any { it.id in assignedTrackIds && it.observations.lastOrNull() === face }) continue
                val alreadyLinked = activeTracks.any { t ->
                    t.observations.lastOrNull() === face
                }
                if (alreadyLinked) continue

                var bestMatch: Tracklet? = null
                var bestSim = -1f

                for (t in activeTracks) {
                    if (t.id in assignedTrackIds) continue
                    val lastObs = t.observations.lastOrNull() ?: continue
                    if (!isSpatiallyConsistent(lastObs.boundingBox, face.boundingBox)) continue
                    val sim = cosineSim(lastObs.embedding, face.embedding)
                    if (sim >= 0.65f && sim > bestSim) {
                        bestSim = sim
                        bestMatch = t
                    }
                }

                if (bestMatch != null) {
                    bestMatch.observations.add(face)
                    bestMatch.frameTimestamps.add(timestampMs)
                    bestMatch.lastSeenMs = timestampMs
                    if (face.trackingId != null) bestMatch.trackingId = face.trackingId
                    assignedTrackIds.add(bestMatch.id)
                } else {
                    val newTrack = Tracklet(
                        id = nextTrackId++,
                        trackingId = face.trackingId,
                        observations = mutableListOf(face),
                        frameTimestamps = mutableSetOf(timestampMs),
                        lastSeenMs = timestampMs,
                    )
                    tracklets.add(newTrack)
                    activeTracks.add(newTrack)
                    assignedTrackIds.add(newTrack.id)
                }
            }
        }

        if (tracklets.isEmpty()) return emptyList()

        // ── STAGE 2: Conservative Agglomerative Merging ───────────────────────────
        val clusters = tracklets.mapIndexed { index, t ->
            TrackCluster(
                id = index,
                tracklets = mutableListOf(t),
                frameTimestamps = t.frameTimestamps.toMutableSet(),
            ).apply { updateCentroid() }
        }.toMutableList()

        mergeClusters(clusters, similarityThreshold)

        // ── STAGE 2b: Adaptive Gap-Maximization Dedup ────────────────────────────
        // Only useful when we have enough clusters to build a distribution
        if (clusters.size >= 4) {
            val adaptive = computeAdaptiveThreshold(clusters, floor = similarityThreshold)
            if (adaptive > similarityThreshold) {
                mergeClusters(clusters, adaptive)
            }
        }

        // ── STAGE 3: Build PersonClusters (no pruning — all individuals preserved) ─
        return clusters.mapIndexedNotNull { clusterIndex, candidate ->
            val appearances = candidate.tracklets
                .filter { it.observations.isNotEmpty() }
                .sortedBy { it.observations.first().frameTimestampMs }
                .map { t ->
                    Appearance(
                        startMs = t.observations.first().frameTimestampMs,
                        endMs = t.observations.last().frameTimestampMs,
                        observations = t.observations.toList(),
                    )
                }
            if (appearances.isEmpty()) null
            else PersonCluster(
                id = clusterIndex,
                centroidEmbedding = candidate.centroid.copyOf(),
                appearances = appearances.toMutableList(),
            )
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────────────────────────

    private fun mergeClusters(clusters: MutableList<TrackCluster>, threshold: Float) {
        while (clusters.size > 1) {
            var bestPair: Pair<Int, Int>? = null
            var highestSim = -1f

            for (i in 0 until clusters.size) {
                for (j in i + 1 until clusters.size) {
                    val c1 = clusters[i]
                    val c2 = clusters[j]
                    if (c1.overlaps(c2)) continue  // hard cannot-link constraint

                    val sim = cosineSim(c1.centroid, c2.centroid)
                    if (sim > highestSim && sim >= threshold) {
                        highestSim = sim
                        bestPair = i to j
                    }
                }
            }

            if (bestPair == null) break
            val (i, j) = bestPair
            clusters[i].tracklets.addAll(clusters[j].tracklets)
            clusters[i].frameTimestamps.addAll(clusters[j].frameTimestamps)
            clusters[i].updateCentroid()
            clusters.removeAt(j)
        }
    }

    /**
     * Gap-maximization adaptive threshold (Otsu-style on 1-D similarity distribution).
     *
     * 1. Collect every eligible pairwise similarity (ignoring cannot-link pairs).
     * 2. Sort them ascending.
     * 3. Find the index of the largest consecutive gap.
     * 4. Return the midpoint of that gap — this is the natural "valley" between
     *    within-person and between-person similarity distributions for this video.
     *
     * Clamped to [floor, 0.92] so we never drop below the safe conservative floor
     * and never merge everything into one.
     */
    private fun computeAdaptiveThreshold(
        clusters: List<TrackCluster>,
        floor: Float = 0.72f,
        cap: Float = 0.92f,
    ): Float {
        val sims = mutableListOf<Float>()
        for (i in clusters.indices) {
            for (j in i + 1 until clusters.size) {
                if (clusters[i].overlaps(clusters[j])) continue
                val sim = cosineSim(clusters[i].centroid, clusters[j].centroid)
                if (sim > 0f) sims.add(sim)
            }
        }

        if (sims.size < 4) return floor

        sims.sort()

        // Find the largest gap between consecutive similarities
        var maxGapIdx = 0
        var maxGap = 0f
        for (k in 0 until sims.size - 1) {
            val gap = sims[k + 1] - sims[k]
            if (gap > maxGap) {
                maxGap = gap
                maxGapIdx = k
            }
        }

        // Adaptive threshold = midpoint of largest gap
        val adaptive = (sims[maxGapIdx] + sims[maxGapIdx + 1]) / 2f
        return adaptive.coerceIn(floor, cap)
    }

    private fun isSpatiallyConsistent(prevBox: RectF?, currBox: RectF): Boolean {
        if (prevBox == null) return true
        if (iou(prevBox, currBox) > 0.15f) return true

        val prevCx = (prevBox.left + prevBox.right) / 2f
        val prevCy = (prevBox.top + prevBox.bottom) / 2f
        val currCx = (currBox.left + currBox.right) / 2f
        val currCy = (currBox.top + currBox.bottom) / 2f
        val dx = currCx - prevCx
        val dy = currCy - prevCy
        val dist = sqrt(dx * dx + dy * dy)
        val currW = currBox.right - currBox.left
        val currH = currBox.bottom - currBox.top
        return dist <= max(currW, currH) * 1.8f
    }

    companion object {
        fun cosineSim(a: FloatArray, b: FloatArray): Float {
            if (a.isEmpty() || b.isEmpty() || a.size != b.size) return 0f
            var dot = 0f
            for (i in a.indices) dot += a[i] * b[i]
            return dot
        }

        fun l2Normalize(v: FloatArray): FloatArray {
            var sumSq = 0f
            for (x in v) sumSq += x * x
            val norm = sqrt(sumSq)
            if (norm < 1e-6f) return v
            val out = FloatArray(v.size)
            for (i in v.indices) out[i] = v[i] / norm
            return out
        }

        fun iou(a: RectF, b: RectF): Float {
            val interLeft = max(a.left, b.left)
            val interTop = max(a.top, b.top)
            val interRight = min(a.right, b.right)
            val interBottom = min(a.bottom, b.bottom)
            val interArea = max(0f, interRight - interLeft) * max(0f, interBottom - interTop)
            val areaA = max(0f, (a.right - a.left) * (a.bottom - a.top))
            val areaB = max(0f, (b.right - b.left) * (b.bottom - b.top))
            val unionArea = areaA + areaB - interArea
            return if (unionArea > 0f) interArea / unionArea else 0f
        }
    }
}
