package com.syedali.flashquiz.network

import com.google.common.truth.Truth.assertThat
import com.syedali.flashquiz.model.Flashcard
import com.syedali.flashquiz.model.MCQ
import org.junit.Test

class GenerationSupportTest {

    @Test
    fun shortSourceIsOneChunk() {
        assertThat(splitSourceChunks("hello world")).isEqualTo(listOf("hello world"))
    }

    @Test
    fun longSourceSplitsIntoFullChunksWithoutLosingCharacters() {
        val text = "a".repeat(SOURCE_CHUNK_CHARS * 2 + 10)
        val chunks = splitSourceChunks(text)
        assertThat(chunks).hasSize(3)
        assertThat(chunks[0]).hasLength(SOURCE_CHUNK_CHARS)
        assertThat(chunks.sumOf { it.length }).isEqualTo(text.length)
    }

    @Test
    fun blankSourceStillProducesOneChunk() {
        assertThat(splitSourceChunks("   ")).isEqualTo(listOf(""))
        assertThat(splitSourceChunks("", size = 10)).isEqualTo(listOf(""))
    }

    @Test
    fun budgetDividesRemainderWithoutDroppingCards() {
        assertThat(chunkBudget(10, 3)).isEqualTo(4) // 4 + 4 + 2
        assertThat(chunkBudget(1, 1)).isEqualTo(1)
        assertThat(chunkBudget(7, 7)).isEqualTo(1)
        assertThat(chunkBudget(5, 0)).isEqualTo(0)
        assertThat(chunkBudget(0, 5)).isEqualTo(0)
        assertThat(chunkBudget(-1, 5)).isEqualTo(0)
    }

    @Test
    fun dedupeKeepsFirstOccurrenceAcrossBatches() {
        val first = card("Q: Alpha", "A")
        val second = card("Q: Beta", "B")
        val existing = listOf(first)

        val merged = distinctNew(listOf(first, second), existing, ::cardKey)

        assertThat(merged).hasSize(1)
        assertThat(merged[0].front).isEqualTo("Q: Beta")
    }

    @Test
    fun dedupeAlsoCollapsesDuplicatesInsideOneBatch() {
        val a = card("Q: Same", "A")
        val b = card("Q: Same", "A")
        val merged = distinctNew(listOf(a, b), emptyList(), ::cardKey)
        assertThat(merged).hasSize(1)
    }

    @Test
    fun cardKeyPrefersClozeTextAndIgnoresCase() {
        val cloze = Flashcard(deckId = 0, type = "Cloze", text = "The {{c1::nucleus}}")
        assertThat(cardKey(cloze)).isEqualTo("the {{c1::nucleus}}")
        assertThat(cardKey(card("Q", "A"))).isEqualTo(cardKey(card("q", "a")))
        assertThat(cardKey(Flashcard(deckId = 0))).isEmpty()
    }

    @Test
    fun mcqKeyIsTrimmedAndCaseInsensitive() {
        val m = MCQ(deckId = 0, question = " What is ATP? ", options = listOf("a", "b"), correctIndex = 0)
        assertThat(mcqKey(m)).isEqualTo("what is atp?")
    }

    @Test
    fun invalidBackendCardsAreDroppedRatherThanStored() {
        val good = BackendCardDto(type = "Basic", front = "Q", back = "A", tags = listOf("x"))
        val emptyCloze = BackendCardDto(type = "Cloze", text = "")
        val emptyBasic = BackendCardDto(type = "Basic", front = "   ", back = "A")

        assertThat(good.toFlashcardOrNull(listOf("bio"))!!.tags)
            .containsExactly("bio", "x")
        assertThat(emptyCloze.toFlashcardOrNull(emptyList())).isNull()
        assertThat(emptyBasic.toFlashcardOrNull(emptyList())).isNull()
    }

    @Test
    fun invalidBackendMcqsAreDroppedRatherThanStored() {
        val good = BackendMcqDto(
            question = "Q?",
            options = listOf("a", "b", "c", "d"),
            correctIndex = 2,
            distractorExplanations = mapOf("1" to "no"),
            tags = listOf("bio")
        )
        val outOfRange = good.copy(correctIndex = 9)
        val tooFewOptions = good.copy(options = listOf("only one"))

        val mapped = good.toMcqOrNull(listOf("tag"))
        assertThat(mapped).isNotNull()
        assertThat(mapped!!.correctIndex).isEqualTo(2)
        assertThat(mapped.tags).containsExactly("tag", "bio").inOrder()
        assertThat(outOfRange.toMcqOrNull(emptyList())).isNull()
        assertThat(tooFewOptions.toMcqOrNull(emptyList())).isNull()
    }

    @Test
    fun ocrErrorsAreAlwaysGivenAReadableMessage() {
        assertThat(friendlyOcrError(RuntimeException(""))).isEqualTo("OCR failed. Try a different image.")
        assertThat(friendlyOcrError(RuntimeException(null as String?)))
            .isEqualTo("OCR failed. Try a different image.")
        assertThat(friendlyOcrError(RuntimeException("boom"))).isEqualTo("boom")
        assertThat(friendlyOcrError(RuntimeException("read timeout")))
            .contains("timed out")
        assertThat(
            friendlyOcrError(RuntimeException("Unable to resolve host \"api.ocr.space\""))
        ).contains("No connection")
    }

    private fun card(front: String, back: String) =
        Flashcard(deckId = 0, type = "Basic", front = front, back = back)
}
