package com.syedali.flashquiz.theme

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.syedali.flashquiz.R

class ThemePickerDialogFragment : DialogFragment() {

    interface ThemeSelectionListener {
        fun onThemeSelected(theme: AppTheme)
    }

    private var listener: ThemeSelectionListener? = null
    private var currentThemeId: String = "dark"

    fun setListener(listener: ThemeSelectionListener) {
        this.listener = listener
    }

    fun setCurrentTheme(themeId: String) {
        this.currentThemeId = themeId
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.dialog_theme_picker, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val currentTheme = ThemeManager.getCurrentTheme(requireContext())
        view.setBackgroundColor(currentTheme.background)

        val title = view.findViewById<TextView>(R.id.tv_title)
        title.setTextColor(currentTheme.textPrimary)

        val recycler = view.findViewById<RecyclerView>(R.id.recycler_themes)
        recycler.layoutManager = GridLayoutManager(requireContext(), 2)

        val adapter = ThemeAdapter(
            themes = ThemeManager.themes,
            currentThemeId = currentThemeId,
            onSelect = { theme ->
                listener?.onThemeSelected(theme)
                dismiss()
            }
        )
        recycler.adapter = adapter

        view.findViewById<View>(R.id.btn_close).setOnClickListener {
            dismiss()
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }

    class ThemeAdapter(
        private val themes: List<AppTheme>,
        private val currentThemeId: String,
        private val onSelect: (AppTheme) -> Unit
    ) : RecyclerView.Adapter<ThemeAdapter.ThemeViewHolder>() {

        class ThemeViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val colorPreview: View = view.findViewById(R.id.view_color_preview)
            val tvName: TextView = view.findViewById(R.id.tv_theme_name)
            val tvAccent: TextView = view.findViewById(R.id.tv_accent_color)
            val checkMark: TextView = view.findViewById(R.id.view_check)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThemeViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_theme, parent, false)
            return ThemeViewHolder(view)
        }

        override fun onBindViewHolder(holder: ThemeViewHolder, position: Int) {
            val theme = themes[position]
            val ctx = holder.itemView.context
            val isSelected = theme.id == currentThemeId

            val bg = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = 24f
                setColor(theme.surface)
                setStroke(3, if (isSelected) theme.accent else theme.border)
            }
            holder.itemView.background = bg

            val previewBg = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = 16f
                colors = intArrayOf(theme.background, theme.surface, theme.accent)
            }
            holder.colorPreview.background = previewBg

            holder.tvName.text = theme.name
            holder.tvName.setTextColor(theme.textPrimary)

            val accentHex = String.format("#%06X", (0xFFFFFF and theme.accent))
            holder.tvAccent.text = accentHex
            holder.tvAccent.setTextColor(theme.textMuted)

            holder.checkMark.visibility = if (isSelected) View.VISIBLE else View.INVISIBLE
            holder.checkMark.setTextColor(theme.accent)

            holder.itemView.setOnClickListener { onSelect(theme) }
        }

        override fun getItemCount() = themes.size
    }
}
