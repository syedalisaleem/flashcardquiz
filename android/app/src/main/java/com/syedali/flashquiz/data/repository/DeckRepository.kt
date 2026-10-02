package com.syedali.flashquiz.data.repository

import com.syedali.flashquiz.model.Deck
import com.syedali.flashquiz.model.DeckStats
import com.syedali.flashquiz.model.LearntTotals
import kotlinx.coroutines.flow.Flow

interface DeckRepository {
    fun observeDecks(): Flow<List<Deck>>
    suspend fun getDecks(): List<Deck>
    suspend fun create(name: String, tags: List<String> = emptyList()): Long
    suspend fun delete(id: Long)
    suspend fun getStats(deckId: Long): DeckStats
    suspend fun getLearntTotals(): LearntTotals
}
