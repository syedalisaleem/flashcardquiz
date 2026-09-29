package com.syedali.flashquiz.domain

import com.google.common.truth.Truth.assertThat
import com.syedali.flashquiz.model.MCQ
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class QuizAnswerUseCaseTest {

    private lateinit var mcqRepository: FakeMcqRepositoryForQuiz
    private lateinit var useCase: QuizAnswerUseCase

    private val mcq = MCQ(
        id = 10L,
        deckId = 1L,
        question = "What is 2+2?",
        options = listOf("3", "4", "5", "6"),
        correctIndex = 1,
        explanation = "Basic arithmetic",
        distractorExplanations = mapOf("0" to "Off by one", "2" to "Too high")
    )

    @Before
    fun setUp() {
        mcqRepository = FakeMcqRepositoryForQuiz()
        useCase = QuizAnswerUseCase(mcqRepository)
    }

    @Test
    fun correctAnswer_returnsCorrectResult_andAppliesPassQuality() = runTest {
        val result = useCase(mcq, selectedIndex = 1, now = 1_700_000_000_000L)

        assertThat(result.isCorrect).isTrue()
        assertThat(result.correctIndex).isEqualTo(1)
        assertThat(result.explanation).isEqualTo("Basic arithmetic")
        assertThat(mcqRepository.lastQuality).isEqualTo(5)
        assertThat(mcqRepository.lastId).isEqualTo(10L)
    }

    @Test
    fun wrongAnswer_returnsDistractorExplanation_andAppliesFailQuality() = runTest {
        val result = useCase(mcq, selectedIndex = 2, now = 1_700_000_000_000L)

        assertThat(result.isCorrect).isFalse()
        assertThat(result.selectedExplanation).isEqualTo("Too high")
        assertThat(mcqRepository.lastQuality).isEqualTo(1)
    }
}
