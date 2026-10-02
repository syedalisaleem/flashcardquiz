package com.syedali.flashquiz.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.syedali.flashquiz.data.local.entity.FlashcardEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FlashcardDao {
    @Query("SELECT * FROM flashcards WHERE deck_id = :deckId ORDER BY id")
    fun observeForDeck(deckId: Long): Flow<List<FlashcardEntity>>

    @Query("SELECT * FROM flashcards WHERE deck_id = :deckId ORDER BY id")
    suspend fun getForDeck(deckId: Long): List<FlashcardEntity>

    @Query(
        """SELECT * FROM flashcards
           WHERE deck_id = :deckId AND next_review <= :now
           ORDER BY next_review"""
    )
    suspend fun getDue(deckId: Long, now: Long): List<FlashcardEntity>

    @Query("SELECT * FROM flashcards WHERE id = :id")
    suspend fun getById(id: Long): FlashcardEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(cards: List<FlashcardEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(card: FlashcardEntity): Long

    @Update
    suspend fun update(card: FlashcardEntity)

    @Delete
    suspend fun delete(card: FlashcardEntity)

    @Query("SELECT COUNT(*) FROM flashcards WHERE interval >= 21")
    suspend fun countLearnt(): Int

    @Query("DELETE FROM flashcards WHERE deck_id = :deckId")
    suspend fun deleteForDeck(deckId: Long)

    @Query(
        """UPDATE flashcards SET
           ease_factor = :easeFactor,
           interval = :interval,
           repetitions = :repetitions,
           next_review = :nextReview,
           last_review = :lastReview
           WHERE id = :id"""
    )
    suspend fun updateSm2(
        id: Long,
        easeFactor: Double,
        interval: Int,
        repetitions: Int,
        nextReview: Long,
        lastReview: Long
    )
}
