package com.syedali.flashquiz.di

import com.syedali.flashquiz.network.CoroutineDispatchers
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DispatcherModule {

    @Provides
    @Singleton
    fun provideDispatchers(): CoroutineDispatchers = CoroutineDispatchers(
        io = Dispatchers.IO,
        default = Dispatchers.Default,
        main = Dispatchers.Main
    )

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(dispatchers: CoroutineDispatchers): CoroutineDispatcher =
        dispatchers.io

    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(dispatchers: CoroutineDispatchers): CoroutineDispatcher =
        dispatchers.default
}
