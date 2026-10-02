package com.syedali.flashquiz.ui.leaderboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.syedali.flashquiz.MainActivity
import com.syedali.flashquiz.R
import com.syedali.flashquiz.auth.AccountManager
import com.syedali.flashquiz.theme.ThemeManager
import com.syedali.flashquiz.theme.ThemeUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LeaderboardFragment : Fragment() {
    private val viewModel: LeaderboardViewModel by viewModels()

    private lateinit var tvYourCards: TextView
    private lateinit var tvYourMcqs: TextView
    private lateinit var tvYourRank: TextView
    private lateinit var signInRow: LinearLayout
    private lateinit var toggleCards: TextView
    private lateinit var toggleMcqs: TextView
    private lateinit var tvError: TextView
    private lateinit var tvEmpty: TextView
    private lateinit var pb: ProgressBar
    private lateinit var recycler: RecyclerView
    private lateinit var adapter: LeaderboardAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_leaderboard, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvYourCards = view.findViewById(R.id.tv_your_cards)
        tvYourMcqs = view.findViewById(R.id.tv_your_mcqs)
        tvYourRank = view.findViewById(R.id.tv_your_rank)
        signInRow = view.findViewById(R.id.sign_in_row)
        toggleCards = view.findViewById(R.id.toggle_cards)
        toggleMcqs = view.findViewById(R.id.toggle_mcqs)
        tvError = view.findViewById(R.id.tv_error)
        tvEmpty = view.findViewById(R.id.tv_empty)
        pb = view.findViewById(R.id.pb_leaderboard)
        recycler = view.findViewById(R.id.recycler_leaderboard)

        adapter = LeaderboardAdapter(emptyList(), AccountManager.getUserId(), "cards")
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        ThemeUtils.applyThemeToViews(view, ThemeManager.getCurrentTheme(requireContext()))

        selectSortUi("cards")
        toggleCards.setOnClickListener { viewModel.setSort("cards") }
        toggleMcqs.setOnClickListener { viewModel.setSort("mcqs") }
        view.findViewById<TextView>(R.id.btn_sign_in).setOnClickListener {
            (activity as? MainActivity)?.showAccountDialog()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect(::render)
            }
        }
    }

    private fun selectSortUi(sort: String) {
        toggleCards.isSelected = sort == "cards"
        toggleMcqs.isSelected = sort == "mcqs"
        toggleCards.setTextColor(if (sort == "cards") ColorOnPrimary else ColorMuted)
        toggleMcqs.setTextColor(if (sort == "mcqs") ColorOnPrimary else ColorMuted)
    }

    private fun render(state: LeaderboardViewState) {
        tvYourCards.text = state.mine.cards.toString()
        tvYourMcqs.text = state.mine.mcqs.toString()

        if (!state.backendAvailable) {
            signInRow.visibility = View.GONE
            tvYourRank.text = "Online mode is off in this build"
        } else {
            signInRow.visibility = if (state.signedIn) View.GONE else View.VISIBLE
            tvYourRank.text = when {
                !state.signedIn -> "Sign in to appear on the board"
                state.myRank != null && state.total > 0 ->
                    "You're ranked #${state.myRank} of ${state.total}"
                else -> "You're signed in — learn more to enter the rankings!"
            }
        }

        selectSortUi(state.sort)

        tvError.visibility = if (state.error != null && !state.isLoading) View.VISIBLE else View.GONE
        state.error?.let { tvError.text = it }

        when {
            state.isLoading -> {
                pb.visibility = View.VISIBLE
                recycler.visibility = View.GONE
                tvEmpty.visibility = View.GONE
            }
            state.entries.isEmpty() -> {
                pb.visibility = View.GONE
                recycler.visibility = View.GONE
                tvEmpty.visibility =
                    if (state.error == null) View.VISIBLE else View.GONE
            }
            else -> {
                pb.visibility = View.GONE
                tvEmpty.visibility = View.GONE
                recycler.visibility = View.VISIBLE
                adapter.update(state.entries, AccountManager.getUserId(), state.sort)
            }
        }
    }

    private companion object {
        val ColorOnPrimary = android.graphics.Color.parseColor("#1A2E28")
        val ColorMuted = android.graphics.Color.parseColor("#685D55")
    }
}
