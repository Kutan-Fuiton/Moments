package com.iykyk.collage.pipeline

import android.content.Context
import android.net.Uri
import com.iykyk.collage.model.FaceObservation
import com.iykyk.collage.model.PersonCluster
import com.iykyk.collage.model.ProcessingProgress
import com.iykyk.collage.model.ProcessingStage
import com.iykyk.collage.model.VideoResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

/**
 * End-to-end pipeline: frames → face detection → embeddings → clustering →
 * adaptive dedup → multi-person frame splitting → shot selection → collage.
 *
 * Emits [ProcessingProgress] as a Flow so the UI can show a live progress bar.
 * Every compute-heavy stage runs on Dispatchers.Default, never on Dispatchers.Main.
 */
class CollagePipeline(private val context: Context) {

    private val frameExtractor = VideoFrameExtractor(context)
    private val faceDetector = FaceDetectorWrapper()
    private val embedder by lazy { FaceEmbedder(context) }
    private val clusterer = FaceClusterer()

    fun process(
        videoUri: Uri,
        videoLabel: String,
        template: CollageTemplate = CollageTemplate.Editorial,
    ): Flow<Pair<ProcessingProgress, VideoResult?>> = flow {
        emit(ProcessingProgress(ProcessingStage.ExtractingFrames) to null)
        val frames = frameExtractor.extractFrames(videoUri) { _, _ -> }

        val observationsByFrame = mutableListOf<Pair<Long, List<FaceObservation>>>()

        for ((index, frame) in frames.withIndex()) {
            emit(ProcessingProgress(ProcessingStage.DetectingFaces, index + 1, frames.size) to null)
            val rawFaces = withContext(Dispatchers.Default) { faceDetector.detect(frame.bitmap) }

            val observations = rawFaces.map { raw ->
                val embedding = withContext(Dispatchers.Default) {
                    embedder.embed(frame.bitmap, raw.box)
                }
                FaceObservation(
                    frameTimestampMs = frame.timestampMs,
                    frameBitmap = frame.bitmap,
                    boundingBox = raw.box,
                    embedding = embedding,
                    headEulerAngleY = raw.yaw,
                    headEulerAngleZ = raw.roll,
                    headEulerAngleX = raw.pitch,
                    trackingId = raw.trackingId,
                    leftEyeOpenProbability = raw.leftEyeOpen,
                    rightEyeOpenProbability = raw.rightEyeOpen,
                    smilingProbability = raw.smiling,
                    sharpness = raw.sharpness,
                    isFullyInFrame = raw.isFullyInFrame,
                )
            }
            observationsByFrame.add(frame.timestampMs to observations)
        }

        emit(ProcessingProgress(ProcessingStage.Clustering) to null)
        val clusters: List<PersonCluster> = withContext(Dispatchers.Default) {
            clusterer.cluster(observationsByFrame)
        }

        emit(ProcessingProgress(ProcessingStage.SelectingShots) to null)
        val allObservations = observationsByFrame.flatMap { it.second }
        val maxSharpness = allObservations.maxOfOrNull { it.sharpness } ?: 1f
        val maxArea = allObservations.maxOfOrNull { it.boundingBox.width() * it.boundingBox.height() } ?: 1f
        withContext(Dispatchers.Default) {
            for (cluster in clusters) {
                cluster.representative = RepresentativeShotSelector.pickBest(cluster, maxSharpness, maxArea)
            }
        }

        val finalClusters = clusters.filter { it.representative != null }

        // Split multi-person frames: if a representative frame contains other people,
        // crop it to just this person's face region for a clean single-person tile.
        withContext(Dispatchers.Default) {
            MultiPersonFrameSplitter.clean(finalClusters, observationsByFrame)
        }

        // Memory: recycle all non-representative frame bitmaps
        val keepBitmaps = finalClusters.mapNotNull { it.representative?.frameBitmap }.toSet()
        for (frame in frames) {
            if (!keepBitmaps.contains(frame.bitmap) && !frame.bitmap.isRecycled) {
                frame.bitmap.recycle()
            }
        }

        emit(ProcessingProgress(ProcessingStage.BuildingCollage) to null)
        val collage = withContext(Dispatchers.Default) {
            CollageGenerator.buildCollage(videoLabel, finalClusters, template)
        }

        emit(
            ProcessingProgress(ProcessingStage.Done) to
                VideoResult(videoLabel, finalClusters, collage)
        )
    }

    fun rebuildCollage(people: List<PersonCluster>, videoLabel: String, template: CollageTemplate) =
        CollageGenerator.buildCollage(videoLabel, people, template)

    fun close() {
        faceDetector.close()
        embedder.close()
    }
}
