package com.syedali.flashquiz.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.syedali.flashquiz.data.repository.DeckRepository
import com.syedali.flashquiz.model.Deck
import com.syedali.flashquiz.model.DeckStats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StatsRow(
    val deck: Deck,
    val stats: DeckStats
)

data class StatsViewState(
    val rows: List<StatsRow> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val deckRepository: DeckRepository
) : ViewModel() {

    private val _state = MutableStateFlow(StatsViewState())
    val state = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val decks = deckRepository.getDecks()
                val rows = decks.map { deck ->
                    StatsRow(deck = deck, stats = deckRepository.getStats(deck.id))
                }
                _state.update { it.copy(rows = rows, isLoading = false) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }
}
