package com.syedali.flashquiz.network

import com.google.common.truth.Truth.assertThat
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Pins the Android client's wire format to `app/main.py` so a backend rename
 * (or a Gson field-name slip) fails here instead of at runtime.
 */
class BackendServiceTest {

    private lateinit var server: MockWebServer
    private lateinit var service: BackendService

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        service = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(
                OkHttpClient.Builder()
                    .connectTimeout(5, TimeUnit.SECONDS)
                    .readTimeout(5, TimeUnit.SECONDS)
                    .build()
            )
            .addConverterFactory(GsonConverterFactory.create(Gson()))
            .build()
            .create(BackendService::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun generatePostsThePayloadFastApiExpects() {
        runBlocking {
            server.enqueue(
                MockResponse().setBody(
                    """
                    {
                      "flashcards": [
                        {"type": "Basic", "front": "Q", "back": "A", "text": "",
                       "tags": ["bio"], "source": "p1"}
                      ],
                      "mcqs": []
                    }
                    """.trimIndent()
                )
            )

            val response = service.generate(
                BackendGenerateRequest(
                    sourceText = "Mitochondria produce ATP.",
                    flashcards = true,
                    mcqs = false,
                    numCards = 3,
                    numMcqs = 1,
                    tags = listOf("bio")
                )
            )

            val recorded = server.takeRequest()
            assertThat(recorded.path).isEqualTo("/api/generate")
            assertThat(recorded.getHeader("Content-Type")).contains("application/json")

            val body = recorded.body.readUtf8()
            assertThat(body).contains("\"source_text\":\"Mitochondria produce ATP.\"")
            assertThat(body).contains("\"flashcards\":true")
            assertThat(body).contains("\"mcqs\":false")
            assertThat(body).contains("\"num_cards\":3")
            assertThat(body).contains("\"num_mcqs\":1")
            assertThat(body).contains("\"tags\":[\"bio\"]")
            // No Java-style camelCase must ever leak onto the wire.
            assertThat(body).doesNotContain("sourceText")
            assertThat(body).doesNotContain("numCards")

            val card = response.flashcards!!.single().toFlashcardOrNull(listOf("bio"))
            assertThat(card).isNotNull()
            assertThat(card!!.front).isEqualTo("Q")
            assertThat(card.tags).containsExactly("bio").inOrder()
        }
    }

    @Test
    fun generateMapsSnakeCaseMcqFields() {
        runBlocking {
            server.enqueue(
                MockResponse().setBody(
                    """
                    {
                      "flashcards": [],
                      "mcqs": [
                        {"question": "What produces ATP?",
                     "options": ["Mito", "Ribosome", "Golgi", "Nucleus"],
                     "correct_index": 0,
                     "explanation": "Mitochondria do.",
                     "distractor_explanations": {"1": "no"},
                     "tags": ["bio"]}
                      ]
                    }
                    """.trimIndent()
                )
            )

            val response = service.generate(
                BackendGenerateRequest(sourceText = "src", flashcards = false, mcqs = true)
            )

            val mcq = response.mcqs!!.single().toMcqOrNull(emptyList())!!
            assertThat(mcq.correctIndex).isEqualTo(0)
            assertThat(mcq.distractorExplanations["1"]).isEqualTo("no")
            assertThat(mcq.options).hasSize(4)
        }
    }

    @Test
    fun ocrUploadsAPartNamedFiles() {
        runBlocking {
            server.enqueue(MockResponse().setBody("""{"text":"HELLO WORLD","ocr":true,"images":1}"""))

            val part = MultipartBody.Part.createFormData(
                "files",
                "scan.jpg",
                byteArrayOf(1, 2, 3).toRequestBody("image/jpeg".toMediaType())
            )
            val response = service.ocr(listOf(part))

            val recorded = server.takeRequest()
            assertThat(recorded.path).isEqualTo("/api/ocr")
            assertThat(recorded.getHeader("Content-Type")).contains("multipart/form-data")
            assertThat(recorded.body.readUtf8()).contains("name=\"files\"")
            assertThat(response.text).isEqualTo("HELLO WORLD")
            assertThat(response.images).isEqualTo(1)
        }
    }
}
