package com.syedali.flashquiz.model

data class Deck(
    val id: Long = 0,
    val name: String,
    val tags: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

data class Flashcard(
    val id: Long = 0,
    val deckId: Long,
    val type: String = "Basic",
    val front: String = "",
    val back: String = "",
    val text: String = "",
    val tags: List<String> = emptyList(),
    val source: String = "",
    val easeFactor: Double = 2.5,
    val interval: Int = 0,
    val repetitions: Int = 0,
    val nextReview: Long = 0,
    val lastReview: Long = 0
)

data class MCQ(
    val id: Long = 0,
    val deckId: Long,
    val question: String,
    val options: List<String>,
    val correctIndex: Int,
    val explanation: String = "",
    val distractorExplanations: Map<String, String> = emptyMap(),
    val tags: List<String> = emptyList(),
    val easeFactor: Double = 2.5,
    val interval: Int = 0,
    val repetitions: Int = 0,
    val nextReview: Long = 0,
    val lastReview: Long = 0
)

data class DeckStats(
    val totalCards: Int = 0,
    val totalMcqs: Int = 0,
    val dueCards: Int = 0,
    val dueMcqs: Int = 0,
    val masteredCards: Int = 0
)
