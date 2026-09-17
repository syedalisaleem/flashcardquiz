package com.syedali.flashquiz.api

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.syedali.flashquiz.model.Flashcard
import com.syedali.flashquiz.model.MCQ
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class ApiClient(context: Context) {
    private val gson = Gson()
    private val appContext = context.applicationContext

    private val client: OkHttpClient by lazy {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustAllCerts, SecureRandom())
        OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .build()
    }

    companion object {
        private const val API_KEY = ""
        private const val BASE_URL = "https://text.pollinations.ai/openai"
        private const val MODEL = "openai-fast"
        private const val OCR_KEY = "K84356707588957"
        private const val OCR_URL = "https://api.ocr.space/parse/image"
        private const val MAX_IMAGE_DIM = 1024
        private const val JPEG_QUALITY = 85
    }

    fun generateFlashcards(
        sourceText: String,
        numCards: Int,
        tags: List<String>,
        callback: (Result<List<Flashcard>>) -> Unit
    ) {
        val systemPrompt = """You are an expert Anki flashcard creator following SuperMemo's Minimum Information Principle.

Rules:
1. ATOMICITY: Each card must test exactly ONE factual connection.
2. QUESTION FORMAT: "Topic: Specific prompt"
3. CLOZE DELETION: Use Cloze format {{c1::hidden text}} when testing key terminology.
4. Answers must be concise (under 15 words).
5. DIVERSITY: Cover different topics from the material.
6. Include a short `source` field on each card.
7. Output JSON only, matching this exact schema:
[
  {"type": "Basic", "front": "Topic: Focus area", "back": "Concise answer", "tags": ["tag1"], "source": "Section 1"},
  {"type": "Cloze", "text": "The {{c1::mitochondria}} produces {{c2::ATP}}.", "tags": ["tag1"], "source": "Section 2"}
]"""

        val userPrompt = """Source material (between START and END):
START
$sourceText
END

Generate $numCards flashcards following the schema.
Tags to include: ${tags.joinToString(", ")}
Return a JSON array of distinct new cards."""

        chatCompletion(systemPrompt, userPrompt, callback = { result ->
            result.onSuccess { content ->
                try {
                    val cards = JsonResponseParser.parseFlashcards(content, tags)
                    callback(Result.success(cards))
                } catch (e: Exception) {
                    callback(Result.failure(e))
                }
            }
            result.onFailure { callback(Result.failure(it)) }
        })
    }

    fun generateMCQs(
        sourceText: String,
        numMcqs: Int,
        tags: List<String>,
        callback: (Result<List<MCQ>>) -> Unit
    ) {
        val systemPrompt = """You are an expert exam question writer. Generate multiple-choice questions from the provided study material.

Rules:
1. Each question must test one important concept from the material.
2. Provide 4 options. Exactly one is correct.
3. DISTRACTORS MUST BE PLAUSIBLE.
4. Include a short `explanation` of the correct answer and a `distractor_explanations` map.
5. Output JSON only, matching this exact schema:
[
  {
    "question": "...",
    "options": ["...", "...", "...", "..."],
    "correct_index": 0,
    "explanation": "...",
    "distractor_explanations": {"1": "...", "2": "...", "3": "..."},
    "tags": ["tag1"]
  }
]"""

        val userPrompt = """Source material (between START and END):
START
$sourceText
END

Generate $numMcqs multiple-choice questions following the schema.
Tags to include: ${tags.joinToString(", ")}
Return a JSON array of distinct new questions."""

        chatCompletion(systemPrompt, userPrompt, callback = { result ->
            result.onSuccess { content ->
                try {
                    val mcqs = JsonResponseParser.parseMCQs(content, tags)
                    callback(Result.success(mcqs))
                } catch (e: Exception) {
                    callback(Result.failure(e))
                }
            }
            result.onFailure { callback(Result.failure(it)) }
        })
    }

    private fun chatCompletion(system: String, user: String, callback: (Result<String>) -> Unit, attempt: Int = 0) {
        val body = gson.toJson(mapOf(
            "model" to MODEL,
            "messages" to listOf(
                mapOf("role" to "system", "content" to system),
                mapOf("role" to "user", "content" to user)
            ),
            "temperature" to 0.4
        ))

        val request = Request.Builder()
            .url("$BASE_URL/chat/completions")
            .addHeader("Authorization", "Bearer $API_KEY")
            .addHeader("Content-Type", "application/json")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(Result.failure(e))
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        val errorBody = it.body?.string() ?: ""

                        // Retry on 429 rate limit with delayed retry on background thread
                        if (it.code == 429 && attempt < 3) {
                            val delayMs = 5000L * (attempt + 1)
                            Thread {
                                Thread.sleep(delayMs)
                                chatCompletion(system, user, callback, attempt + 1)
                            }.start()
                            return
                        }

                        callback(Result.failure(IOException("API error ${it.code}: $errorBody")))
                        return
                    }
                    val json = it.body?.string() ?: ""
                    try {
                        val obj = JsonParser.parseString(json).asJsonObject
                        val content = obj.getAsJsonArray("choices")
                            .get(0).asJsonObject
                            .getAsJsonObject("message")
                            .get("content").asString
                        callback(Result.success(content))
                    } catch (e: Exception) {
                        // Try to extract content even if JSON is malformed
                        val fallback = extractContentFromMalformed(json)
                        if (fallback != null) {
                            callback(Result.success(fallback))
                        } else {
                            callback(Result.failure(Exception("Failed to parse AI response. Try again.")))
                        }
                    }
                }
            }
        })
    }

    private fun extractContentFromMalformed(json: String): String? {
        // Try to extract content field from potentially truncated JSON
        val contentPattern = "\"content\"\\s*:\\s*\"(.*?)\"".toRegex(RegexOption.DOT_MATCHES_ALL)
        val match = contentPattern.find(json)
        return match?.groupValues?.get(1)?.replace("\\n", "\n")?.replace("\\\"", "\"")
    }

    fun ocrImage(imageBytes: ByteArray, callback: (Result<String>) -> Unit) {
        ocrImageWithRetry(imageBytes, 0, callback)
    }

    private fun ocrImageWithRetry(imageBytes: ByteArray, attempt: Int, callback: (Result<String>) -> Unit) {
        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            ?: return callback(Result.failure(Exception("Invalid image")))

        val scaledBitmap = scaleBitmap(bitmap)
        val compressedBytes = compressBitmap(scaledBitmap)
        val base64Image = Base64.encodeToString(compressedBytes, Base64.NO_WRAP)

        val formBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("base64Image", "data:image/jpeg;base64,$base64Image")
            .addFormDataPart("apikey", OCR_KEY)
            .addFormDataPart("language", "eng")
            .addFormDataPart("isOverlayRequired", "false")
            .addFormDataPart("OCREngine", "2")
            .addFormDataPart("scale", "true")
            .addFormDataPart("isTable", "true")
            .build()

        val request = Request.Builder()
            .url(OCR_URL)
            .addHeader("apikey", OCR_KEY)
            .post(formBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (attempt < 2) {
                    Thread.sleep(2000L * (attempt + 1))
                    ocrImageWithRetry(imageBytes, attempt + 1, callback)
                } else {
                    callback(Result.failure(e))
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val json = it.body?.string() ?: ""

                    if (!it.isSuccessful) {
                        if (it.code == 429 && attempt < 2) {
                            Thread.sleep(3000L * (attempt + 1))
                            ocrImageWithRetry(imageBytes, attempt + 1, callback)
                            return
                        }
                        callback(Result.failure(IOException("OCR error ${it.code}: $json")))
                        return
                    }

                    try {
                        val obj = JsonParser.parseString(json).asJsonObject

                        if (obj.has("IsErroredOnProcessing") && obj.get("IsErroredOnProcessing").asBoolean) {
                            val errorMsg = obj.get("ErrorMessage")?.asJsonArray?.get(0)?.asString ?: "OCR processing error"
                            if (attempt < 2) {
                                Thread.sleep(2000L * (attempt + 1))
                                ocrImageWithRetry(imageBytes, attempt + 1, callback)
                            } else {
                                callback(Result.failure(Exception(errorMsg)))
                            }
                            return
                        }

                        val parsedResults = obj.getAsJsonArray("ParsedResults")
                        if (parsedResults != null && parsedResults.size() > 0) {
                            val text = parsedResults[0].asJsonObject.get("ParsedText")?.asString ?: ""
                            if (text.isNotBlank()) {
                                callback(Result.success(text.trim()))
                            } else {
                                callback(Result.failure(Exception("No text detected in image")))
                            }
                        } else {
                            callback(Result.failure(Exception("No text found in image")))
                        }
                    } catch (e: Exception) {
                        callback(Result.failure(e))
                    }
                }
            }
        })
    }

    private fun scaleBitmap(bitmap: Bitmap): Bitmap {
        val maxDim = maxOf(bitmap.width, bitmap.height)
        return if (maxDim > MAX_IMAGE_DIM) {
            val scale = MAX_IMAGE_DIM.toFloat() / maxDim
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
        } else {
            bitmap
        }
    }

    private fun compressBitmap(bitmap: Bitmap): ByteArray {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)
        return outputStream.toByteArray()
    }
}
