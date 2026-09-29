package com.syedali.flashquiz.network

import com.google.gson.Gson
import com.syedali.flashquiz.BuildConfig
import com.syedali.flashquiz.api.JsonResponseParser
import com.syedali.flashquiz.di.IoDispatcher
import com.syedali.flashquiz.model.Flashcard
import com.syedali.flashquiz.model.MCQ
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiRepository @Inject constructor(
    private val openAiService: OpenAiService,
    private val backendService: BackendService,
    private val gson: Gson,
    @IoDispatcher private val io: CoroutineDispatcher
) {
    companion object {
        private const val API_KEY = ""
        private const val MODEL = "openai-fast"

        /** Whole-book runs on the backend can take minutes; give them room. */
        private const val BACKEND_GENERATE_TIMEOUT_MS = 300_000L

        /** Guard so a book-length import cannot blow up the JSON payload. */
        internal const val MAX_SOURCE_CHARS = 1_000_000
    }

    suspend fun generateFlashcards(
        sourceText: String,
        numCards: Int,
        tags: List<String>
    ): Result<List<Flashcard>> = withContext(io) {
        runCatching {
            val source = sourceText.take(MAX_SOURCE_CHARS)
            backendFlashcards(source, numCards, tags)
                ?: generateLocalFlashcards(source, numCards, tags)
        }
    }

    suspend fun generateMCQs(
        sourceText: String,
        numMcqs: Int,
        tags: List<String>
    ): Result<List<MCQ>> = withContext(io) {
        runCatching {
            val source = sourceText.take(MAX_SOURCE_CHARS)
            backendMcqs(source, numMcqs, tags)
                ?: generateLocalMcqs(source, numMcqs, tags)
        }
    }

    // ------------------------------------------------------------- backend first

    private suspend fun backendFlashcards(
        sourceText: String,
        numCards: Int,
        tags: List<String>
    ): List<Flashcard>? {
        if (numCards <= 0) return null
        val response = backendGenerate(
            BackendGenerateRequest(
                sourceText = sourceText,
                flashcards = true,
                mcqs = false,
                numCards = numCards,
                numMcqs = 1,  // schema requires >= 1 even when mcqs is disabled
                tags = tags
            )
        ) ?: return null
        return response.flashcards
            .orEmpty()
            .mapNotNull { it.toFlashcardOrNull(tags) }
            .distinctBy { cardKey(it) }
            .take(numCards)
            .ifEmpty { null }
    }

    private suspend fun backendMcqs(
        sourceText: String,
        numMcqs: Int,
        tags: List<String>
    ): List<MCQ>? {
        if (numMcqs <= 0) return null
        val response = backendGenerate(
            BackendGenerateRequest(
                sourceText = sourceText,
                flashcards = false,
                mcqs = true,
                numCards = 1,  // schema requires >= 1 even when flashcards is disabled
                numMcqs = numMcqs,
                tags = tags
            )
        ) ?: return null
        return response.mcqs
            .orEmpty()
            .mapNotNull { it.toMcqOrNull(tags) }
            .distinctBy { mcqKey(it) }
            .take(numMcqs)
            .ifEmpty { null }
    }

    private suspend fun backendGenerate(
        request: BackendGenerateRequest
    ): BackendGenerateResponse? {
        if (BuildConfig.BACKEND_URL.isBlank()) return null
        val response = try {
            withTimeoutOrNull(BACKEND_GENERATE_TIMEOUT_MS) {
                backendService.generate(request)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } ?: return null
        val hasCards = !response.flashcards.isNullOrEmpty()
        val hasMcqs = !response.mcqs.isNullOrEmpty()
        return if (hasCards || hasMcqs) response else null
    }

    // ---------------------------------------------------------- on-device fallback

    private suspend fun generateLocalFlashcards(
        sourceText: String,
        numCards: Int,
        tags: List<String>
    ): List<Flashcard> {
        if (numCards <= 0) return emptyList()
        val chunks = splitSourceChunks(sourceText)
        val out = mutableListOf<Flashcard>()

        for ((index, chunk) in chunks.withIndex()) {
            var attempts = 0
            while (out.size < numCards && attempts < MAX_ATTEMPTS_PER_CHUNK) {
                attempts++
                val budget = chunkBudget(numCards - out.size, chunks.size - index)
                if (budget <= 0) break
                val content = chat(
                    system = FLASHCARD_SYSTEM_PROMPT,
                    user = buildUserPrompt(chunk, budget, tags, out)
                )
                val batch = distinctNew(
                    JsonResponseParser.parseFlashcards(content, tags),
                    out,
                    ::cardKey
                ).take(budget)
                if (batch.isEmpty()) break  // model produced nothing new for this chunk
                out += batch
            }
            if (out.size >= numCards) break
        }
        return out
    }

    private suspend fun generateLocalMcqs(
        sourceText: String,
        numMcqs: Int,
        tags: List<String>
    ): List<MCQ> {
        if (numMcqs <= 0) return emptyList()
        val chunks = splitSourceChunks(sourceText)
        val out = mutableListOf<MCQ>()

        for ((index, chunk) in chunks.withIndex()) {
            var attempts = 0
            while (out.size < numMcqs && attempts < MAX_ATTEMPTS_PER_CHUNK) {
                attempts++
                val budget = chunkBudget(numMcqs - out.size, chunks.size - index)
                if (budget <= 0) break
                val content = chat(
                    system = MCQ_SYSTEM_PROMPT,
                    user = buildMcqUserPrompt(chunk, budget, tags, out)
                )
                val batch = distinctNew(
                    JsonResponseParser.parseMCQs(content, tags),
                    out,
                    ::mcqKey
                ).take(budget)
                if (batch.isEmpty()) break
                out += batch
            }
            if (out.size >= numMcqs) break
        }
        return out
    }

    // ------------------------------------------------------------- transport

    private suspend fun chat(system: String, user: String): String {
        var lastError: Exception? = null
        repeat(4) { attempt ->
            try {
                val response = openAiService.chatCompletions(
                    authorization = "Bearer $API_KEY",
                    body = ChatRequest(
                        model = MODEL,
                        messages = listOf(
                            Message("system", system),
                            Message("user", user)
                        )
                    )
                )
                val content = response.choices?.firstOrNull()?.message?.content
                    ?: throw IOException("Empty API response")
                return content
            } catch (e: retrofit2.HttpException) {
                lastError = e
                if (e.code() == 429 && attempt < 3) {
                    kotlinx.coroutines.delay(5000L * (attempt + 1))
                } else {
                    throw IOException("API error ${e.code()}: ${e.message()}")
                }
            } catch (e: IOException) {
                lastError = e
                if (attempt < 3) {
                    kotlinx.coroutines.delay(2000L * (attempt + 1))
                } else {
                    throw e
                }
            }
        }
        throw lastError ?: IOException("API request failed")
    }

    private fun buildUserPrompt(
        sourceText: String,
        count: Int,
        tags: List<String>,
        existing: List<Flashcard> = emptyList()
    ): String = """Source material (between START and END):
START
$sourceText
END

Generate $count flashcards following the schema.
Tags to include: ${tags.joinToString(", ")}${alreadyGenerated(existing.map { it.text.ifBlank { it.front } })}
Return a JSON array of distinct new cards."""

    private fun buildMcqUserPrompt(
        sourceText: String,
        count: Int,
        tags: List<String>,
        existing: List<MCQ> = emptyList()
    ): String = """Source material (between START and END):
START
$sourceText
END

Generate $count multiple-choice questions following the schema.
Tags to include: ${tags.joinToString(", ")}${alreadyGenerated(existing.map { it.question })}
Return a JSON array of distinct new questions."""

    private fun alreadyGenerated(items: List<String>): String {
        val recent = items.takeLast(15).filter { it.isNotBlank() }
        if (recent.isEmpty()) return ""
        return "\nAlready generated (do NOT repeat these): " +
            recent.joinToString("; ") { it.take(60) } + "."
    }

    private val FLASHCARD_SYSTEM_PROMPT = """You are an expert Anki flashcard creator following SuperMemo's Minimum Information Principle.

Rules:
1. ATOMICITY: Each card must test exactly ONE factual connection.
2. QUESTION FORMAT: "Topic: Specific prompt"
3. CLOZE DELETION: Use Cloze format {{c1::hidden text}} when testing key terminology.
4. Answers must be concise (under 15 words).
5. DIVERSITY: Cover different topics from the material.
6. Include a short `source` field on each card.
7. Output JSON only, matching this exact schema:
[
  {"type": "Basic", "front": "Topic: Focus area", "back": "Concise answer", "tags": ["tag1"], "source": "Section 1"},
  {"type": "Cloze", "text": "The {{c1::mitochondria}} produces {{c2::ATP}}.", "tags": ["tag1"], "source": "Section 2"}
]"""

    private val MCQ_SYSTEM_PROMPT = """You are an expert exam question writer. Generate multiple-choice questions from the provided study material.

Rules:
1. Each question must test one important concept from the material.
2. Provide 4 options. Exactly one is correct.
3. DISTRACTORS MUST BE PLAUSIBLE.
4. Include a short `explanation` of the correct answer and a `distractor_explanations` map.
5. Output JSON only, matching this exact schema:
[
  {
    "question": "...",
    "options": ["...", "...", "...", "..."],
    "correct_index": 0,
    "explanation": "...",
    "distractor_explanations": {"1": "...", "2": "...", "3": "..."},
    "tags": ["tag1"]
  }
]"""
}
