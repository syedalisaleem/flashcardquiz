package com.syedali.flashquiz.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.syedali.flashquiz.data.repository.DeckRepository
import com.syedali.flashquiz.model.Deck
import com.syedali.flashquiz.model.DeckStats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeViewState(
    val decks: List<Deck> = emptyList(),
    val stats: DeckStats? = null,
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val deckRepository: DeckRepository
) : ViewModel() {

    private val _state = MutableStateFlow(HomeViewState())
    val state: StateFlow<HomeViewState> = _state.asStateFlow()

    val decks = deckRepository.observeDecks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            try {
                val list = deckRepository.getDecks()
                _state.update { it.copy(decks = list, isLoading = false) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun createDeck(name: String, tags: List<String> = emptyList()) {
        viewModelScope.launch {
            try {
                deckRepository.create(name, tags)
                refresh()
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    fun deleteDeck(id: Long) {
        viewModelScope.launch {
            try {
                deckRepository.delete(id)
                refresh()
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    fun loadStats(deckId: Long) {
        viewModelScope.launch {
            try {
                val stats = deckRepository.getStats(deckId)
                _state.update { it.copy(stats = stats) }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    private suspend fun refresh() {
        val list = deckRepository.getDecks()
        _state.update { it.copy(decks = list, isLoading = false) }
    }
}
