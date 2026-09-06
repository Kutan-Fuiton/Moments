package com.iykyk.collage.data

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SaveShareUtil {

    private fun generateTimestampedFilename(prefix: String = "moments"): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        return "${prefix}_$stamp"
    }

    /** Saves the collage into the public Pictures/Moments gallery folder. */
    suspend fun saveToGallery(context: Context, bitmap: Bitmap, customName: String? = null): Uri? =
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val filename = if (!customName.isNullOrBlank()) {
                "${customName.replace("[^a-zA-Z0-9_-]".toRegex(), "_")}.jpg"
            } else {
                "${generateTimestampedFilename()}.jpg"
            }

            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Moments")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@withContext null
            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 97, out)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            uri
        }

    /** Writes to app cache and returns a FileProvider content:// Uri suitable for sharing. */
    suspend fun prepareShareUri(context: Context, bitmap: Bitmap, customName: String? = null): Uri =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "collages").apply { mkdirs() }
            val filename = if (!customName.isNullOrBlank()) {
                "${customName.replace("[^a-zA-Z0-9_-]".toRegex(), "_")}.jpg"
            } else {
                "${generateTimestampedFilename()}_share.jpg"
            }
            val file = File(dir, filename)
            FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out) }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }

    fun shareIntent(uri: Uri): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
}
