package com.syedali.flashquiz.network

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.syedali.flashquiz.di.IoDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OcrRepository @Inject constructor(
    private val ocrService: OcrService,
    private val backendService: BackendService,
    @IoDispatcher private val io: CoroutineDispatcher
) {
    companion object {
        private const val OCR_KEY = "K84356707588957"
        private const val MAX_IMAGE_DIM = 1024
        private const val JPEG_QUALITY = 85
        private const val TEXT = "text/plain"
        private const val JPEG = "image/jpeg"
        private const val MAX_ATTEMPTS = 3

        /** Whole-image ceiling so one stuck request cannot hang an import. */
        private const val IMAGE_TIMEOUT_MS = 120_000L
        /** Per-attempt ceiling: a slow attempt must not eat the retries' budget. */
        private const val ATTEMPT_TIMEOUT_MS = 20_000L
        private const val BACKEND_TIMEOUT_MS = 60_000L
    }

    suspend fun recognizeText(imageBytes: ByteArray): Result<String> = withContext(io) {
        try {
            val text = withTimeoutOrNull(IMAGE_TIMEOUT_MS) {
                val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                    ?: throw IOException("Invalid image")
                val jpeg = compressBitmap(scaleBitmap(bitmap))
                recognizeBackend(jpeg) ?: recognizeCloud(jpeg)
            } ?: return@withContext Result.failure(
                IOException("OCR timed out. Check your connection and try again.")
            )
            Result.success(text)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(IOException(friendlyOcrError(e)))
        }
    }

    /** Our own backend does the OCR server-side (RapidOCR locally, free). */
    private suspend fun recognizeBackend(jpeg: ByteArray): String? {
        val response = try {
            withTimeoutOrNull(BACKEND_TIMEOUT_MS) {
                backendService.ocr(
                    listOf(
                        MultipartBody.Part.createFormData(
                            "files",
                            "scan.jpg",
                            jpeg.toRequestBody(JPEG.toMediaType())
                        )
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } ?: return null
        return response.text?.trim()?.ifEmpty { null }
    }

    /** Direct ocr.space call — kept as the fallback when the backend is unreachable. */
    private suspend fun recognizeCloud(jpeg: ByteArray): String {
        val base64Image = "data:image/jpeg;base64," + Base64.encodeToString(jpeg, Base64.NO_WRAP)
        var lastError: Exception = IOException("OCR request failed")

        repeat(MAX_ATTEMPTS) { attempt ->
            val isLastAttempt = attempt == MAX_ATTEMPTS - 1
            try {
                val response = withTimeoutOrNull(ATTEMPT_TIMEOUT_MS) {
                    ocrService.parseImage(
                        apiKey = OCR_KEY,
                        base64Image = base64Image.toRequestBody(TEXT.toMediaType()),
                        language = "eng".toRequestBody(TEXT.toMediaType()),
                        isOverlayRequired = "false".toRequestBody(TEXT.toMediaType()),
                        ocrEngine = "2".toRequestBody(TEXT.toMediaType()),
                        scale = "true".toRequestBody(TEXT.toMediaType()),
                        isTable = "true".toRequestBody(TEXT.toMediaType())
                    )
                } ?: throw IOException("OCR request timed out")

                if (response.IsErroredOnProcessing == true) {
                    val msg = response.ErrorMessage?.firstOrNull() ?: "OCR processing error"
                    lastError = IOException(msg)
                    if (isLastAttempt) throw lastError
                    delay(2000L * (attempt + 1))
                    return@repeat
                }

                val text = response.ParsedResults?.firstOrNull()?.ParsedText?.trim().orEmpty()
                if (text.isNotBlank()) return text
                lastError = IOException("No text detected in image")
                if (isLastAttempt) throw lastError
                delay(2000L * (attempt + 1))
            } catch (e: CancellationException) {
                throw e
            } catch (e: retrofit2.HttpException) {
                lastError = if (e.code() == 429 && !isLastAttempt) {
                    delay(3000L * (attempt + 1))
                    IOException("OCR rate limited")
                } else {
                    IOException("OCR error ${e.code()}: ${e.message()}")
                }
                if (isLastAttempt) throw lastError
            } catch (e: IOException) {
                lastError = e
                if (isLastAttempt) throw e
                delay(2000L * (attempt + 1))
            }
        }
        throw lastError
    }

    private fun scaleBitmap(bitmap: Bitmap): Bitmap {
        val maxDim = maxOf(bitmap.width, bitmap.height)
        return if (maxDim > MAX_IMAGE_DIM) {
            val scale = MAX_IMAGE_DIM.toFloat() / maxDim
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt(),
                (bitmap.height * scale).toInt(),
                true
            )
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

/**
 * OCR failures surface as toasts, so a null message ("OCR failed: null") is a
 * bug. Everything the user sees goes through here.
 */
internal fun friendlyOcrError(e: Throwable): String {
    val message = e.message?.trim().orEmpty()
    val lower = message.lowercase()
    return when {
        message.isEmpty() -> "OCR failed. Try a different image."
        "timed out" in lower || "timeout" in lower ->
            "OCR timed out. Check your connection and try again."
        "unable to resolve host" in lower || "failed to connect" in lower ||
            "connection refused" in lower ->
            "No connection for OCR. Try again when you're online."
        else -> message
    }
}
