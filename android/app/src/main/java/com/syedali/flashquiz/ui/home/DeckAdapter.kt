package com.syedali.flashquiz.ui.home

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.syedali.flashquiz.R
import com.syedali.flashquiz.model.Deck
import com.syedali.flashquiz.model.DeckStats
import com.syedali.flashquiz.theme.ThemeManager

class DeckAdapter(
    private var decks: List<Deck>,
    private var statsById: Map<Long, DeckStats> = emptyMap(),
    private val onDeckClick: (Deck) -> Unit,
    private val onDeleteClick: (Deck) -> Unit
) : RecyclerView.Adapter<DeckAdapter.DeckViewHolder>() {

    class DeckViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tv_deck_name)
        val tvCardCount: TextView = view.findViewById(R.id.tv_card_count)
        val tvDueCount: TextView = view.findViewById(R.id.tv_due_count)
        val progressMastery: ProgressBar = view.findViewById(R.id.progress_mastery)
        val btnDelete: ImageButton = view.findViewById(R.id.btn_delete)
        val viewAccent: View = view.findViewById(R.id.view_accent)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeckViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_deck, parent, false)
        return DeckViewHolder(view)
    }

    override fun onBindViewHolder(holder: DeckViewHolder, position: Int) {
        val deck = decks[position]
        val stats = statsById[deck.id] ?: DeckStats()
        val theme = ThemeManager.getCurrentTheme(holder.itemView.context)

        // Card background with ripple (from deck_item_bg.xml)
        holder.itemView.background = ContextCompat.getDrawable(holder.itemView.context, R.drawable.deck_item_bg)

        holder.tvName.text = deck.name
        holder.tvName.setTextColor(theme.textPrimary)

        holder.tvCardCount.text = "${stats.totalCards} cards, ${stats.totalMcqs} MCQs"
        holder.tvCardCount.setTextColor(theme.textSecondary)

        holder.tvDueCount.text = "${stats.dueCards + stats.dueMcqs} due"
        val dueBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 24f
            setColor(theme.surfaceVariant)
        }
        holder.tvDueCount.background = dueBg
        holder.tvDueCount.setTextColor(theme.accentLight)

        val total = stats.totalCards + stats.totalMcqs
        val mastered = stats.masteredCards
        val mastery = if (total > 0) (mastered * 100 / total) else 0
        holder.progressMastery.progress = mastery
        holder.progressMastery.indeterminateTintList = android.content.res.ColorStateList.valueOf(theme.accent)

        // Left color accent based on deck progress:
        // Red = new (0-20%), Yellow = learning (21-80%), Green = mastered (81-100%)
        val accentColor = when {
            total == 0 -> 0xFF8B84C7.toInt()  // gray when no cards
            mastery <= 20 -> 0xFFFF4B4B.toInt()  // red - new
            mastery <= 80 -> 0xFFFF9600.toInt()  // amber - learning
            else -> 0xFF58CC02.toInt()  // green - mastered
        }
        val accentBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 4f
            setColor(accentColor)
        }
        holder.viewAccent.background = accentBg

        // Overflow menu instead of prominent trash icon
        holder.btnDelete.setColorFilter(theme.textMuted)

        // Tap with ripple feedback
        holder.itemView.setOnClickListener {
            // Subtle press animation
            it.animate()
                .scaleX(0.97f)
                .scaleY(0.97f)
                .setDuration(80)
                .withEndAction {
                    it.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(80)
                        .start()
                }
                .start()
            onDeckClick(deck)
        }
        holder.btnDelete.setOnClickListener { onDeleteClick(deck) }

        // Entrance animation: fade in + slide up
        holder.itemView.alpha = 0f
        holder.itemView.translationY = 30f
        holder.itemView.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(350L)
            .setStartDelay((position * 60L).coerceAtMost(300L))
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
    }

    override fun getItemCount() = decks.size

    fun updateDecks(newDecks: List<Deck>, newStatsById: Map<Long, DeckStats> = statsById) {
        decks = newDecks
        statsById = newStatsById
        notifyDataSetChanged()
    }
}
