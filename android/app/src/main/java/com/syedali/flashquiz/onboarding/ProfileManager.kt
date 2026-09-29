package com.syedali.flashquiz.onboarding

import android.content.Context
import android.net.Uri

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
