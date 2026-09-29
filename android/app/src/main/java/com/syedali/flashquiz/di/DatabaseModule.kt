package com.syedali.flashquiz.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.syedali.flashquiz.data.local.FlashcardQuizDatabase
import com.syedali.flashquiz.data.local.dao.DeckDao
import com.syedali.flashquiz.data.local.dao.FlashcardDao
import com.syedali.flashquiz.data.local.dao.McqDao
import com.syedali.flashquiz.data.prefs.UserPreferencesDataSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.userPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "user_prefs"
)

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): FlashcardQuizDatabase =
        Room.databaseBuilder(
            context,
            FlashcardQuizDatabase::class.java,
            FlashcardQuizDatabase.NAME
        ).build()

    @Provides
    fun provideDeckDao(db: FlashcardQuizDatabase): DeckDao = db.deckDao()

    @Provides
    fun provideFlashcardDao(db: FlashcardQuizDatabase): FlashcardDao = db.flashcardDao()

    @Provides
    fun provideMcqDao(db: FlashcardQuizDatabase): McqDao = db.mcqDao()

    @Provides
    @Singleton
    fun provideUserPreferences(
        @ApplicationContext context: Context
    ): UserPreferencesDataSource =
        UserPreferencesDataSource(context.userPreferencesDataStore)
}
