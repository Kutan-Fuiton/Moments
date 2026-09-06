package com.iykyk.collage.pipeline

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

data class ExtractedFrame(val timestampMs: Long, val bitmap: Bitmap)

/**
 * Samples frames from the source video at a fixed interval.
 *
 * Sampling rate: every [sampleIntervalMs]. 200ms (5 fps) is dense enough to catch short
 * appearances and to segment appearance boundaries accurately, without exploding processing
 * time on a 30s clip (~150 frames per video). This all runs on Dispatchers.Default /
 * MediaMetadataRetriever's decoder thread — never the main thread.
 *
 * Memory optimization: Frames are decoded scaled (max dimension 960px) to prevent
 * OutOfMemory crashes on high-res (1080p/4K) phone recordings while preserving high-detail
 * face crops for collage tiles.
 */
class VideoFrameExtractor(
    private val context: Context,
    private val sampleIntervalMs: Long = 200L,
    private val maxFrameDimension: Int = 960,
) {
    suspend fun extractFrames(
        videoUri: Uri,
        onProgress: (current: Int, total: Int) -> Unit,
    ): List<ExtractedFrame> = withContext(Dispatchers.Default) {
        val retriever = MediaMetadataRetriever()
        val frames = mutableListOf<ExtractedFrame>()
        val pfd = try {
            context.contentResolver.openFileDescriptor(videoUri, "r")
        } catch (e: Exception) {
            null
        }

        try {
            if (pfd != null) {
                retriever.setDataSource(pfd.fileDescriptor)
            } else {
                retriever.setDataSource(context, videoUri)
            }

            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            val totalSamples = (durationMs / sampleIntervalMs).toInt().coerceAtLeast(1)

            val rawWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 0
            val rawHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 0
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull() ?: 0

            // Compute scaled dimensions to avoid loading full 4K / 1080p frames into memory
            val isSwapped = rotation == 90 || rotation == 270
            val videoWidth = if (isSwapped && rawHeight > 0) rawHeight else rawWidth
            val videoHeight = if (isSwapped && rawWidth > 0) rawWidth else rawHeight

            val scale = if (max(videoWidth, videoHeight) > 0) {
                min(1f, maxFrameDimension.toFloat() / max(videoWidth, videoHeight))
            } else 1f

            val targetW = if (rawWidth > 0) (rawWidth * scale).toInt().coerceAtLeast(1) else 0
            val targetH = if (rawHeight > 0) (rawHeight * scale).toInt().coerceAtLeast(1) else 0

            var t = 0L
            var i = 0
            while (t < durationMs) {
                val timeUs = t * 1000
                var bmp: Bitmap? = null

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 && targetW > 0 && targetH > 0) {
                    try {
                        bmp = retriever.getScaledFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST, targetW, targetH)
                    } catch (e: Exception) {
                        // fallback to standard getFrameAtTime if getScaledFrameAtTime fails on driver
                        bmp = null
                    }
                }

                if (bmp == null) {
                    val rawBmp = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                    if (rawBmp != null) {
                        if (scale < 1f) {
                            val sw = (rawBmp.width * scale).toInt().coerceAtLeast(1)
                            val sh = (rawBmp.height * scale).toInt().coerceAtLeast(1)
                            bmp = Bitmap.createScaledBitmap(rawBmp, sw, sh, true)
                            if (bmp != rawBmp) rawBmp.recycle()
                        } else {
                            bmp = rawBmp
                        }
                    }
                }

                if (bmp != null) {
                    // Normalize orientation if the decoder didn't apply metadata rotation
                    val orientedBmp = fixRotationIfNeeded(bmp, rotation)
                    frames.add(ExtractedFrame(t, orientedBmp))
                }

                i++
                onProgress(i, totalSamples)
                t += sampleIntervalMs
            }
        } finally {
            try { retriever.release() } catch (ignored: Exception) {}
            try { pfd?.close() } catch (ignored: Exception) {}
        }
        frames
    }

    private fun fixRotationIfNeeded(bitmap: Bitmap, rotationDegrees: Int): Bitmap {
        if (rotationDegrees == 0) return bitmap
        // If bitmap dimensions already reflect portrait/landscape orientation match, no rotation needed
        val isPortraitByDims = bitmap.height >= bitmap.width
        val isPortraitByRotation = rotationDegrees == 90 || rotationDegrees == 270

        // If metadata says 90/270 (portrait) but decoder returned landscape dimensions (width > height), rotate
        if (isPortraitByRotation && !isPortraitByDims) {
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated != bitmap) bitmap.recycle()
            return rotated
        }
        return bitmap
    }
}
