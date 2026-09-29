package com.syedali.flashquiz.theme

import androidx.compose.ui.graphics.toArgb
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Phase 1 token contract: official sage/teal palette must not drift.
 * Values must match res/values/colors.xml (light) and res/values-night/colors.xml (dark).
 */
class ThemeTokenTest {

    @Test
    fun lightPalette_matchesOfficialSpec() {
        assertThat(hex(Palette.LightCanvas)).isEqualTo("#BEE9DA")
        assertThat(hex(Palette.LightSurface)).isEqualTo("#BBCAAA")
        assertThat(hex(Palette.LightPrimaryAccent)).isEqualTo("#7CD5C8")
        assertThat(hex(Palette.LightPrimaryText)).isEqualTo("#312116")
        assertThat(hex(Palette.LightMutedText)).isEqualTo("#685D55")
    }

    @Test
    fun darkPalette_matchesOfficialSpec() {
        assertThat(hex(Palette.DarkCanvas)).isEqualTo("#121816")
        assertThat(hex(Palette.DarkSurface)).isEqualTo("#1B2421")
        assertThat(hex(Palette.DarkBorder)).isEqualTo("#2A3833")
        assertThat(hex(Palette.DarkPrimaryAccent)).isEqualTo("#66C2B4")
        assertThat(hex(Palette.DarkSecondaryAccent)).isEqualTo("#9BAE8B")
        assertThat(hex(Palette.DarkPrimaryText)).isEqualTo("#E3ECE8")
        assertThat(hex(Palette.DarkMutedText)).isEqualTo("#A1AFA9")
    }

    @Test
    fun legacyAppTheme_mirrorsOfficialTokens() {
        val light = ThemeManager.themes.first { it.id == "light" }
        val dark = ThemeManager.themes.first { it.id == "dark" }

        assertThat(hexArgb(light.background)).isEqualTo("#BEE9DA")
        assertThat(hexArgb(light.accent)).isEqualTo("#7CD5C8")
        assertThat(hexArgb(light.textPrimary)).isEqualTo("#312116")
        assertThat(hexArgb(light.textMuted)).isEqualTo("#685D55")

        assertThat(hexArgb(dark.background)).isEqualTo("#121816")
        assertThat(hexArgb(dark.surface)).isEqualTo("#1B2421")
        assertThat(hexArgb(dark.border)).isEqualTo("#2A3833")
        assertThat(hexArgb(dark.accent)).isEqualTo("#66C2B4")
        assertThat(hexArgb(dark.textPrimary)).isEqualTo("#E3ECE8")
        assertThat(hexArgb(dark.textMuted)).isEqualTo("#A1AFA9")
    }

    private fun hex(color: androidx.compose.ui.graphics.Color): String =
        "#%06X".format(color.toArgb() and 0xFFFFFF)

    private fun hexArgb(argb: Int): String = "#%06X".format(argb and 0xFFFFFF)
}
