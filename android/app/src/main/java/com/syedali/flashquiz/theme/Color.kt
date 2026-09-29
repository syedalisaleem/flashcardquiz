package com.syedali.flashquiz.theme

import androidx.compose.ui.graphics.Color

/**
 * Official FlashcardQuiz sage/teal design tokens.
 * Single source of truth for Compose; XML mirrors via res/values and values-night colors.xml.
 */
object Palette {

    // Light mode
    val LightCanvas = Color(0xFFBEE9DA)
    val LightSurface = Color(0xFFBBCAAA)
    val LightSurfaceElevated = Color(0xFFF4FBF7)
    val LightPrimaryAccent = Color(0xFF7CD5C8)
    val LightPrimaryText = Color(0xFF312116)
    val LightMutedText = Color(0xFF685D55)
    val LightBorder = Color(0x1F312116)

    // Dark mode
    val DarkCanvas = Color(0xFF121816)
    val DarkSurface = Color(0xFF1B2421)
    val DarkSurfaceElevated = Color(0xFF212C28)
    val DarkBorder = Color(0xFF2A3833)
    val DarkPrimaryAccent = Color(0xFF66C2B4)
    val DarkSecondaryAccent = Color(0xFF9BAE8B)
    val DarkPrimaryText = Color(0xFFE3ECE8)
    val DarkMutedText = Color(0xFFA1AFA9)

    // Shared semantic
    val OnAccentLight = Color(0xFF1A2E28)
    val OnAccentDark = Color(0xFF121816)
    val Success = Color(0xFF3D9A6E)
    val SuccessDark = Color(0xFF5CBF8E)
    val Error = Color(0xFFC45B5B)
    val ErrorDark = Color(0xFFD47A7A)
    val Warning = Color(0xFFB8863B)
    val WarningDark = Color(0xFFD4A85C)
}
