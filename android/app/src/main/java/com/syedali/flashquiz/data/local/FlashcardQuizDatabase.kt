package com.syedali.flashquiz.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.syedali.flashquiz.data.local.converter.Converters
import com.syedali.flashquiz.data.local.dao.DeckDao
import com.syedali.flashquiz.data.local.dao.FlashcardDao
import com.syedali.flashquiz.data.local.dao.McqDao
import com.syedali.flashquiz.data.local.entity.DeckEntity
import com.syedali.flashquiz.data.local.entity.FlashcardEntity
import com.syedali.flashquiz.data.local.entity.McqEntity

@Database(
    entities = [DeckEntity::class, FlashcardEntity::class, McqEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class FlashcardQuizDatabase : RoomDatabase() {
    abstract fun deckDao(): DeckDao
    abstract fun flashcardDao(): FlashcardDao
    abstract fun mcqDao(): McqDao

    companion object {
        const val NAME = "flashcardquiz.db"

        @Volatile
        private var instance: FlashcardQuizDatabase? = null

        fun getInstance(context: Context): FlashcardQuizDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context).also { instance = it }
            }

        private fun build(context: Context): FlashcardQuizDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                FlashcardQuizDatabase::class.java,
                NAME
            ).build()
    }
}
