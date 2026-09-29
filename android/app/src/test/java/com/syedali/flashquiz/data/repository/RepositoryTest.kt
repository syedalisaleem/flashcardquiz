package com.syedali.flashquiz.data.repository

import com.google.common.truth.Truth.assertThat
import com.syedali.flashquiz.data.local.dao.DeckDao
import com.syedali.flashquiz.data.local.dao.FlashcardDao
import com.syedali.flashquiz.data.local.dao.McqDao
import com.syedali.flashquiz.data.local.entity.DeckEntity
import com.syedali.flashquiz.data.local.entity.FlashcardEntity
import com.syedali.flashquiz.data.local.entity.McqEntity
import com.syedali.flashquiz.model.Flashcard
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class FakeDeckDao : DeckDao {
    private val state = MutableStateFlow<List<DeckEntity>>(emptyList())
    private var nextId = 1L

    override fun observeDecks(): Flow<List<DeckEntity>> =
        state.map { list -> list.sortedByDescending { it.createdAt } }

    override suspend fun getDecks(): List<DeckEntity> = observeDecks().first()

    override suspend fun getDeckById(id: Long): DeckEntity? =
        state.value.find { it.id == id }

    override suspend fun insert(deck: DeckEntity): Long {
        val id = if (deck.id == 0L) nextId++ else deck.id
        state.update { list -> list.filterNot { it.id == id } + deck.copy(id = id) }
        return id
    }

    override suspend fun update(deck: DeckEntity) {
        state.update { list -> list.map { if (it.id == deck.id) deck else it } }
    }

    override suspend fun delete(deck: DeckEntity) {
        state.update { list -> list.filterNot { it.id == deck.id } }
    }

    override suspend fun deleteById(id: Long) {
        state.update { list -> list.filterNot { it.id == id } }
    }
}

class FakeFlashcardDao : FlashcardDao {
    private val state = MutableStateFlow<List<FlashcardEntity>>(emptyList())
    private var nextId = 1L

    override fun observeForDeck(deckId: Long): Flow<List<FlashcardEntity>> =
        state.map { list -> list.filter { it.deckId == deckId } }

    override suspend fun getForDeck(deckId: Long): List<FlashcardEntity> =
        observeForDeck(deckId).first()

    override suspend fun getDue(deckId: Long, now: Long): List<FlashcardEntity> =
        getForDeck(deckId).filter { it.nextReview <= now }.sortedBy { it.nextReview }

    override suspend fun getById(id: Long): FlashcardEntity? =
        state.value.find { it.id == id }

    override suspend fun insertAll(cards: List<FlashcardEntity>) {
        cards.forEach { insert(it) }
    }

    override suspend fun insert(card: FlashcardEntity): Long {
        val id = if (card.id == 0L) nextId++ else card.id
        state.update { list -> list.filterNot { it.id == id } + card.copy(id = id) }
        return id
    }

    override suspend fun update(card: FlashcardEntity) {
        state.update { list -> list.map { if (it.id == card.id) card else it } }
    }

    override suspend fun delete(card: FlashcardEntity) {
        state.update { list -> list.filterNot { it.id == card.id } }
    }

    override suspend fun deleteForDeck(deckId: Long) {
        state.update { list -> list.filterNot { it.deckId == deckId } }
    }

    override suspend fun updateSm2(
        id: Long,
        easeFactor: Double,
        interval: Int,
        repetitions: Int,
        nextReview: Long,
        lastReview: Long
    ) {
        val existing = getById(id) ?: return
        update(
            existing.copy(
                easeFactor = easeFactor,
                interval = interval,
                repetitions = repetitions,
                nextReview = nextReview,
                lastReview = lastReview
            )
        )
    }

    fun all(): List<FlashcardEntity> = state.value
}

class FakeMcqDao : McqDao {
    private val state = MutableStateFlow<List<McqEntity>>(emptyList())
    private var nextId = 1L

    override fun observeForDeck(deckId: Long): Flow<List<McqEntity>> =
        state.map { list -> list.filter { it.deckId == deckId } }

    override suspend fun getForDeck(deckId: Long): List<McqEntity> =
        observeForDeck(deckId).first()

    override suspend fun getDue(deckId: Long, now: Long): List<McqEntity> =
        getForDeck(deckId).filter { it.nextReview <= now }.sortedBy { it.nextReview }

    override suspend fun getById(id: Long): McqEntity? =
        state.value.find { it.id == id }

    override suspend fun insertAll(mcqs: List<McqEntity>) {
        mcqs.forEach { insert(it) }
    }

    override suspend fun insert(mcq: McqEntity): Long {
        val id = if (mcq.id == 0L) nextId++ else mcq.id
        state.update { list -> list.filterNot { it.id == id } + mcq.copy(id = id) }
        return id
    }

    override suspend fun update(mcq: McqEntity) {
        state.update { list -> list.map { if (it.id == mcq.id) mcq else it } }
    }

    override suspend fun delete(mcq: McqEntity) {
        state.update { list -> list.filterNot { it.id == mcq.id } }
    }

    override suspend fun deleteForDeck(deckId: Long) {
        state.update { list -> list.filterNot { it.deckId == deckId } }
    }

    override suspend fun updateSm2(
        id: Long,
        easeFactor: Double,
        interval: Int,
        repetitions: Int,
        nextReview: Long,
        lastReview: Long
    ) {
        val existing = getById(id) ?: return
        update(
            existing.copy(
                easeFactor = easeFactor,
                interval = interval,
                repetitions = repetitions,
                nextReview = nextReview,
                lastReview = lastReview
            )
        )
    }

    fun all(): List<McqEntity> = state.value
}

class RepositoryTest {

    private lateinit var deckDao: FakeDeckDao
    private lateinit var flashcardDao: FakeFlashcardDao
    private lateinit var mcqDao: FakeMcqDao
    private lateinit var deckRepository: RoomDeckRepository
    private lateinit var flashcardRepository: RoomFlashcardRepository
    private lateinit var mcqRepository: RoomMcqRepository

    @Before
    fun setUp() {
        deckDao = FakeDeckDao()
        flashcardDao = FakeFlashcardDao()
        mcqDao = FakeMcqDao()
        deckRepository = RoomDeckRepository(deckDao, flashcardDao, mcqDao)
        flashcardRepository = RoomFlashcardRepository(flashcardDao)
        mcqRepository = RoomMcqRepository(mcqDao)
    }

    @Test
    fun createDeck_and_observeDecks() = runTest {
        val id = deckRepository.create("Biology", listOf("science"))
        assertThat(id).isGreaterThan(0L)

        val decks = deckRepository.observeDecks().first()
        assertThat(decks).hasSize(1)
        assertThat(decks.first().name).isEqualTo("Biology")
        assertThat(decks.first().tags).containsExactly("science")
    }

    @Test
    fun addFlashcards_and_getAll() = runTest {
        val deckId = deckRepository.create("Deck")
        flashcardRepository.add(
            deckId,
            listOf(
                Flashcard(deckId = 0, front = "Q1", back = "A1"),
                Flashcard(deckId = 0, front = "Q2", back = "A2")
            )
        )

        val cards = flashcardRepository.getAll(deckId)
        assertThat(cards).hasSize(2)
        assertThat(cards.all { it.deckId == deckId }).isTrue()
    }

    @Test
    fun getDue_returnsOnlyPastDueCards() = runTest {
        val deckId = deckRepository.create("Deck")
        val now = System.currentTimeMillis()
        flashcardDao.insertAll(
            listOf(
                FlashcardEntity(deckId = deckId, front = "due", nextReview = now - 1000),
                FlashcardEntity(deckId = deckId, front = "later", nextReview = now + 86_400_000L)
            )
        )

        val due = flashcardRepository.getDue(deckId)
        assertThat(due).hasSize(1)
        assertThat(due.first().front).isEqualTo("due")
    }

    @Test
    fun updateSm2_updatesEaseFactorAndSchedule() = runTest {
        val deckId = deckRepository.create("Deck")
        flashcardRepository.add(deckId, listOf(Flashcard(deckId = 0, front = "Q", back = "A")))
        val card = flashcardRepository.getAll(deckId).first()

        flashcardRepository.updateSm2(card.id, quality = 5, now = 1_700_000_000_000L)

        val updated = flashcardRepository.getAll(deckId).first()
        assertThat(updated.repetitions).isEqualTo(1)
        assertThat(updated.interval).isEqualTo(1)
        assertThat(updated.easeFactor).isAtLeast(1.3)
        assertThat(updated.nextReview).isGreaterThan(0L)
        assertThat(updated.lastReview).isEqualTo(1_700_000_000_000L)
    }

    @Test
    fun deckStats_countsCardsDueAndMastered() = runTest {
        val deckId = deckRepository.create("Deck")
        val now = System.currentTimeMillis()
        flashcardDao.insertAll(
            listOf(
                FlashcardEntity(deckId = deckId, nextReview = now - 1, interval = 30),
                FlashcardEntity(deckId = deckId, nextReview = now + 999_999L, interval = 0)
            )
        )
        mcqDao.insertAll(
            listOf(McqEntity(deckId = deckId, question = "Q?", options = listOf("a"), correctIndex = 0))
        )

        val stats = deckRepository.getStats(deckId)
        assertThat(stats.totalCards).isEqualTo(2)
        assertThat(stats.totalMcqs).isEqualTo(1)
        assertThat(stats.dueCards).isEqualTo(1)
        assertThat(stats.masteredCards).isEqualTo(1)
    }

    @Test
    fun deleteDeck_cascadesToCardsAndMcqs() = runTest {
        val deckId = deckRepository.create("Deck")
        flashcardRepository.add(deckId, listOf(Flashcard(deckId = 0, front = "Q", back = "A")))
        mcqRepository.add(
            deckId,
            listOf(
                com.syedali.flashquiz.model.MCQ(
                    deckId = 0,
                    question = "Q?",
                    options = listOf("a", "b"),
                    correctIndex = 0
                )
            )
        )

        deckRepository.delete(deckId)

        assertThat(deckRepository.getDecks()).isEmpty()
        assertThat(flashcardDao.all()).isEmpty()
        assertThat(mcqDao.all()).isEmpty()
    }

    @Test
    fun mcqUpdateSm2_failQuality_resetsRepetitions() = runTest {
        val deckId = deckRepository.create("Deck")
        mcqRepository.add(
            deckId,
            listOf(
                com.syedali.flashquiz.model.MCQ(
                    deckId = 0,
                    question = "Q?",
                    options = listOf("a", "b"),
                    correctIndex = 0
                )
            )
        )
        val mcq = mcqDao.getForDeck(deckId).first()
        mcqRepository.updateSm2(mcq.id, quality = 1, now = 1_700_000_000_000L)

        val updated = mcqDao.getById(mcq.id)!!
        assertThat(updated.repetitions).isEqualTo(0)
        assertThat(updated.interval).isEqualTo(1)
    }
}
