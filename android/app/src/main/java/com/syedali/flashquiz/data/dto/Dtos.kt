package com.syedali.flashquiz.data.dto

import com.google.gson.annotations.SerializedName
import com.syedali.flashquiz.model.Flashcard
import com.syedali.flashquiz.model.MCQ

data class FlashcardDto(
    val type: String = "Basic",
    val front: String = "",
    val back: String = "",
    val text: String = "",
    val tags: List<String> = emptyList(),
    val source: String = ""
) {
    fun toDomain(deckId: Long): Flashcard = Flashcard(
        deckId = deckId,
        type = type,
        front = front,
        back = back,
        text = text,
        tags = tags,
        source = source
    )
}

data class McqDto(
    val question: String,
    val options: List<String>,
    @SerializedName("correct_index")
    val correctIndex: Int,
    val explanation: String = "",
    @SerializedName("distractor_explanations")
    val distractorExplanations: Map<String, String> = emptyMap(),
    val tags: List<String> = emptyList()
) {
    fun toDomain(deckId: Long): MCQ = MCQ(
        deckId = deckId,
        question = question,
        options = options,
        correctIndex = correctIndex,
        explanation = explanation,
        distractorExplanations = distractorExplanations,
        tags = tags
    )
}

fun Flashcard.toDto(): FlashcardDto = FlashcardDto(
    type = type,
    front = front,
    back = back,
    text = text,
    tags = tags,
    source = source
)

fun MCQ.toDto(): McqDto = McqDto(
    question = question,
    options = options,
    correctIndex = correctIndex,
    explanation = explanation,
    distractorExplanations = distractorExplanations,
    tags = tags
)
