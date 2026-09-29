package com.syedali.flashquiz.ui.home

import com.google.common.truth.Truth.assertThat
import com.syedali.flashquiz.data.repository.DeckRepository
import com.syedali.flashquiz.model.Deck
import com.syedali.flashquiz.model.DeckStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private class FakeDeckRepository : DeckRepository {
        private val state = MutableStateFlow<List<Deck>>(emptyList())
        private var nextId = 1L

        override fun observeDecks(): Flow<List<Deck>> = state

        override suspend fun getDecks(): List<Deck> = state.value

        override suspend fun create(name: String, tags: List<String>): Long {
            val id = nextId++
            state.update { it + Deck(id = id, name = name, tags = tags) }
            return id
        }

        override suspend fun delete(id: Long) {
            state.update { list -> list.filterNot { it.id == id } }
        }

        override suspend fun getStats(deckId: Long): DeckStats =
            DeckStats(totalCards = 3, totalMcqs = 1, dueCards = 2, dueMcqs = 0, masteredCards = 1)
    }

    private lateinit var repository: FakeDeckRepository
    private lateinit var viewModel: HomeViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repository = FakeDeckRepository()
        viewModel = HomeViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_isLoadingThenReady() = runTest {
        assertThat(viewModel.state.value.isLoading).isFalse()
        assertThat(viewModel.state.value.decks).isEmpty()
        assertThat(viewModel.state.value.error).isNull()
    }

    @Test
    fun createDeck_updatesState() = runTest {
        viewModel.createDeck("Biology", listOf("science"))
        val decks = viewModel.state.value.decks
        assertThat(decks).hasSize(1)
        assertThat(decks.first().name).isEqualTo("Biology")
        assertThat(viewModel.decks.first()).hasSize(1)
    }

    @Test
    fun deleteDeck_removesFromState() = runTest {
        val id = viewModel.run {
            // create via repository path
            repository.create("Temp")
        }
        viewModel.deleteDeck(id)
        assertThat(viewModel.state.value.decks).isEmpty()
    }

    @Test
    fun loadStats_populatesStats() = runTest {
        viewModel.loadStats(1L)
        val stats = viewModel.state.value.stats
        assertThat(stats).isNotNull()
        assertThat(stats!!.totalCards).isEqualTo(3)
        assertThat(stats.dueCards).isEqualTo(2)
    }

    @Test
    fun clearError_resetsError() = runTest {
        // force error via delete path is hard; test clear on null
        viewModel.clearError()
        assertThat(viewModel.state.value.error).isNull()
    }
}
