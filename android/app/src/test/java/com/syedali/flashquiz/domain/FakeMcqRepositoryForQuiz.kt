package com.syedali.flashquiz.domain

import com.syedali.flashquiz.model.MCQ
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class FakeMcqRepositoryForQuiz : com.syedali.flashquiz.data.repository.McqRepository {
    var lastId: Long? = null
    var lastQuality: Int? = null

    override fun observeForDeck(deckId: Long): Flow<List<MCQ>> = flowOf(emptyList())

    override suspend fun getDue(deckId: Long): List<MCQ> = emptyList()

    override suspend fun add(deckId: Long, mcqs: List<MCQ>) {}

    override suspend fun updateSm2(id: Long, quality: Int, now: Long) {
        lastId = id
        lastQuality = quality
    }
}
