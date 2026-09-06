package com.iykyk.collage.pipeline

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Generates a 192-d face embedding using MobileFaceNet.
 *
 * Model: `mobilefacenet.tflite` (assets/mobilefacenet.tflite)
 * Input: 112x112 RGB, normalized to [-1, 1]
 * Output: 192-d L2-normalized unit vector (cosine similarity == dot product)
 */
class FaceEmbedder(context: Context, modelAssetName: String = "mobilefacenet.tflite") {

    companion object {
        private const val INPUT_SIZE = 112
        const val EMBEDDING_DIM = 192
    }

    private val interpreter: Interpreter

    init {
        val afd = context.assets.openFd(modelAssetName)
        val inputStream = FileInputStream(afd.fileDescriptor)
        val mapped: MappedByteBuffer = inputStream.channel.map(
            FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.declaredLength
        )
        val options = Interpreter.Options().apply { setNumThreads(4) }
        interpreter = Interpreter(mapped, options)
    }

    fun embed(fullFrame: Bitmap, faceBox: RectF): FloatArray {
        val aligned = cropAndResize(fullFrame, faceBox)
        val input = bitmapToByteBuffer(aligned)
        val output = Array(1) { FloatArray(EMBEDDING_DIM) }
        interpreter.run(input, output)
        aligned.recycle()
        return l2Normalize(output[0])
    }

    private fun cropAndResize(bitmap: Bitmap, box: RectF): Bitmap {
        val cx = box.centerX()
        val cy = box.centerY()
        val side = max(box.width(), box.height()) * 1.30f
        val clampedSide = min(side, min(bitmap.width.toFloat(), bitmap.height.toFloat()))

        val left = (cx - clampedSide / 2f).coerceIn(0f, bitmap.width - clampedSide)
        val top = (cy - clampedSide / 2f).coerceIn(0f, bitmap.height - clampedSide)

        val cropped = Bitmap.createBitmap(
            bitmap,
            left.toInt(),
            top.toInt(),
            clampedSide.toInt(),
            clampedSide.toInt(),
        )

        val resized = Bitmap.createScaledBitmap(cropped, INPUT_SIZE, INPUT_SIZE, true)
        if (resized != cropped) cropped.recycle()
        return resized
    }

    private fun bitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(4 * INPUT_SIZE * INPUT_SIZE * 3)
        buffer.order(ByteOrder.nativeOrder())
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        bitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        for (p in pixels) {
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            buffer.putFloat((r - 127.5f) / 128f)
            buffer.putFloat((g - 127.5f) / 128f)
            buffer.putFloat((b - 127.5f) / 128f)
        }
        buffer.rewind()
        return buffer
    }

    private fun l2Normalize(v: FloatArray): FloatArray {
        var sumSq = 0f
        for (x in v) sumSq += x * x
        val norm = sqrt(sumSq).coerceAtLeast(1e-6f)
        return FloatArray(v.size) { v[it] / norm }
    }

    fun close() = interpreter.close()
}
