package com.syedali.flashquiz.data.repository

import com.syedali.flashquiz.data.local.dao.McqDao
import com.syedali.flashquiz.data.mapper.toDomain
import com.syedali.flashquiz.data.mapper.toEntity
import com.syedali.flashquiz.engine.SpacedRepetitionEngine
import com.syedali.flashquiz.model.MCQ
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomMcqRepository @Inject constructor(
    private val mcqDao: McqDao
) : McqRepository {

    override fun observeForDeck(deckId: Long): Flow<List<MCQ>> =
        mcqDao.observeForDeck(deckId).map { list -> list.map { it.toDomain() } }

    override suspend fun getDue(deckId: Long): List<MCQ> =
        mcqDao.getDue(deckId, System.currentTimeMillis()).map { it.toDomain() }

    override suspend fun add(deckId: Long, mcqs: List<MCQ>) {
        mcqDao.insertAll(mcqs.map { it.copy(id = 0, deckId = deckId).toEntity() })
    }

    override suspend fun updateSm2(id: Long, quality: Int, now: Long) {
        val entity = mcqDao.getById(id) ?: return
        val result = SpacedRepetitionEngine.calculate(
            currentEaseFactor = entity.easeFactor,
            currentInterval = entity.interval,
            currentRepetitions = entity.repetitions,
            quality = quality
        )
        mcqDao.updateSm2(
            id = id,
            easeFactor = result.easeFactor,
            interval = result.interval,
            repetitions = result.repetitions,
            nextReview = result.nextReview,
            lastReview = now
        )
    }
}
