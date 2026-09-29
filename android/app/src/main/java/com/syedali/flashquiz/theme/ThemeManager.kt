package com.syedali.flashquiz.theme

import android.content.Context

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

/**
 * Official sage/teal tokens mapped to the legacy AppTheme surface.
 * Values mirror Palette (Compose) and res/values + values-night colors.xml.
 */
object ThemeManager {
    private const val PREFS_NAME = "theme_prefs"
    private const val KEY_THEME_ID = "current_theme_id"

    fun parseColor(color: String): Int {
        val hex = color.removePrefix("#")
        return when (hex.length) {
            6 -> (0xFF000000L or hex.toLong(16)).toInt()
            8 -> hex.toLong(16).toInt()
            else -> throw IllegalArgumentException("Invalid color: $color")
        }
    }

    val themes = listOf(
        AppTheme(
            id = "light",
            name = "Light",
            background = parseColor("#BEE9DA"),
            surface = parseColor("#BBCAAA"),
            surfaceVariant = parseColor("#F4FBF7"),
            accent = parseColor("#7CD5C8"),
            accentLight = parseColor("#3DB8A8"),
            textPrimary = parseColor("#312116"),
            textSecondary = parseColor("#4A3C32"),
            textMuted = parseColor("#685D55"),
            border = parseColor("#33312116"),
            error = parseColor("#C45B5B"),
            success = parseColor("#3D9A6E"),
            warning = parseColor("#B8863B")
        ),
        AppTheme(
            id = "dark",
            name = "Dark",
            background = parseColor("#121816"),
            surface = parseColor("#1B2421"),
            surfaceVariant = parseColor("#212C28"),
            accent = parseColor("#66C2B4"),
            accentLight = parseColor("#9BAE8B"),
            textPrimary = parseColor("#E3ECE8"),
            textSecondary = parseColor("#B8C4BE"),
            textMuted = parseColor("#A1AFA9"),
            border = parseColor("#2A3833"),
            error = parseColor("#D47A7A"),
            success = parseColor("#5CBF8E"),
            warning = parseColor("#D4A85C")
        )
    )

    fun getCurrentTheme(context: Context): AppTheme {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val themeId = prefs.getString(KEY_THEME_ID, "dark") ?: "dark"
        return themes.find { it.id == themeId } ?: themes.first { it.id == "dark" }
    }

    fun setTheme(context: Context, themeId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_THEME_ID, themeId).apply()
    }
}
