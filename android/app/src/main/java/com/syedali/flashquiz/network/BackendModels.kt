package com.syedali.flashquiz.network

import com.google.gson.annotations.SerializedName
import com.syedali.flashquiz.model.Flashcard
import com.syedali.flashquiz.model.MCQ

data class BackendOcrResponse(
    val text: String? = null,
    val ocr: Boolean? = null,
    val images: Int? = null
)

/** Mirrors the backend's `GenerateRequest` (snake_case on the wire). */
data class BackendGenerateRequest(
    @SerializedName("source_text") val sourceText: String,
    val flashcards: Boolean = true,
    val mcqs: Boolean = true,
    @SerializedName("num_cards") val numCards: Int = 15,
    @SerializedName("num_mcqs") val numMcqs: Int = 5,
    val tags: List<String> = emptyList()
)

data class BackendCardDto(
    val type: String? = null,
    val front: String? = null,
    val back: String? = null,
    val text: String? = null,
    val tags: List<String>? = null,
    val source: String? = null
)

data class BackendMcqDto(
    val question: String? = null,
    val options: List<String>? = null,
    @SerializedName("correct_index") val correctIndex: Int? = null,
    val explanation: String? = null,
    @SerializedName("distractor_explanations")
    val distractorExplanations: Map<String, String>? = null,
    val tags: List<String>? = null
)

data class BackendStudyRequest(
    val prompt: String,
    @SerializedName("class_name") val className: String? = null,
    val school: String? = null,
    val city: String? = null,
    val country: String? = null,
    @SerializedName("deck_name") val deckName: String? = null,
    @SerializedName("num_cards") val numCards: Int = 15,
    @SerializedName("num_mcqs") val numMcqs: Int = 5,
    val tags: List<String> = emptyList()
)

data class BackendStudyResponse(
    @SerializedName("deck_id") val deckId: Int? = null,
    @SerializedName("job_id") val jobId: String? = null,
    val provider: String? = null,
    val chars: Int? = null
)

/** Mirrors the backend's job record from GET /api/jobs/{id}. */
data class BackendJobDto(
    val status: String? = null,
    val cards: Int? = null,
    val mcqs: Int? = null,
    @SerializedName("target_cards") val targetCards: Int? = null,
    @SerializedName("target_mcqs") val targetMcqs: Int? = null,
    val error: String? = null,
    @SerializedName("reset_ms") val resetMs: Long? = null,
    val wait_s: Int? = null
)

/** Mirrors GET /api/decks/{id}: the server-side generated deck. */
data class BackendDeckResponse(
    val id: Int? = null,
    val name: String? = null,
    val cards: List<BackendCardDto>? = null,
    val mcqs: List<BackendMcqDto>? = null
)

data class BackendGenerateResponse(
    val flashcards: List<BackendCardDto>? = null,
    val mcqs: List<BackendMcqDto>? = null
)

fun BackendCardDto.toFlashcardOrNull(deckTags: List<String>): Flashcard? {
    val type = if (type.equals("Cloze", ignoreCase = true)) "Cloze" else "Basic"
    val front = front.orEmpty().trim()
    val back = back.orEmpty().trim()
    val text = text.orEmpty().trim()
    val usable = if (type == "Cloze") text.isNotBlank() else front.isNotBlank() && back.isNotBlank()
    if (!usable) return null
    return Flashcard(
        deckId = 0,
        type = type,
        front = front,
        back = back,
        text = text,
        tags = (deckTags + (tags ?: emptyList())).distinct(),
        source = source.orEmpty()
    )
}

fun BackendMcqDto.toMcqOrNull(deckTags: List<String>): MCQ? {
    val question = question.orEmpty().trim()
    val options = options.orEmpty()
    val correctIndex = correctIndex ?: -1
    if (question.isEmpty() || options.size < 2 || correctIndex !in options.indices) return null
    return MCQ(
        deckId = 0,
        question = question,
        options = options,
        correctIndex = correctIndex,
        explanation = explanation.orEmpty(),
        distractorExplanations = distractorExplanations.orEmpty(),
        tags = (deckTags + (tags ?: emptyList())).distinct()
    )
}

