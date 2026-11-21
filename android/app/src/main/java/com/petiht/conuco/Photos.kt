package com.petiht.conuco

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlin.math.max

/**
 * Loads a plant photo upright and at most 768 px on the long side. The Bitmap is a new image:
 * no EXIF (GPS, phone model, time) goes to the AI, on the device or in the cloud.
 */
object Photos {
    private const val MAX = 768f
    fun load(context: Context, uri: Uri): Bitmap? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        if (opts.outWidth <= 0) return null
        var sample = 1
        while (max(opts.outWidth, opts.outHeight) / (sample * 2) >= MAX) sample *= 2
        val bmp = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val rotation = runCatching { context.contentResolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0 }.getOrDefault(0)
        val scale = minOf(1f, MAX / max(bmp.width, bmp.height))
        val m = Matrix().apply { postRotate(rotation.toFloat()); postScale(scale, scale) }
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    }
}
