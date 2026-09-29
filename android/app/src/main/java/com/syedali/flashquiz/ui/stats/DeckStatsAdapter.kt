package com.syedali.flashquiz.ui.stats

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.syedali.flashquiz.R
import com.syedali.flashquiz.model.Deck
import com.syedali.flashquiz.model.DeckStats

class DeckStatsAdapter(private val context: Context) : RecyclerView.Adapter<DeckStatsAdapter.DeckStatViewHolder>() {
    private var deckStatsList = listOf<Pair<Deck, DeckStats>>()

    fun submitDeckStats(newList: List<Pair<Deck, DeckStats>>) {
        deckStatsList = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeckStatViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_deck_stat, parent, false)
        return DeckStatViewHolder(view)
    }

    override fun onBindViewHolder(holder: DeckStatViewHolder, position: Int) {
        val (deck, stats) = deckStatsList[position]
        holder.bind(deck, stats)
    }

    override fun getItemCount() = deckStatsList.size

    class DeckStatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val progressRing: ProgressBar = itemView.findViewById(R.id.progress_ring)
        private val tvPercent: TextView = itemView.findViewById(R.id.tv_percent)
        private val tvDeckName: TextView = itemView.findViewById(R.id.tv_deck_name)
        private val tvDeckDetail: TextView = itemView.findViewById(R.id.tv_deck_detail)
        private val ivStatusIcon: ImageView = itemView.findViewById(R.id.tv_status_emoji)

        fun bind(deck: Deck, stats: DeckStats) {
            tvDeckName.text = deck.name
            val total = stats.totalCards + stats.totalMcqs
            tvDeckDetail.text = "$total cards \u2022 ${stats.masteredCards} mastered"

            val percent = if (total > 0) (stats.masteredCards * 100 / total) else 0
            progressRing.progress = percent
            tvPercent.text = "$percent%"

            when {
                percent >= 80 -> {
                    ivStatusIcon.setImageResource(R.drawable.ic_celebration)
                    ivStatusIcon.setColorFilter(Color.parseColor("#FF9600"))
                }
                percent >= 50 -> {
                    ivStatusIcon.setImageResource(R.drawable.ic_books)
                    ivStatusIcon.setColorFilter(Color.parseColor("#22D3EE"))
                }
                percent >= 20 -> {
                    ivStatusIcon.setImageResource(R.drawable.ic_check_circle)
                    ivStatusIcon.setColorFilter(Color.parseColor("#58CC02"))
                }
                else -> {
                    ivStatusIcon.setImageResource(R.drawable.ic_add)
                    ivStatusIcon.setColorFilter(Color.parseColor("#9D97D6"))
                }
            }
        }
    }
}
