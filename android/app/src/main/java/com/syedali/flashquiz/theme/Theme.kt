package com.syedali.flashquiz.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    background = Palette.LightCanvas,
    surface = Palette.LightSurface,
    surfaceVariant = Palette.LightSurfaceElevated,
    primary = Palette.LightPrimaryAccent,
    onPrimary = Palette.OnAccentLight,
    onBackground = Palette.LightPrimaryText,
    onSurface = Palette.LightPrimaryText,
    onSurfaceVariant = Palette.LightMutedText,
    outline = Palette.LightMutedText,
    outlineVariant = Palette.LightBorder,
    error = Palette.Error,
    tertiary = Palette.Success,
)

private val DarkColors = darkColorScheme(
    background = Palette.DarkCanvas,
    surface = Palette.DarkSurface,
    surfaceVariant = Palette.DarkSurfaceElevated,
    primary = Palette.DarkPrimaryAccent,
    onPrimary = Palette.OnAccentDark,
    onBackground = Palette.DarkPrimaryText,
    onSurface = Palette.DarkPrimaryText,
    onSurfaceVariant = Palette.DarkMutedText,
    outline = Palette.DarkMutedText,
    outlineVariant = Palette.DarkBorder,
    secondary = Palette.DarkSecondaryAccent,
    error = Palette.ErrorDark,
    tertiary = Palette.SuccessDark,
)

@Composable
fun FlashcardQuizTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
