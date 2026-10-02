package com.syedali.flashquiz.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.syedali.flashquiz.MainActivity
import com.syedali.flashquiz.R
import com.syedali.flashquiz.ads.BannerAdManager
import com.syedali.flashquiz.auth.AccountManager
import com.syedali.flashquiz.data.repository.DeckRepository
import com.syedali.flashquiz.model.Deck
import com.syedali.flashquiz.onboarding.ProfileManager
import com.syedali.flashquiz.theme.ThemeManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

@AndroidEntryPoint
class HomeFragment : Fragment() {
    private val viewModel: HomeViewModel by viewModels()

    @Inject lateinit var deckRepo: DeckRepository
    private lateinit var adapter: DeckAdapter
    private lateinit var layoutEmpty: LinearLayout
    private lateinit var tvStreak: TextView
    private lateinit var tvStatCards: TextView
    private lateinit var tvStatMastered: TextView
    private lateinit var tvStatDue: TextView
    private lateinit var tvAccountInitials: TextView
    private lateinit var btnAccount: FrameLayout
    private lateinit var btnStartReview: com.google.android.material.button.MaterialButton

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        layoutEmpty = view.findViewById(R.id.layout_empty)
        tvStreak = view.findViewById(R.id.tv_streak)
        tvStatCards = view.findViewById(R.id.tv_stat_cards)
        tvStatMastered = view.findViewById(R.id.tv_stat_mastered)
        tvStatDue = view.findViewById(R.id.tv_stat_due)
        tvAccountInitials = view.findViewById(R.id.tv_account_initials)
        btnAccount = view.findViewById(R.id.btn_account)
        btnStartReview = view.findViewById(R.id.btn_start_review)

        adapter = DeckAdapter(
            emptyList(),
            emptyMap(),
            onDeckClick = { deck ->
                val reviewFragment = com.syedali.flashquiz.ui.review.ReviewFragment.newInstance(deck.id, deck.name)
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, reviewFragment)
                    .addToBackStack(null)
                    .commit()
            },
            onDeleteClick = { deck ->
                AlertDialog.Builder(requireContext())
                    .setTitle("Delete Deck")
                    .setMessage("Delete \"${deck.name}\"? This cannot be undone.")
                    .setPositiveButton("Delete") { _, _ ->
                        viewModel.deleteDeck(deck.id)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        )

        val recycler = view.findViewById<RecyclerView>(R.id.recycler_decks)
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    if (!state.isLoading) {
                        renderDecks(state.decks)
                    }
                    state.error?.let {
                        viewModel.clearError()
                    }
                }
            }
        }

        // FAB click - navigate to Generate
        val fab = view.findViewById<ExtendedFloatingActionButton>(R.id.fab_add_deck)
        fab.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, com.syedali.flashquiz.ui.generate.GenerateFragment())
                .commit()
            val bottomNav = activity?.findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottom_nav)
            bottomNav?.selectedItemId = R.id.nav_generate
        }

        // Start Review button
        btnStartReview.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, com.syedali.flashquiz.ui.review.ReviewFragment())
                .commit()
            val bottomNav = activity?.findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottom_nav)
            bottomNav?.selectedItemId = R.id.nav_review
        }

        // Account button — show initials
        btnAccount.setOnClickListener {
            if (AccountManager.isLoggedIn()) {
                showAccountMenu()
            } else {
                (activity as? MainActivity)?.showAccountDialog()
            }
        }

        // Update greeting and account avatar
        updateGreeting(view)
        updateAccountAvatar()

        com.syedali.flashquiz.theme.ThemeUtils.applyThemeToViews(view, ThemeManager.getCurrentTheme(requireContext()))

        renderDecks(viewModel.state.value.decks)
    }

    override fun onResume() {
        super.onResume()
        viewLifecycleOwner.lifecycleScope.launch {
            renderDecks(deckRepo.getDecks())
        }
        updateAccountAvatar()
        val adContainer = activity?.findViewById<android.widget.FrameLayout>(R.id.ad_container)
        if (adContainer != null) {
            if (com.syedali.flashquiz.BuildConfig.ADS_ENABLED) {
                BannerAdManager.showBanner(requireActivity(), adContainer)
            } else {
                BannerAdManager.hideBanner(adContainer)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        val adContainer = activity?.findViewById<android.widget.FrameLayout>(R.id.ad_container)
        if (adContainer != null) {
            BannerAdManager.hideBanner(adContainer)
        }
    }

    private fun updateGreeting(view: View) {
        val tvGreeting = view.findViewById<TextView>(R.id.tv_greeting)
        val greeting = getGreetingPrefix()
        if (AccountManager.isLoggedIn()) {
            val name = AccountManager.getDisplayName()
            tvGreeting.text = if (name.isNotEmpty()) "$greeting, $name" else greeting
        } else {
            tvGreeting.text = greeting
        }
    }

    /**
     * Returns time-of-day greeting: Good morning / Good afternoon / Good evening
     */
    private fun getGreetingPrefix(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when {
            hour < 12 -> "Good morning"
            hour < 17 -> "Good afternoon"
            else -> "Good evening"
        }
    }

    private fun updateAccountAvatar() {
        val initials = ProfileManager.getInitials(requireContext())
        tvAccountInitials.text = initials
        // Show profile picture if available, otherwise initials on circle background
        val pictureUri = ProfileManager.getPictureUri(requireContext())
        if (pictureUri != null) {
            val bitmap = decodeSampledBitmap(pictureUri, 256)
            if (bitmap != null) {
                try {
                    val size = (44 * resources.displayMetrics.density).toInt()
                    val croppedBitmap = android.graphics.Bitmap.createScaledBitmap(bitmap, size, size, true)
                    // Use the bitmap as a circular background
                    tvAccountInitials.background = android.graphics.drawable.BitmapDrawable(
                        resources,
                        croppedBitmap
                    )
                    tvAccountInitials.text = "" // Hide initials when photo is shown
                    return
                } catch (_: Exception) {
                    // Fall back to initials below
                }
            }
        }
        // Default: show initials on circle background
        tvAccountInitials.setBackgroundResource(R.drawable.circle_avatar_bg)
        tvAccountInitials.text = initials
    }

    /**
     * Decode a URI-backed image downsampled to roughly [reqSize] pixels so a
     * full-resolution photo cannot OOM, returning null on any failure (stale
     * URI, unreadable stream, decode error) so callers can fall back safely.
     */
    private fun decodeSampledBitmap(uri: android.net.Uri, reqSize: Int): android.graphics.Bitmap? = try {
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        requireContext().contentResolver.openInputStream(uri)?.use {
            android.graphics.BitmapFactory.decodeStream(it, null, bounds)
        }
        var sample = 1
        val largest = maxOf(bounds.outWidth, bounds.outHeight)
        while (largest > 0 && largest / (sample * 2) >= reqSize) sample *= 2
        val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
        requireContext().contentResolver.openInputStream(uri)?.use {
            android.graphics.BitmapFactory.decodeStream(it, null, opts)
        }
    } catch (_: Exception) {
        null
    }

    private fun showAccountMenu() {
        val name = AccountManager.getDisplayName()
        val email = AccountManager.getEmail()

        val items = mutableListOf("Signed in as $email")
        items.add("Upgrade to Premium")
        items.add("Switch theme")
        items.add("Sign out")

        AlertDialog.Builder(requireContext())
            .setTitle(if (name.isNotEmpty()) name else "Account")
            .setItems(items.toTypedArray()) { _, which ->
                when {
                    which == 1 -> {
                        (activity as? MainActivity)?.showSubscription()
                    }
                    items[which] == "Switch theme" -> {
                        val themePicker = com.syedali.flashquiz.theme.ThemePickerDialogFragment()
                        themePicker.setListener(activity as com.syedali.flashquiz.theme.ThemePickerDialogFragment.ThemeSelectionListener)
                        themePicker.show(parentFragmentManager, "theme_picker")
                    }
                    items[which] == "Sign out" -> {
                        AccountManager.logout()
                        updateGreeting(view ?: return@setItems)
                        updateAccountAvatar()
                    }
                }
            }
            .show()
    }

    private fun renderDecks(decks: List<Deck>) {
        if (decks.isEmpty()) {
            layoutEmpty.visibility = View.VISIBLE
            view?.findViewById<RecyclerView>(R.id.recycler_decks)?.visibility = View.GONE
        } else {
            layoutEmpty.visibility = View.GONE
            view?.findViewById<RecyclerView>(R.id.recycler_decks)?.visibility = View.VISIBLE
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val statsById = decks.associate { it.id to deckRepo.getStats(it.id) }
            adapter.updateDecks(decks, statsById)

            var totalCards = 0
            var masteredCards = 0
            var dueCards = 0
            for (stats in statsById.values) {
                totalCards += stats.totalCards + stats.totalMcqs
                masteredCards += stats.masteredCards
                dueCards += stats.dueCards + stats.dueMcqs
            }

            tvStatCards.text = totalCards.toString()
            tvStatMastered.text = masteredCards.toString()
            tvStatDue.text = dueCards.toString()

            animateStatCards()

            val streak = getStreak()
            tvStreak.text = streak.toString()

            btnStartReview.text = if (dueCards > 0) {
                "Review $dueCards due cards"
            } else {
                "Review 0 due cards today"
            }
        }
    }

    private fun animateStatCards() {
        val statContainers = listOf(
            view?.findViewById<View>(R.id.stat_cards_container),
            view?.findViewById<View>(R.id.stat_mastered_container),
            view?.findViewById<View>(R.id.stat_due_container)
        )

        statContainers.forEachIndexed { index, container ->
            container?.let {
                it.alpha = 0f
                it.scaleX = 0.9f
                it.scaleY = 0.9f
                it.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(350L)
                    .setStartDelay((index * 80L))
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .start()
            }
        }
    }

    private fun getStreak(): Int {
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
}
