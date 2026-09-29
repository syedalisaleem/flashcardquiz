package com.syedali.flashquiz.ui.generate

import androidx.lifecycle.ViewModel
import com.syedali.flashquiz.domain.GenerateCardsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class GenerateViewState(
    val isGenerating: Boolean = false,
    val generatedCount: Int = 0,
    val error: String? = null
)

@HiltViewModel
class GenerateViewModel @Inject constructor(
    private val generateCards: GenerateCardsUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(GenerateViewState())
    val state = _state.asStateFlow()

    suspend fun generateFlashcards(
        deckId: Long,
        sourceText: String,
        numCards: Int,
        tags: List<String>
    ) {
        if (sourceText.isBlank()) {
            _state.update { it.copy(error = "Source text is empty") }
            return
        }
        _state.update { it.copy(isGenerating = true, error = null) }
        generateCards.flashcards(deckId, sourceText, numCards, tags)
            .onSuccess { cards ->
                _state.update { it.copy(isGenerating = false, generatedCount = cards.size) }
            }
            .onFailure { e ->
                _state.update {
                    it.copy(isGenerating = false, error = e.message ?: "Generation failed")
                }
            }
    }

    suspend fun generateMcqs(
        deckId: Long,
        sourceText: String,
        numMcqs: Int,
        tags: List<String>
    ) {
        if (sourceText.isBlank()) {
            _state.update { it.copy(error = "Source text is empty") }
            return
        }
        _state.update { it.copy(isGenerating = true, error = null) }
        generateCards.mcqs(deckId, sourceText, numMcqs, tags)
            .onSuccess { mcqs ->
                _state.update { it.copy(isGenerating = false, generatedCount = mcqs.size) }
            }
            .onFailure { e ->
                _state.update {
                    it.copy(isGenerating = false, error = e.message ?: "Generation failed")
                }
            }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun resetGenerated() {
        _state.update { it.copy(generatedCount = 0) }
    }
}
