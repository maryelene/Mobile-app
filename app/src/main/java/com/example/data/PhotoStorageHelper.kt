package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object PhotoStorageHelper {
    private const val PHOTOS_DIR = "note_photos"
    private const val CAMERA_DIR = "camera_captures"
    private const val MAX_DIMENSION = 1600

    fun createCameraCaptureUri(context: Context): Pair<Uri, File> {
        val cacheFolder = File(context.cacheDir, CAMERA_DIR).apply { mkdirs() }
        val file = File(cacheFolder, "capture_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return uri to file
    }

    fun getPhotoFile(context: Context, photoFileName: String?): File? {
        if (photoFileName.isNullOrBlank()) return null
        val file = File(File(context.filesDir, PHOTOS_DIR), photoFileName)
        return if (file.exists()) file else null
    }

    suspend fun saveCompressedPhoto(context: Context, sourceUri: Uri): Pair<String, Bitmap> =
        withContext(Dispatchers.IO) {
            val bitmap = decodeScaledBitmap(context, sourceUri)
                ?: throw IllegalStateException("Could not read image from selected photo.")
            val dir = File(context.filesDir, PHOTOS_DIR).apply { mkdirs() }
            val fileName = "note_${UUID.randomUUID().toString().replace("-", "").take(16)}.jpg"
            val outFile = File(dir, fileName)
            FileOutputStream(outFile).use { stream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 86, stream)
            }
            fileName to bitmap
        }

    suspend fun loadBitmapForFile(context: Context, photoFileName: String): Bitmap? =
        withContext(Dispatchers.IO) {
            val file = getPhotoFile(context, photoFileName) ?: return@withContext null
            BitmapFactory.decodeFile(file.absolutePath)
        }

    private fun decodeScaledBitmap(context: Context, uri: Uri): Bitmap? {
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, boundsOpts)
        }
        val origW = boundsOpts.outWidth
        val origH = boundsOpts.outHeight
        if (origW <= 0 || origH <= 0) return null

        var sampleSize = 1
        while (origW / sampleSize > MAX_DIMENSION || origH / sampleSize > MAX_DIMENSION) {
            sampleSize *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val raw = context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, decodeOpts)
        } ?: return null

        val rotationDegrees = context.contentResolver.openInputStream(uri)?.use { input ->
            runCatching {
                when (ExifInterface(input).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            }.getOrDefault(0f)
        } ?: 0f

        val rotated = if (rotationDegrees != 0f) {
            val matrix = Matrix().apply { postRotate(rotationDegrees) }
            Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true).also {
                if (it != raw) raw.recycle()
            }
        } else {
            raw
        }

        val maxSide = maxOf(rotated.width, rotated.height)
        return if (maxSide > MAX_DIMENSION) {
            val scale = MAX_DIMENSION.toFloat() / maxSide.toFloat()
            val targetW = (rotated.width * scale).toInt().coerceAtLeast(1)
            val targetH = (rotated.height * scale).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(rotated, targetW, targetH, true).also {
                if (it != rotated) rotated.recycle()
            }
        } else {
            rotated
        }
    }
}
