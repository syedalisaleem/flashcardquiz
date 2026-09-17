package com.syedali.flashquiz.ui.home

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.syedali.flashquiz.R
import com.syedali.flashquiz.data.DeckRepository
import com.syedali.flashquiz.model.Deck
import com.syedali.flashquiz.theme.ThemeManager

class DeckAdapter(
    private var decks: List<Deck>,
    private val deckRepo: DeckRepository,
    private val onDeckClick: (Deck) -> Unit,
    private val onDeleteClick: (Deck) -> Unit
) : RecyclerView.Adapter<DeckAdapter.DeckViewHolder>() {

    class DeckViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tv_deck_name)
        val tvCardCount: TextView = view.findViewById(R.id.tv_card_count)
        val tvDueCount: TextView = view.findViewById(R.id.tv_due_count)
        val progressMastery: ProgressBar = view.findViewById(R.id.progress_mastery)
        val btnDelete: ImageButton = view.findViewById(R.id.btn_delete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeckViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_deck, parent, false)
        return DeckViewHolder(view)
    }

    override fun onBindViewHolder(holder: DeckViewHolder, position: Int) {
        val deck = decks[position]
        val stats = deckRepo.getStats(deck.id)
        val theme = ThemeManager.getCurrentTheme(holder.itemView.context)

        val cardBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 24f
            setColor(theme.surface)
            setStroke(1, theme.border)
        }
        holder.itemView.background = cardBg

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
        val mastery = if (total > 0) (stats.masteredCards * 100 / total) else 0
        holder.progressMastery.progress = mastery
        holder.progressMastery.indeterminateTintList = android.content.res.ColorStateList.valueOf(theme.accent)

        holder.btnDelete.setColorFilter(theme.textMuted)

        holder.itemView.setOnClickListener { onDeckClick(deck) }
        holder.btnDelete.setOnClickListener { onDeleteClick(deck) }
    }

    override fun getItemCount() = decks.size

    fun updateDecks(newDecks: List<Deck>) {
        decks = newDecks
        notifyDataSetChanged()
    }
}
