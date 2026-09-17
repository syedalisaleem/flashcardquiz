package com.syedali.flashquiz.data

import android.content.ContentValues
import android.database.Cursor
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.syedali.flashquiz.engine.SpacedRepetitionEngine
import com.syedali.flashquiz.model.MCQ

class MCQRepository(private val dbHelper: DatabaseHelper) {
    private val gson = Gson()

    fun add(deckId: Long, mcqs: List<MCQ>) {
        dbHelper.writableDatabase.beginTransaction()
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
                dbHelper.writableDatabase.insert("mcqs", null, cv)
            }
            dbHelper.writableDatabase.setTransactionSuccessful()
        } finally {
            dbHelper.writableDatabase.endTransaction()
        }
    }

    fun getDue(deckId: Long): List<MCQ> {
        val now = System.currentTimeMillis()
        val mcqs = mutableListOf<MCQ>()
        val cursor = dbHelper.readableDatabase.rawQuery(
            "SELECT * FROM mcqs WHERE deck_id=? AND next_review<=? ORDER BY next_review",
            arrayOf(deckId.toString(), now.toString())
        )
        cursor.use {
            while (it.moveToNext()) {
                mcqs.add(cursorToMCQ(it))
            }
        }
        return mcqs
    }

    fun updateSM2(id: Long, quality: Int) {
        val cursor = dbHelper.readableDatabase.rawQuery("SELECT * FROM mcqs WHERE id=?", arrayOf(id.toString()))
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
                dbHelper.writableDatabase.update("mcqs", cv, "id=?", arrayOf(id.toString()))
            }
        }
    }

    private fun cursorToMCQ(cursor: Cursor): MCQ {
        return MCQ(
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
        )
    }
}
