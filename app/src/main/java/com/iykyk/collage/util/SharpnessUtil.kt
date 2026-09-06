package com.iykyk.collage.util

import android.graphics.Bitmap
import android.graphics.RectF
import kotlin.math.max
import kotlin.math.min

/**
 * Blur / motion-blur detection via variance of the Laplacian, computed on a downsampled
 * grayscale patch for speed. This is the standard cheap sharpness proxy used to reject
 * whip-pan / motion-blurred frames when scoring candidate shots — no extra ML model needed.
 */
object SharpnessUtil {

    fun laplacianVariance(bitmap: Bitmap, region: RectF, maxDim: Int = 128): Float {
        val left = max(0, region.left.toInt())
        val top = max(0, region.top.toInt())
        val right = min(bitmap.width, region.right.toInt())
        val bottom = min(bitmap.height, region.bottom.toInt())
        val w = right - left
        val h = bottom - top
        if (w <= 4 || h <= 4) return 0f

        // Downscale for speed; relative sharpness ranking is preserved.
        val scale = min(1f, maxDim.toFloat() / max(w, h))
        val sw = max(4, (w * scale).toInt())
        val sh = max(4, (h * scale).toInt())

        val cropped = Bitmap.createBitmap(bitmap, left, top, w, h)
        val scaled = Bitmap.createScaledBitmap(cropped, sw, sh, true)

        val gray = IntArray(sw * sh)
        scaled.getPixels(gray, 0, sw, 0, 0, sw, sh)
        val luma = FloatArray(sw * sh)
        for (i in gray.indices) {
            val p = gray[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            luma[i] = 0.299f * r + 0.587f * g + 0.114f * b
        }

        // 3x3 Laplacian kernel convolution.
        var sum = 0.0
        var sumSq = 0.0
        var count = 0
        for (y in 1 until sh - 1) {
            for (x in 1 until sw - 1) {
                val idx = y * sw + x
                val lap = -4f * luma[idx] +
                    luma[idx - 1] + luma[idx + 1] +
                    luma[idx - sw] + luma[idx + sw]
                sum += lap
                sumSq += lap.toDouble() * lap
                count++
            }
        }
        if (count == 0) return 0f
        val mean = sum / count
        val variance = (sumSq / count) - (mean * mean)

        if (cropped !== bitmap && !cropped.isRecycled) cropped.recycle()
        if (scaled !== cropped && scaled !== bitmap && !scaled.isRecycled) scaled.recycle()
        return variance.toFloat()
    }
}
