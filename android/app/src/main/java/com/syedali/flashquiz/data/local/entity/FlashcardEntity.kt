package com.syedali.flashquiz.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "flashcards",
    foreignKeys = [
        ForeignKey(
            entity = DeckEntity::class,
            parentColumns = ["id"],
            childColumns = ["deck_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("deck_id")]
)
data class FlashcardEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "deck_id")
    val deckId: Long,
    val type: String = "Basic",
    val front: String = "",
    val back: String = "",
    val text: String = "",
    val tags: List<String> = emptyList(),
    val source: String = "",
    @ColumnInfo(name = "ease_factor")
    val easeFactor: Double = 2.5,
    val interval: Int = 0,
    val repetitions: Int = 0,
    @ColumnInfo(name = "next_review")
    val nextReview: Long = 0,
    @ColumnInfo(name = "last_review")
    val lastReview: Long = 0
)
