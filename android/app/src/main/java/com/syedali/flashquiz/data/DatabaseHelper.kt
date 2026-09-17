package com.syedali.flashquiz.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.syedali.flashquiz.model.Deck
import com.syedali.flashquiz.model.DeckStats
import com.syedali.flashquiz.model.Flashcard
import com.syedali.flashquiz.model.MCQ

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, "flashcardquiz.db", null, 1) {
    private val gson = Gson()

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE decks (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                tags TEXT DEFAULT '[]',
                created_at INTEGER DEFAULT (strftime('%s','now') * 1000)
            )
        """)
        db.execSQL("""
            CREATE TABLE flashcards (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                deck_id INTEGER NOT NULL,
                type TEXT DEFAULT 'Basic',
                front TEXT DEFAULT '',
                back TEXT DEFAULT '',
                text TEXT DEFAULT '',
                tags TEXT DEFAULT '[]',
                source TEXT DEFAULT '',
                ease_factor REAL DEFAULT 2.5,
                interval INTEGER DEFAULT 0,
                repetitions INTEGER DEFAULT 0,
                next_review INTEGER DEFAULT 0,
                last_review INTEGER DEFAULT 0,
                FOREIGN KEY (deck_id) REFERENCES decks(id) ON DELETE CASCADE
            )
        """)
        db.execSQL("""
            CREATE TABLE mcqs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                deck_id INTEGER NOT NULL,
                question TEXT NOT NULL,
                options TEXT NOT NULL,
                correct_index INTEGER NOT NULL,
                explanation TEXT DEFAULT '',
                distractor_explanations TEXT DEFAULT '{}',
                tags TEXT DEFAULT '[]',
                ease_factor REAL DEFAULT 2.5,
                interval INTEGER DEFAULT 0,
                repetitions INTEGER DEFAULT 0,
                next_review INTEGER DEFAULT 0,
                last_review INTEGER DEFAULT 0,
                FOREIGN KEY (deck_id) REFERENCES decks(id) ON DELETE CASCADE
            )
        """)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS mcqs")
        db.execSQL("DROP TABLE IF EXISTS flashcards")
        db.execSQL("DROP TABLE IF EXISTS decks")
        onCreate(db)
    }

    fun createDeck(name: String, tags: List<String> = emptyList()): Long {
        val cv = ContentValues().apply {
            put("name", name)
            put("tags", gson.toJson(tags))
        }
        return writableDatabase.insert("decks", null, cv)
    }

    fun getDecks(): List<Deck> {
        val decks = mutableListOf<Deck>()
        val cursor = readableDatabase.rawQuery("SELECT * FROM decks ORDER BY created_at DESC", null)
        while (cursor.moveToNext()) {
            decks.add(Deck(
                id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
                tags = gson.fromJson(cursor.getString(cursor.getColumnIndexOrThrow("tags")), object : TypeToken<List<String>>() {}.type),
                createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at"))
            ))
        }
        cursor.close()
        return decks
    }

    fun deleteDeck(id: Long) {
        writableDatabase.delete("decks", "id=?", arrayOf(id.toString()))
        writableDatabase.delete("flashcards", "deck_id=?", arrayOf(id.toString()))
        writableDatabase.delete("mcqs", "deck_id=?", arrayOf(id.toString()))
    }

    fun getDeckStats(deckId: Long): DeckStats {
        val now = System.currentTimeMillis()
        val cards = readableDatabase.rawQuery("SELECT COUNT(*) FROM flashcards WHERE deck_id=?", arrayOf(deckId.toString()))
        cards.moveToFirst()
        val totalCards = cards.getInt(0)
        cards.close()

        val mcqs = readableDatabase.rawQuery("SELECT COUNT(*) FROM mcqs WHERE deck_id=?", arrayOf(deckId.toString()))
        mcqs.moveToFirst()
        val totalMcqs = mcqs.getInt(0)
        mcqs.close()

        val dueCards = readableDatabase.rawQuery("SELECT COUNT(*) FROM flashcards WHERE deck_id=? AND next_review<=?", arrayOf(deckId.toString(), now.toString()))
        dueCards.moveToFirst()
        val due = dueCards.getInt(0)
        dueCards.close()

        val dueMcqs = readableDatabase.rawQuery("SELECT COUNT(*) FROM mcqs WHERE deck_id=? AND next_review<=?", arrayOf(deckId.toString(), now.toString()))
        dueMcqs.moveToFirst()
        val dueM = dueMcqs.getInt(0)
        dueMcqs.close()

        val mastered = readableDatabase.rawQuery("SELECT COUNT(*) FROM flashcards WHERE deck_id=? AND interval>=21", arrayOf(deckId.toString()))
        mastered.moveToFirst()
        val m = mastered.getInt(0)
        mastered.close()

        return DeckStats(totalCards, totalMcqs, due, dueM, m)
    }

    fun addFlashcards(deckId: Long, cards: List<Flashcard>) {
        writableDatabase.beginTransaction()
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
                writableDatabase.insert("flashcards", null, cv)
            }
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    fun getFlashcards(deckId: Long): List<Flashcard> {
        val cards = mutableListOf<Flashcard>()
        val cursor = readableDatabase.rawQuery("SELECT * FROM flashcards WHERE deck_id=?", arrayOf(deckId.toString()))
        while (cursor.moveToNext()) {
            cards.add(Flashcard(
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
            ))
        }
        cursor.close()
        return cards
    }

    fun getDueFlashcards(deckId: Long): List<Flashcard> {
        val now = System.currentTimeMillis()
        val cards = mutableListOf<Flashcard>()
        val cursor = readableDatabase.rawQuery(
            "SELECT * FROM flashcards WHERE deck_id=? AND next_review<=? ORDER BY next_review",
            arrayOf(deckId.toString(), now.toString())
        )
        while (cursor.moveToNext()) {
            cards.add(Flashcard(
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
            ))
        }
        cursor.close()
        return cards
    }

    fun updateFlashcardSM2(id: Long, quality: Int) {
        val card = readableDatabase.rawQuery("SELECT * FROM flashcards WHERE id=?", arrayOf(id.toString()))
        if (card.moveToFirst()) {
            var ef = card.getDouble(card.getColumnIndexOrThrow("ease_factor"))
            var interval = card.getInt(card.getColumnIndexOrThrow("interval"))
            var rep = card.getInt(card.getColumnIndexOrThrow("repetitions"))

            if (quality >= 3) {
                if (rep == 0) interval = 1
                else if (rep == 1) interval = 6
                else interval = (interval * ef).toInt()
                rep++
            } else {
                rep = 0
                interval = 1
            }

            ef = ef + (0.1 - (5 - quality) * (0.08 + (5 - quality) * 0.02))
            if (ef < 1.3) ef = 1.3

            val now = System.currentTimeMillis()
            val nextReview = now + interval * 24L * 60 * 60 * 1000

            val cv = ContentValues().apply {
                put("ease_factor", ef)
                put("interval", interval)
                put("repetitions", rep)
                put("next_review", nextReview)
                put("last_review", now)
            }
            writableDatabase.update("flashcards", cv, "id=?", arrayOf(id.toString()))
        }
        card.close()
    }

    fun addMCQs(deckId: Long, mcqs: List<MCQ>) {
        writableDatabase.beginTransaction()
        try {
            for (mcq in mcqs) {
                val cv = ContentValues().apply {
                    put("deck_id", deckId)
                    put("question", mcq.question)
                    put("options", gson.toJson(mcq.options))
                    put("correct_index", mcq.correctIndex)
                    put("explanation", mcq.explanation)
                    put("distractor_explanations", gson.toJson(mcq.distractorExplanations))
                    put("tags", gson.toJson(mcq.tags))
                }
                writableDatabase.insert("mcqs", null, cv)
            }
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    fun getDueMCQs(deckId: Long): List<MCQ> {
        val now = System.currentTimeMillis()
        val mcqs = mutableListOf<MCQ>()
        val cursor = readableDatabase.rawQuery(
            "SELECT * FROM mcqs WHERE deck_id=? AND next_review<=? ORDER BY next_review",
            arrayOf(deckId.toString(), now.toString())
        )
        while (cursor.moveToNext()) {
            mcqs.add(MCQ(
                id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                deckId = cursor.getLong(cursor.getColumnIndexOrThrow("deck_id")),
                question = cursor.getString(cursor.getColumnIndexOrThrow("question")),
                options = gson.fromJson(cursor.getString(cursor.getColumnIndexOrThrow("options")), object : TypeToken<List<String>>() {}.type),
                correctIndex = cursor.getInt(cursor.getColumnIndexOrThrow("correct_index")),
                explanation = cursor.getString(cursor.getColumnIndexOrThrow("explanation")),
                distractorExplanations = gson.fromJson(cursor.getString(cursor.getColumnIndexOrThrow("distractor_explanations")), object : TypeToken<Map<String, String>>() {}.type),
                tags = gson.fromJson(cursor.getString(cursor.getColumnIndexOrThrow("tags")), object : TypeToken<List<String>>() {}.type),
                easeFactor = cursor.getDouble(cursor.getColumnIndexOrThrow("ease_factor")),
                interval = cursor.getInt(cursor.getColumnIndexOrThrow("interval")),
                repetitions = cursor.getInt(cursor.getColumnIndexOrThrow("repetitions")),
                nextReview = cursor.getLong(cursor.getColumnIndexOrThrow("next_review")),
                lastReview = cursor.getLong(cursor.getColumnIndexOrThrow("last_review"))
            ))
        }
        cursor.close()
        return mcqs
    }

    fun updateMCQSM2(id: Long, quality: Int) {
        val mcq = readableDatabase.rawQuery("SELECT * FROM mcqs WHERE id=?", arrayOf(id.toString()))
        if (mcq.moveToFirst()) {
            var ef = mcq.getDouble(mcq.getColumnIndexOrThrow("ease_factor"))
            var interval = mcq.getInt(mcq.getColumnIndexOrThrow("interval"))
            var rep = mcq.getInt(mcq.getColumnIndexOrThrow("repetitions"))

            if (quality >= 3) {
                if (rep == 0) interval = 1
                else if (rep == 1) interval = 6
                else interval = (interval * ef).toInt()
                rep++
            } else {
                rep = 0
                interval = 1
            }

            ef = ef + (0.1 - (5 - quality) * (0.08 + (5 - quality) * 0.02))
            if (ef < 1.3) ef = 1.3

            val now = System.currentTimeMillis()
            val nextReview = now + interval * 24L * 60 * 60 * 1000

            val cv = ContentValues().apply {
                put("ease_factor", ef)
                put("interval", interval)
                put("repetitions", rep)
                put("next_review", nextReview)
                put("last_review", now)
            }
            writableDatabase.update("mcqs", cv, "id=?", arrayOf(id.toString()))
        }
        mcq.close()
    }
}
