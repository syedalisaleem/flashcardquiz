package com.syedali.flashquiz.theme

import android.app.Activity
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.textfield.TextInputLayout

object ThemeHelper {

    fun applyTheme(activity: Activity, theme: AppTheme) {
        activity.window.decorView.setBackgroundColor(theme.background)
        activity.window.statusBarColor = theme.background
        activity.window.navigationBarColor = theme.background
    }

    fun applyToView(view: View, theme: AppTheme) {
        when (view) {
            is CardView -> applyToCard(view, theme)
            is MaterialButton -> applyToMaterialButton(view, theme)
            is Button -> applyToButton(view, theme)
            is TextView -> applyToTextView(view, theme)
            is EditText -> applyToEditText(view, theme)
            is TextInputLayout -> applyToInputLayout(view, theme)
            is Chip -> applyToChip(view, theme)
            is ProgressBar -> applyToProgressBar(view, theme)
            is ImageButton -> applyToImageButton(view, theme)
        }
    }

    private fun applyToCard(card: CardView, theme: AppTheme) {
        card.setCardBackgroundColor(theme.surface)
    }

    private fun applyToMaterialButton(button: MaterialButton, theme: AppTheme) {
        val tag = button.tag?.toString()
        when (tag) {
            "accent" -> {
                button.setBackgroundColor(theme.accent)
                button.setTextColor(theme.textPrimary)
            }
            "surface" -> {
                button.setBackgroundColor(theme.surfaceVariant)
                button.setTextColor(theme.textPrimary)
            }
            else -> {
                button.setBackgroundColor(theme.surfaceVariant)
                button.setTextColor(theme.textPrimary)
            }
        }
    }

    private fun applyToButton(button: Button, theme: AppTheme) {
        button.setBackgroundColor(theme.surfaceVariant)
        button.setTextColor(theme.textPrimary)
    }

    private fun applyToTextView(textView: TextView, theme: AppTheme) {
        val tag = textView.tag?.toString()
        when (tag) {
            "primary" -> textView.setTextColor(theme.textPrimary)
            "secondary" -> textView.setTextColor(theme.textSecondary)
            "muted" -> textView.setTextColor(theme.textMuted)
            "accent" -> textView.setTextColor(theme.accent)
            "error" -> textView.setTextColor(theme.error)
            "success" -> textView.setTextColor(theme.success)
            else -> {
                // Keep default
            }
        }
    }

    private fun applyToEditText(editText: EditText, theme: AppTheme) {
        editText.setTextColor(theme.textPrimary)
        editText.setHintTextColor(theme.textMuted)
    }

    private fun applyToInputLayout(layout: TextInputLayout, theme: AppTheme) {
        val boxBg = GradientDrawable().apply {
            setColor(theme.surface)
            cornerRadius = 36f
            setStroke(2, theme.border)
        }
        layout.boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_NONE
        layout.background = boxBg
        layout.hintTextColor = android.content.res.ColorStateList.valueOf(theme.accentLight)
    }

    private fun applyToChip(chip: Chip, theme: AppTheme) {
        val bg = GradientDrawable().apply {
            setColor(theme.surfaceVariant)
            cornerRadius = 48f
        }
        chip.background = bg
        chip.setTextColor(theme.accentLight)
    }

    private fun applyToProgressBar(progressBar: ProgressBar, theme: AppTheme) {
        progressBar.indeterminateTintList = android.content.res.ColorStateList.valueOf(theme.accent)
    }

    private fun applyToImageButton(button: ImageButton, theme: AppTheme) {
        button.setColorFilter(theme.textMuted)
    }

    fun getAccentColor(theme: AppTheme): Int = theme.accent
    fun getBackgroundColor(theme: AppTheme): Int = theme.background
    fun getSurfaceColor(theme: AppTheme): Int = theme.surface
    fun getErrorColor(theme: AppTheme): Int = theme.error
    fun getSuccessColor(theme: AppTheme): Int = theme.success
}
