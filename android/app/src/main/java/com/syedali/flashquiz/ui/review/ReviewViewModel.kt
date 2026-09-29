package com.syedali.flashquiz.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.syedali.flashquiz.data.repository.FlashcardRepository
import com.syedali.flashquiz.data.repository.McqRepository
import com.syedali.flashquiz.domain.QuizAnswerUseCase
import com.syedali.flashquiz.domain.QuizResult
import com.syedali.flashquiz.domain.ReviewCardUseCase
import com.syedali.flashquiz.model.Flashcard
import com.syedali.flashquiz.model.MCQ
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReviewViewState(
    val flashcards: List<Flashcard> = emptyList(),
    val mcqs: List<MCQ> = emptyList(),
    val currentIndex: Int = 0,
    val isFlipped: Boolean = false,
    val quizResult: QuizResult? = null,
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class ReviewViewModel @Inject constructor(
    private val flashcardRepository: FlashcardRepository,
    private val mcqRepository: McqRepository,
    private val reviewCard: ReviewCardUseCase,
    private val quizAnswer: QuizAnswerUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(ReviewViewState())
    val state = _state.asStateFlow()

    private var deckId: Long = -1L

    fun load(deckId: Long) {
        if (this.deckId == deckId && !_state.value.isLoading) return
        this.deckId = deckId
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val now = System.currentTimeMillis()
                val cards = flashcardRepository.getAll(deckId).filter { it.nextReview <= now }
                val mcqs = mcqRepository.getDue(deckId)
                _state.update {
                    it.copy(
                        flashcards = cards,
                        mcqs = mcqs,
                        currentIndex = 0,
                        isFlipped = false,
                        quizResult = null,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun flipCard() {
        _state.update { it.copy(isFlipped = !it.isFlipped) }
    }

    fun answerFlashcard(quality: Int) {
        val current = _state.value.flashcards.getOrNull(_state.value.currentIndex) ?: return
        viewModelScope.launch {
            reviewCard.reviewFlashcard(current.id, quality)
                .onSuccess { advance() }
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    fun answerQuiz(selectedIndex: Int) {
        val current = _state.value.mcqs.getOrNull(_state.value.currentIndex) ?: return
        if (_state.value.quizResult != null) return
        viewModelScope.launch {
            runCatching { quizAnswer(current, selectedIndex) }
                .onSuccess { result ->
                    _state.update { it.copy(quizResult = result) }
                }
                .onFailure { e ->
                    _state.update { it.copy(error = e.message) }
                }
        }
    }

    fun nextQuizQuestion() {
        _state.update { state ->
            val next = (state.currentIndex + 1)
            if (next < state.mcqs.size) {
                state.copy(currentIndex = next, quizResult = null, isFlipped = false)
            } else {
                state.copy(error = "Session complete")
            }
        }
    }

    private fun advance() {
        _state.update { state ->
            val next = state.currentIndex + 1
            val total = maxOf(state.flashcards.size, state.mcqs.size)
            if (next < total) {
                state.copy(currentIndex = next, isFlipped = false, quizResult = null)
            } else {
                state.copy(error = "Session complete")
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}
