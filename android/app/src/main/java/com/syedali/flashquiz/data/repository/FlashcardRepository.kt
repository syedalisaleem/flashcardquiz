package com.syedali.flashquiz.data.repository

import com.syedali.flashquiz.model.Flashcard
import kotlinx.coroutines.flow.Flow

interface FlashcardRepository {
    fun observeForDeck(deckId: Long): Flow<List<Flashcard>>
    suspend fun getAll(deckId: Long): List<Flashcard>
    suspend fun getDue(deckId: Long): List<Flashcard>
    suspend fun add(deckId: Long, cards: List<Flashcard>)
    suspend fun updateSm2(id: Long, quality: Int, now: Long = System.currentTimeMillis())
}
