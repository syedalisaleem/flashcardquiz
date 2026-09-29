package com.syedali.flashquiz.network

import okhttp3.MultipartBody
import retrofit2.http.Body
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
}
