package com.syedali.flashquiz.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.syedali.flashquiz.data.local.entity.McqEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface McqDao {
    @Query("SELECT * FROM mcqs WHERE deck_id = :deckId ORDER BY id")
    fun observeForDeck(deckId: Long): Flow<List<McqEntity>>

    @Query("SELECT * FROM mcqs WHERE deck_id = :deckId ORDER BY id")
    suspend fun getForDeck(deckId: Long): List<McqEntity>

    @Query(
        """SELECT * FROM mcqs
           WHERE deck_id = :deckId AND next_review <= :now
           ORDER BY next_review"""
    )
    suspend fun getDue(deckId: Long, now: Long): List<McqEntity>

    @Query("SELECT * FROM mcqs WHERE id = :id")
    suspend fun getById(id: Long): McqEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(mcqs: List<McqEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(mcq: McqEntity): Long

    @Update
    suspend fun update(mcq: McqEntity)

    @Delete
    suspend fun delete(mcq: McqEntity)

    @Query("DELETE FROM mcqs WHERE deck_id = :deckId")
    suspend fun deleteForDeck(deckId: Long)

    @Query(
        """UPDATE mcqs SET
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
