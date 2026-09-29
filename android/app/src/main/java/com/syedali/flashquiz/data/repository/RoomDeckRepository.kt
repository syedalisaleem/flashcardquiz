package com.syedali.flashquiz.data.repository

import com.syedali.flashquiz.data.local.dao.DeckDao
import com.syedali.flashquiz.data.local.dao.FlashcardDao
import com.syedali.flashquiz.data.local.dao.McqDao
import com.syedali.flashquiz.data.local.entity.DeckEntity
import com.syedali.flashquiz.data.mapper.toDomain
import com.syedali.flashquiz.model.Deck
import com.syedali.flashquiz.model.DeckStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomDeckRepository @Inject constructor(
    private val deckDao: DeckDao,
    private val flashcardDao: FlashcardDao,
    private val mcqDao: McqDao
) : DeckRepository {

    override fun observeDecks(): Flow<List<Deck>> =
        deckDao.observeDecks().map { list -> list.map { it.toDomain() } }

    override suspend fun getDecks(): List<Deck> =
        deckDao.getDecks().map { it.toDomain() }

    override suspend fun create(name: String, tags: List<String>): Long =
        deckDao.insert(DeckEntity(name = name, tags = tags))

    override suspend fun delete(id: Long) {
        flashcardDao.deleteForDeck(id)
        mcqDao.deleteForDeck(id)
        deckDao.deleteById(id)
    }

    override suspend fun getStats(deckId: Long): DeckStats {
        val now = System.currentTimeMillis()
        val cards = flashcardDao.getForDeck(deckId)
        val mcqs = mcqDao.getForDeck(deckId)
        return DeckStats(
            totalCards = cards.size,
            totalMcqs = mcqs.size,
            dueCards = cards.count { it.nextReview <= now },
            dueMcqs = mcqs.count { it.nextReview <= now },
            masteredCards = cards.count { it.interval >= 21 }
        )
    }
}
