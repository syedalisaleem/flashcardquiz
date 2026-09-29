package com.syedali.flashquiz.data.repository

import com.syedali.flashquiz.data.local.dao.FlashcardDao
import com.syedali.flashquiz.data.mapper.toDomain
import com.syedali.flashquiz.data.mapper.toEntity
import com.syedali.flashquiz.engine.SpacedRepetitionEngine
import com.syedali.flashquiz.model.Flashcard
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomFlashcardRepository @Inject constructor(
    private val flashcardDao: FlashcardDao
) : FlashcardRepository {

    override fun observeForDeck(deckId: Long): Flow<List<Flashcard>> =
        flashcardDao.observeForDeck(deckId).map { list -> list.map { it.toDomain() } }

    override suspend fun getAll(deckId: Long): List<Flashcard> =
        flashcardDao.getForDeck(deckId).map { it.toDomain() }

    override suspend fun getDue(deckId: Long): List<Flashcard> =
        flashcardDao.getDue(deckId, System.currentTimeMillis()).map { it.toDomain() }

    override suspend fun add(deckId: Long, cards: List<Flashcard>) {
        flashcardDao.insertAll(cards.map { it.copy(id = 0, deckId = deckId).toEntity() })
    }

    override suspend fun updateSm2(id: Long, quality: Int, now: Long) {
        val entity = flashcardDao.getById(id) ?: return
        val result = SpacedRepetitionEngine.calculate(
            currentEaseFactor = entity.easeFactor,
            currentInterval = entity.interval,
            currentRepetitions = entity.repetitions,
            quality = quality
        )
        flashcardDao.updateSm2(
            id = id,
            easeFactor = result.easeFactor,
            interval = result.interval,
            repetitions = result.repetitions,
            nextReview = result.nextReview,
            lastReview = now
        )
    }
}
