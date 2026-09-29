package com.syedali.flashquiz.data.repository

import com.syedali.flashquiz.model.MCQ
import kotlinx.coroutines.flow.Flow

interface McqRepository {
    fun observeForDeck(deckId: Long): Flow<List<MCQ>>
    suspend fun getDue(deckId: Long): List<MCQ>
    suspend fun add(deckId: Long, mcqs: List<MCQ>)
    suspend fun updateSm2(id: Long, quality: Int, now: Long = System.currentTimeMillis())
}
