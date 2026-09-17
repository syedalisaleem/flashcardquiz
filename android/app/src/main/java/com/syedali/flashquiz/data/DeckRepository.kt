package com.syedali.flashquiz.data

import android.content.ContentValues
import android.database.Cursor
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.syedali.flashquiz.model.Deck
import com.syedali.flashquiz.model.DeckStats

class DeckRepository(private val dbHelper: DatabaseHelper) {
    private val gson = Gson()

    fun create(name: String, tags: List<String> = emptyList()): Long {
        val cv = ContentValues().apply {
            put("name", name)
            put("tags", gson.toJson(tags))
        }
        return dbHelper.writableDatabase.insert("decks", null, cv)
    }

    fun getAll(): List<Deck> {
        val decks = mutableListOf<Deck>()
        val cursor = dbHelper.readableDatabase.rawQuery("SELECT * FROM decks ORDER BY created_at DESC", null)
        cursor.use {
            while (it.moveToNext()) {
                decks.add(cursorToDeck(it))
            }
        }
        return decks
    }

    fun delete(id: Long) {
        dbHelper.writableDatabase.delete("decks", "id=?", arrayOf(id.toString()))
        dbHelper.writableDatabase.delete("flashcards", "deck_id=?", arrayOf(id.toString()))
        dbHelper.writableDatabase.delete("mcqs", "deck_id=?", arrayOf(id.toString()))
    }

    fun getStats(deckId: Long): DeckStats {
        val now = System.currentTimeMillis()
        val totalCards = countQuery("SELECT COUNT(*) FROM flashcards WHERE deck_id=?", deckId)
        val totalMcqs = countQuery("SELECT COUNT(*) FROM mcqs WHERE deck_id=?", deckId)
        val dueCards = countQuery("SELECT COUNT(*) FROM flashcards WHERE deck_id=? AND next_review<=?", deckId, now)
        val dueMcqs = countQuery("SELECT COUNT(*) FROM mcqs WHERE deck_id=? AND next_review<=?", deckId, now)
        val mastered = countQuery("SELECT COUNT(*) FROM flashcards WHERE deck_id=? AND interval>=21", deckId)
        return DeckStats(totalCards, totalMcqs, dueCards, dueMcqs, mastered)
    }

    private fun countQuery(query: String, vararg args: Long): Int {
        val cursor = dbHelper.readableDatabase.rawQuery(query, args.map { it.toString() }.toTypedArray())
        cursor.use {
            return if (it.moveToFirst()) it.getInt(0) else 0
        }
    }

    private fun cursorToDeck(cursor: Cursor): Deck {
        return Deck(
            id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
            name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
            tags = gson.fromJson(cursor.getString(cursor.getColumnIndexOrThrow("tags")), object : TypeToken<List<String>>() {}.type),
            createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at"))
        )
    }
}
