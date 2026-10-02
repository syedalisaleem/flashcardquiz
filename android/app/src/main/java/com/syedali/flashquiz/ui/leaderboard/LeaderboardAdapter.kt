package com.syedali.flashquiz.ui.leaderboard

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.syedali.flashquiz.R
import com.syedali.flashquiz.model.LeaderboardEntry

class LeaderboardAdapter(
    private var entries: List<LeaderboardEntry>,
    private var myUid: String,
    private var sort: String
) : RecyclerView.Adapter<LeaderboardAdapter.RowViewHolder>() {

    class RowViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val root: LinearLayout = view.findViewById(R.id.row_root)
        val tvRank: TextView = view.findViewById(R.id.tv_rank)
        val tvName: TextView = view.findViewById(R.id.tv_name)
        val tvScore: TextView = view.findViewById(R.id.tv_score)
    }

    fun update(entries: List<LeaderboardEntry>, myUid: String, sort: String) {
        this.entries = entries
        this.myUid = myUid
        this.sort = sort
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowViewHolder =
        RowViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.item_leaderboard, parent, false)
        )

    override fun getItemCount(): Int = entries.size

    override fun onBindViewHolder(holder: RowViewHolder, position: Int) {
        val entry = entries[position]
        val density = holder.itemView.resources.displayMetrics.density

        holder.tvRank.text = entry.rank.toString()
        val badge = GradientDrawable().apply { shape = GradientDrawable.OVAL }
        when (entry.rank) {
            1 -> {
                badge.setColor(Color.parseColor("#FFD700"))
                holder.tvRank.setTextColor(Color.parseColor("#312116"))
            }
            2 -> {
                badge.setColor(Color.parseColor("#D9D9D9"))
                holder.tvRank.setTextColor(Color.parseColor("#312116"))
            }
            3 -> {
                badge.setColor(Color.parseColor("#CD7F32"))
                holder.tvRank.setTextColor(Color.WHITE)
            }
            else -> {
                badge.setColor(Color.parseColor("#A8D9C4"))
                holder.tvRank.setTextColor(Color.parseColor("#312116"))
            }
        }
        holder.tvRank.background = badge

        holder.tvName.text = entry.name.ifBlank { "Learner" }
        holder.tvScore.text =
            if (sort == "mcqs") "${entry.mcqs} MCQs" else "${entry.cards} cards"

        val isMe = myUid.isNotBlank() && entry.uid == myUid
        if (isMe) {
            val highlight = GradientDrawable().apply {
                setColor(Color.parseColor("#FFF3B0"))
                cornerRadius = 16f * density
            }
            holder.root.background = highlight
        } else {
            holder.root.setBackgroundResource(R.drawable.deck_card_bg)
        }
    }
}
