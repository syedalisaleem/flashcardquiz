package com.syedali.flashquiz.ui.stats

import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.syedali.flashquiz.R
import com.syedali.flashquiz.data.repository.DeckRepository
import com.syedali.flashquiz.model.Deck
import com.syedali.flashquiz.model.DeckStats
import com.syedali.flashquiz.theme.ThemeManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class StatsFragment : Fragment() {
    private val viewModel: StatsViewModel by viewModels()

    @Inject lateinit var deckRepo: DeckRepository
    private lateinit var adapter: DeckStatsAdapter

    private lateinit var tvStreakCount: TextView
    private lateinit var tvTodayStatus: TextView
    private lateinit var tvStatTotalReviews: TextView
    private lateinit var tvStatAccuracy: TextView
    private lateinit var tvMotivation: TextView
    private lateinit var tvMotivationSub: TextView
    private lateinit var recyclerDeckStats: RecyclerView

    private lateinit var bars: List<View>

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_stats, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvStreakCount = view.findViewById(R.id.tv_streak_count)
        tvTodayStatus = view.findViewById(R.id.tv_today_status)
        tvStatTotalReviews = view.findViewById(R.id.tv_stat_total_reviews)
        tvStatAccuracy = view.findViewById(R.id.tv_stat_accuracy)
        tvMotivation = view.findViewById(R.id.tv_motivation)
        tvMotivationSub = view.findViewById(R.id.tv_motivation_sub)
        recyclerDeckStats = view.findViewById(R.id.recycler_deck_stats)

        bars = listOf(
            view.findViewById(R.id.bar_mon),
            view.findViewById(R.id.bar_tue),
            view.findViewById(R.id.bar_wed),
            view.findViewById(R.id.bar_thu),
            view.findViewById(R.id.bar_fri),
            view.findViewById(R.id.bar_sat),
            view.findViewById(R.id.bar_sun)
        )

        adapter = DeckStatsAdapter(requireContext())
        recyclerDeckStats.layoutManager = LinearLayoutManager(requireContext())
        recyclerDeckStats.adapter = adapter

        com.syedali.flashquiz.theme.ThemeUtils.applyThemeToViews(view, ThemeManager.getCurrentTheme(requireContext()))

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    if (!state.isLoading) {
                        loadStats()
                    }
                }
            }
        }

        loadStats()
    }

    override fun onResume() {
        super.onResume()
        loadStats()
    }

    private fun loadStats() {
        viewLifecycleOwner.lifecycleScope.launch {
            renderStats(deckRepo.getDecks())
        }
    }

    private suspend fun renderStats(decks: List<Deck>) {
        var totalCards = 0
        var masteredCards = 0
        var dueCards = 0

        val deckStatsList = mutableListOf<Pair<Deck, DeckStats>>()
        for (deck in decks) {
            val stats = deckRepo.getStats(deck.id)
            totalCards += stats.totalCards + stats.totalMcqs
            masteredCards += stats.masteredCards
            dueCards += stats.dueCards + stats.dueMcqs
            deckStatsList.add(deck to stats)
        }

        val streak = calculateStreak()
        val totalReviews = getTotalReviews()

        // Smart defaults for streak
        if (streak == 0) {
            tvStreakCount.text = "Start your streak today"
            tvStreakCount.textSize = 16f
        } else {
            tvStreakCount.text = "$streak day${if (streak != 1) "s" else ""}"
            tvStreakCount.textSize = 28f
        }

        tvTodayStatus.text = if (dueCards > 0) "$dueCards due today" else "No cards due"
        tvStatTotalReviews.text = totalReviews.toString()

        // Smart defaults for accuracy
        if (totalCards == 0) {
            tvStatAccuracy.text = "--"
            tvStatAccuracy.textSize = 22f
        } else {
            val accuracy = masteredCards * 100 / totalCards
            tvStatAccuracy.text = "$accuracy%"
            tvStatAccuracy.textSize = 28f
        }

        // Contextual motivational card based on user progress
        when {
            decks.isEmpty() -> {
                tvMotivation.text = "Create your first deck"
                tvMotivationSub.text = "Takes 2 minutes with AI"
            }
            decks.isNotEmpty() && totalReviews == 0 -> {
                tvMotivation.text = "Review your first cards"
                tvMotivationSub.text = "Build your streak today"
            }
            else -> {
                val cardsMasteredThisWeek = masteredCards // Simplified - using total mastered
                if (cardsMasteredThisWeek > 0) {
                    tvMotivation.text = "You've mastered $cardsMasteredThisWeek cards!"
                    tvMotivationSub.text = "Keep up the great work"
                } else {
                    tvMotivation.text = "Keep going!"
                    tvMotivationSub.text = "You're making progress"
                }
            }
        }

        updateWeeklyBars()
        adapter.submitDeckStats(deckStatsList)

        // Animate deck mastery rings on appear
        animateDeckMasteryRings()
    }

    private fun calculateStreak(): Int {
        val prefs = requireContext().getSharedPreferences("streak", 0)
        val lastReviewDay = prefs.getLong("last_review_day", 0)
        val streak = prefs.getInt("streak_count", 0)
        val today = System.currentTimeMillis() / (1000 * 60 * 60 * 24)

        return when {
            lastReviewDay == today -> streak
            lastReviewDay == today - 1 -> streak
            else -> 0
        }
    }

    private fun getTotalReviews(): Int {
        val prefs = requireContext().getSharedPreferences("streak", 0)
        return prefs.getInt("total_reviews", 0)
    }

    private fun updateWeeklyBars() {
        val density = resources.displayMetrics.density
        val maxHeightPx = (60 * density).toInt()
        val minHeightPx = (4 * density).toInt()

        bars.forEach { bar ->
            val randomHeight = (Math.random() * maxHeightPx).toInt().coerceAtLeast(minHeightPx)
            val params = bar.layoutParams
            params.height = randomHeight
            bar.layoutParams = params
        }
    }

    private fun animateDeckMasteryRings() {
        // Animate rings after a short delay for layout pass
        recyclerDeckStats.post {
            for (i in 0 until recyclerDeckStats.childCount) {
                val child = recyclerDeckStats.getChildAt(i)
                child?.let {
                    it.scaleX = 0f
                    it.scaleY = 0f
                    it.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(400)
                        .setStartDelay((i * 100).toLong())
                        .setInterpolator(AccelerateDecelerateInterpolator())
                        .start()
                }
            }
        }
    }
}
