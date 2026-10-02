package com.syedali.flashquiz.network

import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

/**
 * FlashcardQuiz backend (`app/main.py`).
 *
 * `BACKEND_URL` is injected through BuildConfig; when it is blank the
 * repositories skip these calls and keep working on-device.
 */
interface BackendService {

    @Multipart
    @POST("api/ocr")
    suspend fun ocr(@Part files: List<MultipartBody.Part>): BackendOcrResponse

    @POST("api/generate")
    suspend fun generate(@Body request: BackendGenerateRequest): BackendGenerateResponse

    @POST("api/study-request")
    suspend fun studyRequest(@Body request: BackendStudyRequest): BackendStudyResponse

    @GET("api/jobs/{jobId}")
    suspend fun job(@Path("jobId") jobId: String): BackendJobDto

    @GET("api/decks/{deckId}")
    suspend fun deck(@Path("deckId") deckId: Int): BackendDeckResponse
}

