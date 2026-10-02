package com.syedali.flashquiz.ui.leaderboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.syedali.flashquiz.auth.AccountManager
import com.syedali.flashquiz.data.repository.DeckRepository
import com.syedali.flashquiz.data.repository.LeaderboardRepository
import com.syedali.flashquiz.model.LearntTotals
import com.syedali.flashquiz.model.LeaderboardEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LeaderboardViewState(
    val isLoading: Boolean = true,
    val sort: String = "cards",
    val entries: List<LeaderboardEntry> = emptyList(),
    val total: Int = 0,
    val mine: LearntTotals = LearntTotals(),
    val myRank: Int? = null,
    val signedIn: Boolean = false,
    val backendAvailable: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class LeaderboardViewModel @Inject constructor(
    private val leaderboardRepo: LeaderboardRepository,
    private val deckRepo: DeckRepository
) : ViewModel() {

    private val _state = MutableStateFlow(LeaderboardViewState())
    val state = _state.asStateFlow()

    init {
        _state.update {
            it.copy(
                signedIn = leaderboardRepo.isSignedIn,
                backendAvailable = leaderboardRepo.isConfigured
            )
        }
        load()
    }

    fun setSort(sort: String) {
        if (sort == _state.value.sort && !_state.value.isLoading) return
        load(sort)
    }

    fun load(sort: String = _state.value.sort) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, sort = sort) }
            val mine = deckRepo.getLearntTotals()

            if (!leaderboardRepo.isConfigured) {
                _state.update { it.copy(isLoading = false, mine = mine) }
                return@launch
            }

            leaderboardRepo.load(sort)
                .onSuccess { board ->
                    val uid = AccountManager.getUserId()
                    val rank = board.entries.indexOfFirst { it.uid == uid && uid.isNotBlank() }
                        .let { pos -> if (pos >= 0) board.entries[pos].rank.takeIf { r -> r > 0 } ?: pos + 1 else null }
                    _state.update {
                        it.copy(
                            isLoading = false,
                            entries = board.entries,
                            total = board.total,
                            mine = mine,
                            myRank = rank,
                            signedIn = leaderboardRepo.isSignedIn
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            mine = mine,
                            error = e.message ?: "Could not load the leaderboard"
                        )
                    }
                }
        }
    }
}
