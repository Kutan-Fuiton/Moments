package com.iykyk.collage.pipeline

import android.graphics.Bitmap
import android.graphics.RectF
import com.iykyk.collage.model.FaceObservation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceClustererTest {

    private fun createDummyObservation(
        timestampMs: Long,
        embedding: FloatArray,
        box: RectF = RectF(100f, 100f, 200f, 200f),
        trackingId: Int? = null,
        yaw: Float = 0f,
    ): FaceObservation {
        // In unit tests with isReturnDefaultValues = true, a mocked Bitmap is acceptable
        val mockBitmap = org.mockito.Mockito.mock(Bitmap::class.java)
        return FaceObservation(
            frameTimestampMs = timestampMs,
            frameBitmap = mockBitmap,
            boundingBox = box,
            embedding = embedding,
            headEulerAngleY = yaw,
            headEulerAngleZ = 0f,
            headEulerAngleX = 0f,
            trackingId = trackingId,
            leftEyeOpenProbability = 0.9f,
            rightEyeOpenProbability = 0.9f,
            smilingProbability = 0.5f,
            sharpness = 100f,
            isFullyInFrame = true,
        )
    }

    @Test
    fun testCosineSimilarityIdenticalVectors() {
        val v = floatArrayOf(1f, 0f, 0f)
        val sim = FaceClusterer.cosineSim(v, v)
        assertEquals(1f, sim, 1e-5f)
    }

    @Test
    fun testCosineSimilarityOrthogonalVectors() {
        val a = floatArrayOf(1f, 0f, 0f)
        val b = floatArrayOf(0f, 1f, 0f)
        val sim = FaceClusterer.cosineSim(a, b)
        assertEquals(0f, sim, 1e-5f)
    }

    @Test
    fun testL2Normalize() {
        val v = floatArrayOf(3f, 4f)
        val norm = FaceClusterer.l2Normalize(v)
        assertEquals(0.6f, norm[0], 1e-5f)
        assertEquals(0.8f, norm[1], 1e-5f)
    }

    @Test
    fun testDistinctPeopleNeverMerged() {
        val clusterer = FaceClusterer(similarityThreshold = 0.72f)
        // Two people with similarity 0.50 appearing at different times
        val embA = FaceClusterer.l2Normalize(floatArrayOf(1f, 0f, 0f))
        val embB = FaceClusterer.l2Normalize(floatArrayOf(0.5f, 0.866f, 0f)) // cosine similarity = 0.50

        val obsA = createDummyObservation(0L, embA, box = RectF(100f, 100f, 200f, 200f))
        val obsB = createDummyObservation(2000L, embB, box = RectF(100f, 100f, 200f, 200f))

        val input = listOf(
            0L to listOf(obsA),
            2000L to listOf(obsB),
        )

        val clusters = clusterer.cluster(input)
        assertEquals("Both distinct people must be preserved as separate clusters", 2, clusters.size)
    }

    @Test
    fun testHardCannotLinkConstraint() {
        val clusterer = FaceClusterer(similarityThreshold = 0.72f)
        // Two faces appearing in the EXACT same frame timestamp (co-present)
        // Even with high similarity (0.90), they CANNOT be merged because one physical person
        // cannot appear in two distinct locations at the exact same millisecond.
        val embA = FaceClusterer.l2Normalize(floatArrayOf(1f, 0f, 0f))
        val embB = FaceClusterer.l2Normalize(floatArrayOf(0.9f, 0.4358f, 0f)) // similarity = 0.90

        val obsA = createDummyObservation(1000L, embA, box = RectF(50f, 100f, 150f, 200f))
        val obsB = createDummyObservation(1000L, embB, box = RectF(400f, 100f, 500f, 200f))

        val input = listOf(
            1000L to listOf(obsA, obsB)
        )

        val clusters = clusterer.cluster(input)
        assertEquals("Co-present faces in same frame can never merge", 2, clusters.size)
    }

    @Test
    fun testSamePersonAcrossDisjointAppearancesMerged() {
        val clusterer = FaceClusterer(similarityThreshold = 0.72f)
        // Same person appearing in two separate scenes (similarity 0.88 >= 0.72)
        val emb1 = FaceClusterer.l2Normalize(floatArrayOf(1f, 0.1f, 0f))
        val emb2 = FaceClusterer.l2Normalize(floatArrayOf(1f, 0.15f, 0f))

        val obs1 = createDummyObservation(0L, emb1)
        val obs2 = createDummyObservation(3000L, emb2)

        val input = listOf(
            0L to listOf(obs1),
            3000L to listOf(obs2),
        )

        val clusters = clusterer.cluster(input)
        assertEquals("Same person across scenes should merge into 1 cluster", 1, clusters.size)
        assertEquals("Merged person should record both appearances", 2, clusters.first().appearanceCount)
    }

    @Test
    fun testFiveDistinctPeopleAreAllCovered() {
        val clusterer = FaceClusterer(similarityThreshold = 0.72f)
        // 5 distinct individuals
        val embeddings = listOf(
            FaceClusterer.l2Normalize(floatArrayOf(1f, 0f, 0f, 0f, 0f)),
            FaceClusterer.l2Normalize(floatArrayOf(0f, 1f, 0f, 0f, 0f)),
            FaceClusterer.l2Normalize(floatArrayOf(0f, 0f, 1f, 0f, 0f)),
            FaceClusterer.l2Normalize(floatArrayOf(0f, 0f, 0f, 1f, 0f)),
            FaceClusterer.l2Normalize(floatArrayOf(0f, 0f, 0f, 0f, 1f)),
        )

        val input = embeddings.mapIndexed { i, emb ->
            (i * 1000L) to listOf(createDummyObservation(i * 1000L, emb))
        }

        val clusters = clusterer.cluster(input)
        assertTrue("All 5 people must be represented (at least 5 clusters)", clusters.size >= 5)
        assertEquals("Exactly 5 distinct individuals preserved", 5, clusters.size)
    }
}
