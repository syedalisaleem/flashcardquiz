package com.syedali.flashquiz.theme

import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.cardview.widget.CardView
import com.google.android.material.chip.Chip
import com.syedali.flashquiz.R

/**
 * Applies theme colors to entire view trees.
 * Buttons keep their XML colors (green primary, cyan/green rating buttons) —
 * only neutral surfaces and known text colors are remapped per theme.
 */
object ThemeUtils {

    fun applyThemeToViews(root: View, theme: AppTheme) {
        root.setBackgroundColor(theme.background)
        applyRecursive(root, theme)
    }

    private fun applyRecursive(view: View, theme: AppTheme) {
        when (view) {
            is ViewGroup -> {
                for (i in 0 until view.childCount) {
                    applyRecursive(view.getChildAt(i), theme)
                }
            }
        }

        when (view) {
            is CardView -> view.setCardBackgroundColor(theme.surface)
            is EditText -> {
                view.setTextColor(theme.textPrimary)
                view.setHintTextColor(theme.textMuted)
                applyEditTextBackground(view, theme)
            }
            is TextView -> applyTextView(view, theme)
            is ImageButton -> view.setColorFilter(theme.textMuted)
            is ProgressBar -> view.indeterminateTintList =
                android.content.res.ColorStateList.valueOf(theme.success)
            is Chip -> {
                val bg = GradientDrawable().apply {
                    setColor(theme.surfaceVariant)
                    cornerRadius = 48f
                }
                view.background = bg
                view.setTextColor(theme.accentLight)
            }
            else -> { /* buttons/FABs keep their styled XML colors */ }
        }
    }

    private fun applyTextView(textView: TextView, theme: AppTheme) {
        if (textView is EditText) return
        val currentColor = textView.currentTextColor
        when (currentColor) {
            0xFFF5F3FF.toInt() -> textView.setTextColor(theme.textPrimary)    // #F5F3FF
            0xFFF1F5F9.toInt() -> textView.setTextColor(theme.textPrimary)    // legacy
            0xFFB9B3E6.toInt() -> textView.setTextColor(theme.textSecondary)  // #B9B3E6
            0xFF94A3B8.toInt() -> textView.setTextColor(theme.textSecondary)  // legacy
            0xFF9D97D6.toInt() -> textView.setTextColor(theme.textMuted)      // #9D97D6
            0xFF64748B.toInt() -> textView.setTextColor(theme.textSecondary)  // legacy
            0xFF475569.toInt() -> textView.setTextColor(theme.textMuted)      // legacy
            0xFF22D3EE.toInt() -> textView.setTextColor(theme.accentLight)    // #22D3EE
            0xFF60A5FA.toInt() -> textView.setTextColor(theme.accentLight)    // legacy
            0xFF58CC02.toInt() -> textView.setTextColor(theme.success)        // #58CC02
            0xFF10B981.toInt() -> textView.setTextColor(theme.success)        // legacy
            0xFFFF9600.toInt() -> textView.setTextColor(theme.warning)        // #FF9600
            0xFFF59E0B.toInt() -> textView.setTextColor(theme.warning)        // legacy
            0xFFFF4B4B.toInt() -> textView.setTextColor(theme.error)          // #FF4B4B
            0xFFEF4444.toInt() -> textView.setTextColor(theme.error)          // legacy
        }
    }

    private fun applyEditTextBackground(editText: EditText, theme: AppTheme) {
        val bg = editText.background
        if (bg is GradientDrawable) {
            bg.setColor(theme.surface)
            bg.setStroke(1, theme.border)
        } else if (bg != null) {
            // Keep Material box backgrounds as-is (they carry their own stroke/hint colors)
        } else {
            val newBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 24f
                setColor(theme.surface)
                setStroke(1, theme.border)
            }
            editText.background = newBg
        }
    }
}
