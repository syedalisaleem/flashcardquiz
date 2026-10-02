package com.syedali.flashquiz.onboarding

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

/**
 * Manages user profile data stored in SharedPreferences.
 * Access profile name and picture URI app-wide via ProfileManager.get()
 */
object ProfileManager {

    private const val PREFS_NAME = "flashquiz_profile"
    private const val KEY_NAME = "profile_name"
    private const val KEY_PICTURE_URI = "profile_picture_uri"

    @Volatile
    private var cachedName: String? = null
    @Volatile
    private var cachedPictureUri: String? = null

    fun get(context: Context): ProfileManager {
        // Initialize cache on first access
        if (cachedName == null) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            cachedName = prefs.getString(KEY_NAME, "") ?: ""
            cachedPictureUri = prefs.getString(KEY_PICTURE_URI, null)
        }
        return this
    }

    fun getName(context: Context): String {
        get(context)
        return cachedName ?: ""
    }

    fun getPictureUri(context: Context): Uri? {
        get(context)
        return cachedPictureUri?.let { Uri.parse(it) }
    }

    fun setName(context: Context, name: String) {
        cachedName = name
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_NAME, name)
            .apply()
    }

    fun setPictureUri(context: Context, uri: Uri?) {
        cachedPictureUri = uri?.toString()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PICTURE_URI, uri?.toString())
            .apply()
    }

    /**
     * Copy a picked/captured picture into app-private storage
     * (filesDir/profile/picture.jpg) and return its durable file:// URI.
     *
     * Foreign content:// URIs lose their read grant on reboot (Google Photos
     * etc.) and camera temp files live in cacheDir, which the system may
     * clear — both broke the avatar later. The copy is downscaled to
     * <=1024px so a full-resolution camera photo cannot OOM the app.
     * Returns null when the source cannot be read/decoded.
     */
    fun importPicture(context: Context, source: Uri): Uri? {
        return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(source)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        } ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        val largest = maxOf(bounds.outWidth, bounds.outHeight)
        while (largest / (sample * 2) >= 1024) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = context.contentResolver.openInputStream(source)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: return null

        val dir = File(context.filesDir, "profile").apply { mkdirs() }
        val out = File(dir, "picture.jpg")
        val scale = minOf(1f, 1024f / maxOf(bitmap.width, bitmap.height))
        val scaled = if (scale < 1f) {
            val w = maxOf(1, (bitmap.width * scale).toInt())
            val h = maxOf(1, (bitmap.height * scale).toInt())
            val s = Bitmap.createScaledBitmap(bitmap, w, h, true)
            bitmap.recycle()
            s
        } else {
            bitmap
        }
        FileOutputStream(out).use { fos ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 88, fos)
        }
        scaled.recycle()
        Uri.fromFile(out)
        } catch (_: Exception) {
            null
        }
    }

    fun getInitials(context: Context): String {
        val name = getName(context)
        if (name.isBlank()) return "?"
        val parts = name.trim().split("\\s+".toRegex())
        return if (parts.size >= 2) {
            "${parts[0].first().uppercaseChar()}${parts[1].first().uppercaseChar()}"
        } else {
            parts[0].first().uppercaseChar().toString()
        }
    }
}
