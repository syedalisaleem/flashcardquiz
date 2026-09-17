package com.syedali.flashquiz.data

import android.content.ContentValues
import android.database.Cursor
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.syedali.flashquiz.engine.SpacedRepetitionEngine
import com.syedali.flashquiz.model.Flashcard

class FlashcardRepository(private val dbHelper: DatabaseHelper) {
    private val gson = Gson()

    fun add(deckId: Long, cards: List<Flashcard>) {
        dbHelper.writableDatabase.beginTransaction()
        try {
            for (card in cards) {
                val cv = ContentValues().apply {
                    put("deck_id", deckId)
                    put("type", card.type)
                    put("front", card.front)
                    put("back", card.back)
                    put("text", card.text)
                    put("tags", gson.toJson(card.tags))
                    put("source", card.source)
                }
                dbHelper.writableDatabase.insert("flashcards", null, cv)
            }
            dbHelper.writableDatabase.setTransactionSuccessful()
        } finally {
            dbHelper.writableDatabase.endTransaction()
        }
    }

    fun getAll(deckId: Long): List<Flashcard> {
        return query("SELECT * FROM flashcards WHERE deck_id=?", deckId)
    }

    fun getDue(deckId: Long): List<Flashcard> {
        val now = System.currentTimeMillis()
        val cards = mutableListOf<Flashcard>()
        val cursor = dbHelper.readableDatabase.rawQuery(
            "SELECT * FROM flashcards WHERE deck_id=? AND next_review<=? ORDER BY next_review",
            arrayOf(deckId.toString(), now.toString())
        )
        cursor.use {
            while (it.moveToNext()) {
                cards.add(cursorToFlashcard(it))
            }
        }
        return cards
    }

    fun updateSM2(id: Long, quality: Int) {
        val cursor = dbHelper.readableDatabase.rawQuery("SELECT * FROM flashcards WHERE id=?", arrayOf(id.toString()))
        cursor.use {
            if (it.moveToFirst()) {
                val result = SpacedRepetitionEngine.calculate(
                    currentEaseFactor = it.getDouble(it.getColumnIndexOrThrow("ease_factor")),
                    currentInterval = it.getInt(it.getColumnIndexOrThrow("interval")),
                    currentRepetitions = it.getInt(it.getColumnIndexOrThrow("repetitions")),
                    quality = quality
                )
                val cv = ContentValues().apply {
                    put("ease_factor", result.easeFactor)
                    put("interval", result.interval)
                    put("repetitions", result.repetitions)
                    put("next_review", result.nextReview)
                    put("last_review", System.currentTimeMillis())
                }
                dbHelper.writableDatabase.update("flashcards", cv, "id=?", arrayOf(id.toString()))
            }
        }
    }

    private fun query(sql: String, deckId: Long): List<Flashcard> {
        val cards = mutableListOf<Flashcard>()
        val cursor = dbHelper.readableDatabase.rawQuery(sql, arrayOf(deckId.toString()))
        cursor.use {
            while (it.moveToNext()) {
                cards.add(cursorToFlashcard(it))
            }
        }
        return cards
    }

    private fun cursorToFlashcard(cursor: Cursor): Flashcard {
        return Flashcard(
            id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
            deckId = cursor.getLong(cursor.getColumnIndexOrThrow("deck_id")),
            type = cursor.getString(cursor.getColumnIndexOrThrow("type")),
            front = cursor.getString(cursor.getColumnIndexOrThrow("front")),
            back = cursor.getString(cursor.getColumnIndexOrThrow("back")),
            text = cursor.getString(cursor.getColumnIndexOrThrow("text")),
            tags = gson.fromJson(cursor.getString(cursor.getColumnIndexOrThrow("tags")), object : TypeToken<List<String>>() {}.type),
            source = cursor.getString(cursor.getColumnIndexOrThrow("source")),
            easeFactor = cursor.getDouble(cursor.getColumnIndexOrThrow("ease_factor")),
            interval = cursor.getInt(cursor.getColumnIndexOrThrow("interval")),
            repetitions = cursor.getInt(cursor.getColumnIndexOrThrow("repetitions")),
            nextReview = cursor.getLong(cursor.getColumnIndexOrThrow("next_review")),
            lastReview = cursor.getLong(cursor.getColumnIndexOrThrow("last_review"))
        )
    }
}
