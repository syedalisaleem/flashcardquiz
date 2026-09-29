package com.syedali.flashquiz.di

import com.syedali.flashquiz.data.repository.DeckRepository
import com.syedali.flashquiz.data.repository.FlashcardRepository
import com.syedali.flashquiz.data.repository.McqRepository
import com.syedali.flashquiz.data.repository.RoomDeckRepository
import com.syedali.flashquiz.data.repository.RoomFlashcardRepository
import com.syedali.flashquiz.data.repository.RoomMcqRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindDeckRepository(impl: RoomDeckRepository): DeckRepository

    @Binds
    @Singleton
    abstract fun bindFlashcardRepository(impl: RoomFlashcardRepository): FlashcardRepository

    @Binds
    @Singleton
    abstract fun bindMcqRepository(impl: RoomMcqRepository): McqRepository
}
