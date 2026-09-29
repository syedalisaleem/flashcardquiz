package com.syedali.flashquiz.di

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.syedali.flashquiz.BuildConfig
import com.syedali.flashquiz.network.BackendService
import com.syedali.flashquiz.network.OcrService
import com.syedali.flashquiz.network.OpenAiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    const val OPENAI_BASE_URL = "https://text.pollinations.ai/"
    const val OCR_BASE_URL = "https://api.ocr.space/"

    /** Placeholder keeps Retrofit happy when the backend is not configured. */
    const val BACKEND_DISABLED_URL = "https://backend-disabled.invalid/"

    /** Whole-book generation is a long request; the shared 60s client would cut it off. */
    private const val BACKEND_READ_TIMEOUT_SECONDS = 300L

    @Provides
    @Singleton
    fun provideGson(): Gson = GsonBuilder().create()

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

    @Provides
    @Singleton
    @BackendHttp
    fun provideBackendOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(BACKEND_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()

    @Provides
    @Singleton
    @OpenAi
    fun provideOpenAiRetrofit(client: OkHttpClient, gson: Gson): Retrofit =
        Retrofit.Builder()
            .baseUrl(OPENAI_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()

    @Provides
    @Singleton
    @Ocr
    fun provideOcrRetrofit(client: OkHttpClient, gson: Gson): Retrofit =
        Retrofit.Builder()
            .baseUrl(OCR_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()

    @Provides
    @Singleton
    @Backend
    fun provideBackendRetrofit(@BackendHttp client: OkHttpClient, gson: Gson): Retrofit =
        Retrofit.Builder()
            .baseUrl(backendBaseUrl())
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()

    @Provides
    @Singleton
    fun provideOpenAiService(@OpenAi retrofit: Retrofit): OpenAiService =
        retrofit.create(OpenAiService::class.java)

    @Provides
    @Singleton
    fun provideOcrService(@Ocr retrofit: Retrofit): OcrService =
        retrofit.create(OcrService::class.java)

    @Provides
    @Singleton
    fun provideBackendService(@Backend retrofit: Retrofit): BackendService =
        retrofit.create(BackendService::class.java)

    /** BuildConfig.BACKEND_URL is an origin (scheme://host[:port]); it may not
     *  end in a path. Blank means "backend disabled — stay on-device". */
    private fun backendBaseUrl(): String {
        val raw = BuildConfig.BACKEND_URL.trim()
        if (raw.isEmpty()) return BACKEND_DISABLED_URL
        return if (raw.endsWith("/")) raw else "$raw/"
    }
}
