package com.syedali.flashquiz.theme

import android.content.Context
import android.graphics.Color

data class AppTheme(
    val id: String,
    val name: String,
    val background: Int,
    val surface: Int,
    val surfaceVariant: Int,
    val accent: Int,
    val accentLight: Int,
    val textPrimary: Int,
    val textSecondary: Int,
    val textMuted: Int,
    val border: Int,
    val error: Int,
    val success: Int,
    val warning: Int
)

object ThemeManager {
    private const val PREFS_NAME = "theme_prefs"
    private const val KEY_THEME_ID = "current_theme_id"

    val themes = listOf(
        AppTheme(
            id = "dark",
            name = "Dark",
            background = Color.parseColor("#191B1D"),
            surface = Color.parseColor("#232527"),
            surfaceVariant = Color.parseColor("#393B3D"),
            accent = Color.parseColor("#00A2FF"),
            accentLight = Color.parseColor("#00A2FF"),
            textPrimary = Color.parseColor("#FFFFFF"),
            textSecondary = Color.parseColor("#A1A8AD"),
            textMuted = Color.parseColor("#656B70"),
            border = Color.parseColor("#393B3D"),
            error = Color.parseColor("#FF4444"),
            success = Color.parseColor("#00E054"),
            warning = Color.parseColor("#FFAA00")
        ),
        AppTheme(
            id = "light",
            name = "Light",
            background = Color.parseColor("#DEE1E3"),
            surface = Color.parseColor("#FFFFFF"),
            surfaceVariant = Color.parseColor("#F2F4F5"),
            accent = Color.parseColor("#00A2FF"),
            accentLight = Color.parseColor("#00A2FF"),
            textPrimary = Color.parseColor("#393B3D"),
            textSecondary = Color.parseColor("#656B70"),
            textMuted = Color.parseColor("#A1A8AD"),
            border = Color.parseColor("#BDC3C7"),
            error = Color.parseColor("#FF4444"),
            success = Color.parseColor("#00E054"),
            warning = Color.parseColor("#FFAA00")
        )
    )

    fun getCurrentTheme(context: Context): AppTheme {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val themeId = prefs.getString(KEY_THEME_ID, "dark") ?: "dark"
        return themes.find { it.id == themeId } ?: themes[0]
    }

    fun setTheme(context: Context, themeId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_THEME_ID, themeId).apply()
    }
}
