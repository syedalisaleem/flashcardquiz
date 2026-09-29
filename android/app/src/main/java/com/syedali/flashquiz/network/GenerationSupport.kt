package com.syedali.flashquiz.network

import com.syedali.flashquiz.model.Flashcard
import com.syedali.flashquiz.model.MCQ

/** Chunk size for on-device generation. Mirrors the backend's 35k prompt budget
 * minus the system prompt + already-generated list. */
internal const val SOURCE_CHUNK_CHARS = 30_000

/** Model refills are capped per chunk so a hallucinating model cannot loop. */
internal const val MAX_ATTEMPTS_PER_CHUNK = 6

internal fun splitSourceChunks(text: String, size: Int = SOURCE_CHUNK_CHARS): List<String> {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return listOf("")
    if (size <= 0) return listOf(trimmed)
    return trimmed.chunked(size)
}

/** Share of the remaining budget this chunk owns (rounds up, never 0 when work remains). */
internal fun chunkBudget(remaining: Int, chunksLeft: Int): Int {
    if (remaining <= 0 || chunksLeft <= 0) return 0
    return (remaining + chunksLeft - 1) / chunksLeft
}

internal fun cardKey(c: Flashcard): String =
    c.text.ifBlank { c.front }.trim().lowercase()

internal fun mcqKey(m: MCQ): String = m.question.trim().lowercase()

/** Keeps only items whose key has not been seen yet (previous runs included). */
internal fun <T> distinctNew(items: List<T>, existing: List<T>, key: (T) -> String): List<T> {
    val seen = existing.map(key).filter { it.isNotBlank() }.toMutableSet()
    return items.filter { item ->
        val k = key(item)
        k.isNotBlank() && seen.add(k)
    }
}
