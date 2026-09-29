package com.syedali.flashquiz.data.mapper

import com.google.common.truth.Truth.assertThat
import com.syedali.flashquiz.data.dto.toDto
import com.syedali.flashquiz.data.dto.FlashcardDto
import com.syedali.flashquiz.data.dto.McqDto
import com.syedali.flashquiz.data.local.entity.DeckEntity
import com.syedali.flashquiz.data.local.entity.FlashcardEntity
import com.syedali.flashquiz.data.local.entity.McqEntity
import com.syedali.flashquiz.model.Deck
import com.syedali.flashquiz.model.Flashcard
import com.syedali.flashquiz.model.MCQ
import org.junit.Test

class MappersTest {

    @Test
    fun deckEntity_toDomain_roundTrip() {
        val entity = DeckEntity(
            id = 7L,
            name = "Biology",
            tags = listOf("science", "exam"),
            createdAt = 1_700_000_000_000L
        )
        val domain = entity.toDomain()
        assertThat(domain.id).isEqualTo(7L)
        assertThat(domain.name).isEqualTo("Biology")
        assertThat(domain.tags).containsExactly("science", "exam").inOrder()
        assertThat(domain.createdAt).isEqualTo(1_700_000_000_000L)

        val back = domain.toEntity()
        assertThat(back).isEqualTo(entity)
    }

    @Test
    fun flashcardEntity_toDomain_preservesSm2Fields() {
        val entity = FlashcardEntity(
            id = 42L,
            deckId = 3L,
            type = "Cloze",
            front = "",
            back = "",
            text = "The {{c1::mitochondria}} produces ATP.",
            tags = listOf("bio"),
            source = "Section 2",
            easeFactor = 2.7,
            interval = 6,
            repetitions = 2,
            nextReview = 1_700_000_100_000L,
            lastReview = 1_699_400_000_000L
        )
        val domain = entity.toDomain()
        assertThat(domain.easeFactor).isEqualTo(2.7)
        assertThat(domain.interval).isEqualTo(6)
        assertThat(domain.repetitions).isEqualTo(2)
        assertThat(domain.nextReview).isEqualTo(1_700_000_100_000L)
        assertThat(domain.lastReview).isEqualTo(1_699_400_000_000L)
        assertThat(domain.text).contains("mitochondria")

        assertThat(domain.toEntity()).isEqualTo(entity)
    }

    @Test
    fun flashcard_toEntity_defaultsMatchSchema() {
        val domain = Flashcard(deckId = 1L, front = "Q", back = "A")
        val entity = domain.toEntity()
        assertThat(entity.easeFactor).isEqualTo(2.5)
        assertThat(entity.interval).isEqualTo(0)
        assertThat(entity.repetitions).isEqualTo(0)
        assertThat(entity.nextReview).isEqualTo(0L)
        assertThat(entity.lastReview).isEqualTo(0L)
    }

    @Test
    fun mcqEntity_toDomain_preservesOptionsAndExplanations() {
        val entity = McqEntity(
            id = 9L,
            deckId = 2L,
            question = "What is 2+2?",
            options = listOf("3", "4", "5", "6"),
            correctIndex = 1,
            explanation = "Basic arithmetic",
            distractorExplanations = mapOf("0" to "Off by one", "2" to "Too high"),
            tags = listOf("math"),
            easeFactor = 2.5,
            interval = 0,
            repetitions = 0,
            nextReview = 0L,
            lastReview = 0L
        )
        val domain = entity.toDomain()
        assertThat(domain.options).containsExactly("3", "4", "5", "6").inOrder()
        assertThat(domain.correctIndex).isEqualTo(1)
        assertThat(domain.distractorExplanations).containsEntry("0", "Off by one")

        assertThat(domain.toEntity()).isEqualTo(entity)
    }

    @Test
    fun domain_toDto_roundTrip() {
        val flashcard = Flashcard(
            deckId = 1L,
            type = "Basic",
            front = "Topic: Focus",
            back = "Concise answer",
            tags = listOf("t1"),
            source = "Section 1"
        )
        val dto: FlashcardDto = flashcard.toDto()
        assertThat(dto.front).isEqualTo("Topic: Focus")
        assertThat(dto.back).isEqualTo("Concise answer")

        val mcq = MCQ(
            deckId = 1L,
            question = "Q?",
            options = listOf("a", "b", "c", "d"),
            correctIndex = 2,
            explanation = "because",
            distractorExplanations = mapOf("0" to "no"),
            tags = listOf("tag")
        )
        val mcqDto: McqDto = mcq.toDto()
        assertThat(mcqDto.correctIndex).isEqualTo(2)
        assertThat(mcqDto.options).hasSize(4)

        val backToDomain = dto.toDomain(deckId = 1L)
        assertThat(backToDomain.front).isEqualTo(flashcard.front)
        assertThat(backToDomain.tags).isEqualTo(flashcard.tags)
    }

    @Test
    fun deck_defaultsMatchLegacySchema() {
        val deck = Deck(name = "New")
        assertThat(deck.id).isEqualTo(0L)
        assertThat(deck.tags).isEmpty()
        assertThat(deck.createdAt).isGreaterThan(0L)
    }
}
