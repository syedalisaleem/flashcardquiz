package com.syedali.flashquiz.network

import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

data class ChatRequest(
    val model: String,
    val messages: List<Message>,
    val temperature: Double = 0.4
)

data class Message(
    val role: String,
    val content: String
)

data class ChatResponse(
    val choices: List<Choice>? = null
)

data class Choice(
    val message: Message? = null
)

interface OpenAiService {
    @POST("openai/chat/completions")
    suspend fun chatCompletions(
        @Header("Authorization") authorization: String,
        @Body body: ChatRequest
    ): ChatResponse
}

data class OcrResponse(
    val IsErroredOnProcessing: Boolean? = null,
    val ErrorMessage: List<String>? = null,
    val ParsedResults: List<ParsedResult>? = null
)

data class ParsedResult(
    val ParsedText: String? = null
)

interface OcrService {
    @Multipart
    @POST("parse/image")
    suspend fun parseImage(
        @Header("apikey") apiKey: String,
        @Part("base64Image") base64Image: RequestBody,
        @Part("language") language: RequestBody,
        @Part("isOverlayRequired") isOverlayRequired: RequestBody,
        @Part("OCREngine") ocrEngine: RequestBody,
        @Part("scale") scale: RequestBody,
        @Part("isTable") isTable: RequestBody
    ): OcrResponse
}
