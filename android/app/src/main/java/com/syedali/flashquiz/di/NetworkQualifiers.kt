package com.syedali.flashquiz.di

import javax.inject.Qualifier

@Retention(AnnotationRetention.BINARY)
@Qualifier
annotation class OpenAi

@Retention(AnnotationRetention.BINARY)
@Qualifier
annotation class Ocr

@Retention(AnnotationRetention.BINARY)
@Qualifier
annotation class Backend

/** Backend client with a long read timeout (distinct from the shared client). */
@Retention(AnnotationRetention.BINARY)
@Qualifier
annotation class BackendHttp
