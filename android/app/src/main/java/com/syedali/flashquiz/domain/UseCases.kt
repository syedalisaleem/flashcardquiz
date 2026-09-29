package com.syedali.flashquiz.domain

import com.syedali.flashquiz.data.repository.DeckRepository
import com.syedali.flashquiz.data.repository.FlashcardRepository
import com.syedali.flashquiz.data.repository.McqRepository
import com.syedali.flashquiz.model.Deck
import com.syedali.flashquiz.model.Flashcard
import com.syedali.flashquiz.model.MCQ
import com.syedali.flashquiz.network.AiRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GenerateCardsUseCase @Inject constructor(
    private val aiRepository: AiRepository,
    private val flashcardRepository: FlashcardRepository,
    private val mcqRepository: McqRepository
) {
    suspend fun flashcards(
        deckId: Long,
        sourceText: String,
        numCards: Int,
        tags: List<String>
    ): Result<List<Flashcard>> =
        aiRepository.generateFlashcards(sourceText, numCards, tags)
            .onSuccess { cards -> flashcardRepository.add(deckId, cards) }

    suspend fun mcqs(
        deckId: Long,
        sourceText: String,
        numMcqs: Int,
        tags: List<String>
    ): Result<List<MCQ>> =
        aiRepository.generateMCQs(sourceText, numMcqs, tags)
            .onSuccess { mcqs -> mcqRepository.add(deckId, mcqs) }
}

class ReviewCardUseCase @Inject constructor(
    private val flashcardRepository: FlashcardRepository,
    private val mcqRepository: McqRepository
) {
    suspend fun reviewFlashcard(cardId: Long, quality: Int): Result<Unit> = runCatching {
        flashcardRepository.updateSm2(cardId, quality)
    }

    suspend fun reviewMcq(mcqId: Long, quality: Int): Result<Unit> = runCatching {
        mcqRepository.updateSm2(mcqId, quality)
    }
}

class QuizAnswerUseCase @Inject constructor(
    private val mcqRepository: McqRepository
) {
    suspend operator fun invoke(
        mcq: MCQ,
        selectedIndex: Int,
        now: Long = System.currentTimeMillis()
    ): QuizResult {
        val correct = selectedIndex == mcq.correctIndex
        val quality = if (correct) 5 else 1
        mcqRepository.updateSm2(mcq.id, quality, now)
        return QuizResult(
            isCorrect = correct,
            correctIndex = mcq.correctIndex,
            explanation = mcq.explanation,
            selectedExplanation = if (!correct) {
                mcq.distractorExplanations[selectedIndex.toString()].orEmpty()
            } else {
                mcq.explanation
            }
        )
    }
}

data class QuizResult(
    val isCorrect: Boolean,
    val correctIndex: Int,
    val explanation: String,
    val selectedExplanation: String
)

class ObserveDecksUseCase @Inject constructor(
    private val deckRepository: DeckRepository
) {
    operator fun invoke(): Flow<List<Deck>> = deckRepository.observeDecks()
}

class ObserveDueCardsUseCase @Inject constructor(
    private val flashcardRepository: FlashcardRepository,
    private val mcqRepository: McqRepository
) {
    fun flashcards(deckId: Long): Flow<List<Flashcard>> =
        flashcardRepository.observeForDeck(deckId)

    fun mcqs(deckId: Long): Flow<List<MCQ>> =
        mcqRepository.observeForDeck(deckId)
}
