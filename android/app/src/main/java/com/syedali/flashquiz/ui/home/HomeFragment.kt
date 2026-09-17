package com.syedali.flashquiz.ui.home

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.syedali.flashquiz.R
import com.syedali.flashquiz.data.DatabaseHelper
import com.syedali.flashquiz.data.DeckRepository
import com.syedali.flashquiz.theme.ThemeHelper
import com.syedali.flashquiz.theme.ThemeManager

class HomeFragment : Fragment() {
    private lateinit var deckRepo: DeckRepository
    private lateinit var adapter: DeckAdapter
    private lateinit var layoutEmpty: LinearLayout
    private lateinit var chipStats: Chip

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val theme = ThemeManager.getCurrentTheme(requireContext())

        deckRepo = DeckRepository(DatabaseHelper(requireContext()))

        layoutEmpty = view.findViewById(R.id.layout_empty)
        chipStats = view.findViewById(R.id.chip_stats)

        applyTheme(view, theme)

        adapter = DeckAdapter(
            decks = deckRepo.getAll(),
            deckRepo = deckRepo,
            onDeckClick = { deck ->
                val fragment = com.syedali.flashquiz.ui.review.ReviewFragment.newInstance(deck.id, deck.name)
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .addToBackStack(null)
                    .commit()
            },
            onDeleteClick = { deck ->
                AlertDialog.Builder(requireContext())
                    .setTitle("Delete Deck")
                    .setMessage("Delete \"${deck.name}\" and all its cards?")
                    .setPositiveButton("Delete") { _, _ ->
                        deckRepo.delete(deck.id)
                        refreshDeckList()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        )

        val recycler = view.findViewById<RecyclerView>(R.id.recycler_decks)
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        view.findViewById<ExtendedFloatingActionButton>(R.id.fab_add_deck).setOnClickListener {
            showAddDeckDialog()
        }

        refreshDeckList()
    }

    private fun applyTheme(view: View, theme: com.syedali.flashquiz.theme.AppTheme) {
        view.setBackgroundColor(theme.background)

        view.findViewById<TextView>(R.id.tv_welcome)?.setTextColor(theme.textPrimary)
        view.findViewById<TextView>(R.id.tv_subtitle)?.setTextColor(theme.textSecondary)

        val chipBg = GradientDrawable().apply {
            setColor(theme.surfaceVariant)
            cornerRadius = 48f
        }
        chipStats.background = chipBg
        chipStats.setTextColor(theme.accentLight)

        val emptyIcon = view.findViewById<TextView>(R.id.tv_empty_icon)
        emptyIcon?.setTextColor(theme.textMuted)

        val emptyTitle = view.findViewById<TextView>(R.id.tv_empty_title)
        emptyTitle?.setTextColor(theme.textPrimary)

        val emptySubtitle = view.findViewById<TextView>(R.id.tv_empty_subtitle)
        emptySubtitle?.setTextColor(theme.textMuted)

        val fab = view.findViewById<ExtendedFloatingActionButton>(R.id.fab_add_deck)
        fab.backgroundTintList = android.content.res.ColorStateList.valueOf(theme.accent)
        fab.setTextColor(theme.textPrimary)
    }

    private fun refreshDeckList() {
        val decks = deckRepo.getAll()
        adapter.updateDecks(decks)

        if (decks.isEmpty()) {
            layoutEmpty.visibility = View.VISIBLE
            view?.findViewById<RecyclerView>(R.id.recycler_decks)?.visibility = View.GONE
        } else {
            layoutEmpty.visibility = View.GONE
            view?.findViewById<RecyclerView>(R.id.recycler_decks)?.visibility = View.VISIBLE
        }

        var totalDue = 0
        for (deck in decks) {
            val stats = deckRepo.getStats(deck.id)
            totalDue += stats.dueCards + stats.dueMcqs
        }
        chipStats.text = "$totalDue due"
    }

    private fun showAddDeckDialog() {
        val theme = ThemeManager.getCurrentTheme(requireContext())
        val input = EditText(requireContext()).apply {
            hint = "Deck name"
            setPadding(48, 32, 48, 16)
            setTextColor(theme.textPrimary)
            setHintTextColor(theme.textMuted)
        }
        AlertDialog.Builder(requireContext())
            .setTitle("New Deck")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    deckRepo.create(name)
                    refreshDeckList()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        if (::adapter.isInitialized) {
            refreshDeckList()
        }
    }
}
