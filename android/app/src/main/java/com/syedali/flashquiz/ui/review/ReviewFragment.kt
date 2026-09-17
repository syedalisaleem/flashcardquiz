package com.syedali.flashquiz.ui.review

import android.animation.AnimatorInflater
import android.animation.ObjectAnimator
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.syedali.flashquiz.R
import com.syedali.flashquiz.data.DatabaseHelper
import com.syedali.flashquiz.data.DeckRepository
import com.syedali.flashquiz.data.FlashcardRepository
import com.syedali.flashquiz.model.Deck
import com.syedali.flashquiz.model.Flashcard
import com.syedali.flashquiz.theme.ThemeManager

class ReviewFragment : Fragment() {
    private lateinit var deckRepo: DeckRepository
    private lateinit var flashcardRepo: FlashcardRepository
    private var decks = listOf<Deck>()
    private var currentCards = mutableListOf<Flashcard>()
    private var currentIndex = 0
    private var deckId: Long = -1
    private var isShowingAnswer = false
    private var isAnimating = false

    private lateinit var tvTitle: TextView
    private lateinit var tvDeckName: TextView
    private lateinit var tvProgress: TextView
    private lateinit var spinnerDeck: Spinner
    private lateinit var cardReview: CardView
    private lateinit var tvCardType: TextView
    private lateinit var tvCardFront: TextView
    private lateinit var tvCardBack: TextView
    private lateinit var tvTapHint: TextView
    private lateinit var layoutButtons: LinearLayout
    private lateinit var layoutEmpty: LinearLayout

    companion object {
        fun newInstance(deckId: Long, deckName: String): ReviewFragment {
            return ReviewFragment().apply {
                arguments = Bundle().apply {
                    putLong("deck_id", deckId)
                    putString("deck_name", deckName)
                }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_review, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val dbHelper = DatabaseHelper(requireContext())
        deckRepo = DeckRepository(dbHelper)
        flashcardRepo = FlashcardRepository(dbHelper)

        tvTitle = view.findViewById(R.id.tv_review_title)
        tvDeckName = view.findViewById(R.id.tv_deck_name)
        tvProgress = view.findViewById(R.id.tv_progress)
        spinnerDeck = view.findViewById(R.id.spinner_deck)
        cardReview = view.findViewById(R.id.card_review)
        tvCardType = view.findViewById(R.id.tv_card_type)
        tvCardFront = view.findViewById(R.id.tv_card_front)
        tvCardBack = view.findViewById(R.id.tv_card_back)
        tvTapHint = view.findViewById(R.id.tv_tap_hint)
        layoutButtons = view.findViewById(R.id.layout_buttons)
        layoutEmpty = view.findViewById(R.id.layout_empty)

        applyTheme(view)

        deckId = arguments?.getLong("deck_id", -1) ?: -1
        val deckName = arguments?.getString("deck_name", "")

        if (deckId != -1L) {
            tvDeckName.text = deckName
            spinnerDeck.visibility = View.GONE
            loadDueCards(deckId)
        } else {
            setupDeckSpinner()
        }

        cardReview.setOnClickListener {
            if (!isAnimating) flipCard()
        }

        view.findViewById<MaterialButton>(R.id.btn_again).setOnClickListener { rateCard(1) }
        view.findViewById<MaterialButton>(R.id.btn_hard).setOnClickListener { rateCard(3) }
        view.findViewById<MaterialButton>(R.id.btn_good).setOnClickListener { rateCard(4) }
        view.findViewById<MaterialButton>(R.id.btn_easy).setOnClickListener { rateCard(5) }
    }

    private fun applyTheme(view: View) {
        val theme = ThemeManager.getCurrentTheme(requireContext())
        view.setBackgroundColor(theme.background)

        tvTitle.setTextColor(theme.textPrimary)
        tvDeckName.setTextColor(theme.textSecondary)
        tvProgress.setTextColor(theme.textMuted)

        val cardBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 32f
            setColor(theme.surface)
            setStroke(2, theme.border)
        }
        cardReview.cardElevation = 16f
        cardReview.setContentPadding(0, 0, 0, 0)

        tvCardType.setTextColor(theme.accentLight)
        tvCardFront.setTextColor(theme.textPrimary)
        tvCardBack.setTextColor(theme.textSecondary)
        tvTapHint.setTextColor(theme.textMuted)

        val emptyIcon = view.findViewById<TextView>(R.id.tv_empty_icon)
        emptyIcon?.setTextColor(theme.textMuted)

        val emptyTitle = view.findViewById<TextView>(R.id.tv_empty_title)
        emptyTitle?.setTextColor(theme.textPrimary)

        val emptySubtitle = view.findViewById<TextView>(R.id.tv_empty_subtitle)
        emptySubtitle?.setTextColor(theme.textMuted)

        applyButtonStyle(view.findViewById(R.id.btn_again), theme, theme.error)
        applyButtonStyle(view.findViewById(R.id.btn_hard), theme, theme.warning)
        applyButtonStyle(view.findViewById(R.id.btn_good), theme, theme.success)
        applyButtonStyle(view.findViewById(R.id.btn_easy), theme, theme.accent)
    }

    private fun applyButtonStyle(button: MaterialButton, theme: com.syedali.flashquiz.theme.AppTheme, color: Int) {
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 24f
            setColor(color)
        }
        button.background = bg
        button.setTextColor(theme.textPrimary)
    }

    private fun setupDeckSpinner() {
        decks = deckRepo.getAll()
        if (decks.isEmpty()) {
            tvDeckName.text = "No decks yet"
            cardReview.visibility = View.GONE
            layoutButtons.visibility = View.GONE
            layoutEmpty.visibility = View.VISIBLE
            return
        }

        val names = decks.map { it.name }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, names)
        spinnerDeck.adapter = adapter
        spinnerDeck.visibility = View.VISIBLE

        spinnerDeck.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                deckId = decks[position].id
                tvDeckName.text = decks[position].name
                loadDueCards(deckId)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun loadDueCards(deckId: Long) {
        currentCards = flashcardRepo.getDue(deckId).toMutableList()
        currentIndex = 0
        isShowingAnswer = false

        if (currentCards.isEmpty()) {
            cardReview.visibility = View.GONE
            layoutButtons.visibility = View.GONE
            layoutEmpty.visibility = View.VISIBLE
            tvProgress.text = "0/0"
            playEmptyAnimation()
        } else {
            cardReview.visibility = View.VISIBLE
            layoutEmpty.visibility = View.GONE
            layoutButtons.visibility = View.GONE
            tvProgress.text = "${currentIndex + 1}/${currentCards.size}"
            showCard()
        }
    }

    private fun playEmptyAnimation() {
        val emptyIcon = view?.findViewById<TextView>(R.id.tv_empty_icon) ?: return
        val bounceAnim = ObjectAnimator.ofFloat(emptyIcon, "translationY", 0f, -20f, 0f)
        bounceAnim.duration = 800
        bounceAnim.interpolator = OvershootInterpolator(2f)
        bounceAnim.repeatCount = ObjectAnimator.INFINITE
        bounceAnim.start()
    }

    private fun showCard() {
        if (currentIndex >= currentCards.size) {
            cardReview.visibility = View.GONE
            layoutButtons.visibility = View.GONE
            layoutEmpty.visibility = View.VISIBLE
            playEmptyAnimation()
            return
        }

        val card = currentCards[currentIndex]
        tvCardType.text = card.type.uppercase()
        tvCardFront.text = if (card.type == "Cloze") card.text else card.front
        tvCardBack.text = card.back
        tvCardBack.visibility = View.GONE
        tvTapHint.visibility = View.VISIBLE
        layoutButtons.visibility = View.GONE
        tvProgress.text = "${currentIndex + 1}/${currentCards.size}"
        isShowingAnswer = false

        cardReview.rotationY = 0f
        cardReview.alpha = 1f

        // Slide in animation
        cardReview.translationX = 300f
        cardReview.alpha = 0f
        cardReview.animate()
            .translationX(0f)
            .alpha(1f)
            .setDuration(350)
            .setInterpolator(OvershootInterpolator(1.2f))
            .start()
    }

    private fun flipCard() {
        if (currentIndex >= currentCards.size || isShowingAnswer || isAnimating) return
        isAnimating = true

        val scale = resources.displayMetrics.density
        cardReview.cameraDistance = 12000 * scale

        val flipOut = AnimatorInflater.loadAnimator(context, R.animator.flip_out)
        val flipIn = AnimatorInflater.loadAnimator(context, R.animator.flip_in)

        flipOut.setTarget(cardReview)
        flipIn.setTarget(cardReview)

        flipOut.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                tvCardBack.visibility = View.VISIBLE
                tvTapHint.visibility = View.GONE
                isShowingAnswer = true

                // Slide up buttons with bounce
                layoutButtons.visibility = View.VISIBLE
                layoutButtons.translationY = 100f
                layoutButtons.alpha = 0f
                layoutButtons.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(400)
                    .setInterpolator(OvershootInterpolator(1.5f))
                    .withEndAction { isAnimating = false }
                    .start()
            }
        })

        flipOut.start()
        flipIn.start()
    }

    private fun rateCard(quality: Int) {
        if (currentIndex >= currentCards.size || isAnimating) return
        isAnimating = true

        val card = currentCards[currentIndex]
        flashcardRepo.updateSM2(card.id, quality)

        // Slide out animation
        cardReview.animate()
            .translationX(-300f)
            .alpha(0f)
            .setDuration(300)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                currentIndex++
                isShowingAnswer = false
                showCard()
                isAnimating = false
            }
            .start()
    }
}
