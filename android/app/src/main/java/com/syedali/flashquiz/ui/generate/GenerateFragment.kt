package com.syedali.flashquiz.ui.generate

import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.syedali.flashquiz.R
import com.syedali.flashquiz.api.ApiClient
import com.syedali.flashquiz.data.DatabaseHelper
import com.syedali.flashquiz.data.DeckRepository
import com.syedali.flashquiz.data.FlashcardRepository
import com.syedali.flashquiz.data.MCQRepository
import com.syedali.flashquiz.theme.ThemeManager
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.BufferedReader
import java.io.InputStreamReader

class GenerateFragment : Fragment() {
    private lateinit var flashcardRepo: FlashcardRepository
    private lateinit var mcqRepo: MCQRepository
    private lateinit var api: ApiClient
    private lateinit var editDeckName: EditText
    private lateinit var editSourceText: EditText
    private lateinit var editNumCards: EditText
    private lateinit var editNumMcqs: EditText
    private lateinit var editTags: EditText
    private lateinit var progressBar: ProgressBar
    private lateinit var tvStatus: TextView
    private lateinit var btnGenerate: MaterialButton

    companion object {
        private const val MAX_FILE_SIZE_MB = 50
        private const val MAX_FILE_SIZE_BYTES = MAX_FILE_SIZE_MB * 1024L * 1024L
    }

    private val pickFiles = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            processFiles(uris)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_generate, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        PDFBoxResourceLoader.init(requireContext())

        val dbHelper = DatabaseHelper(requireContext())
        flashcardRepo = FlashcardRepository(dbHelper)
        mcqRepo = MCQRepository(dbHelper)
        api = ApiClient(requireContext())

        editDeckName = view.findViewById(R.id.edit_deck_name)
        editSourceText = view.findViewById(R.id.edit_source_text)
        editNumCards = view.findViewById(R.id.edit_num_cards)
        editNumMcqs = view.findViewById(R.id.edit_num_mcqs)
        editTags = view.findViewById(R.id.edit_tags)
        progressBar = view.findViewById(R.id.progress_bar)
        tvStatus = view.findViewById(R.id.tv_status)
        btnGenerate = view.findViewById(R.id.btn_generate)

        applyTheme(view)

        view.findViewById<MaterialButton>(R.id.btn_ocr).setOnClickListener {
            pickFiles.launch(arrayOf("image/*"))
        }

        view.findViewById<MaterialButton>(R.id.btn_import_pdf).setOnClickListener {
            pickFiles.launch(arrayOf("application/pdf", "text/*"))
        }

        btnGenerate.setOnClickListener { generate() }
    }

    private fun applyTheme(view: View) {
        val theme = ThemeManager.getCurrentTheme(requireContext())
        view.setBackgroundColor(theme.background)

        view.findViewById<TextView>(R.id.tv_title)?.setTextColor(theme.textPrimary)
        view.findViewById<TextView>(R.id.tv_subtitle)?.setTextColor(theme.textSecondary)
        tvStatus.setTextColor(theme.textSecondary)

        applyInputStyle(editDeckName, theme)
        applyInputStyle(editSourceText, theme)
        applyInputStyle(editNumCards, theme)
        applyInputStyle(editNumMcqs, theme)
        applyInputStyle(editTags, theme)

        val btnOcr = view.findViewById<MaterialButton>(R.id.btn_ocr)
        val btnPdf = view.findViewById<MaterialButton>(R.id.btn_import_pdf)
        applyButtonStyle(btnOcr, theme)
        applyButtonStyle(btnPdf, theme)

        val btnGenerateBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 28f
            setColor(theme.accent)
        }
        btnGenerate.background = btnGenerateBg
        btnGenerate.setTextColor(theme.textPrimary)

        progressBar.indeterminateTintList = android.content.res.ColorStateList.valueOf(theme.accent)

        val hintTextView = view.findViewById<TextView>(R.id.tv_file_hint)
        hintTextView?.setTextColor(theme.textMuted)
    }

    private fun applyInputStyle(editText: EditText, theme: com.syedali.flashquiz.theme.AppTheme) {
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 24f
            setColor(theme.surface)
            setStroke(1, theme.border)
        }
        editText.background = bg
        editText.setTextColor(theme.textPrimary)
        editText.setHintTextColor(theme.textMuted)
    }

    private fun applyButtonStyle(button: MaterialButton, theme: com.syedali.flashquiz.theme.AppTheme) {
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 24f
            setColor(theme.surfaceVariant)
        }
        button.background = bg
        button.setTextColor(theme.textPrimary)
    }

    private fun processFiles(uris: List<Uri>) {
        tvStatus.text = "Processing ${uris.size} file(s)..."
        progressBar.visibility = View.VISIBLE

        Thread {
            val allText = StringBuilder()
            var processedCount = 0
            var errorCount = 0

            for (uri in uris) {
                try {
                    val fileSize = getFileSize(uri)
                    if (fileSize > MAX_FILE_SIZE_BYTES) {
                        errorCount++
                        activity?.runOnUiThread {
                            Toast.makeText(requireContext(), "File too large (${fileSize / 1024 / 1024}MB). Max: ${MAX_FILE_SIZE_MB}MB", Toast.LENGTH_LONG).show()
                        }
                        continue
                    }

                    val mimeType = requireContext().contentResolver.getType(uri) ?: ""

                    when {
                        mimeType.startsWith("image/") -> {
                            val text = processImageUri(uri)
                            if (text.isNotBlank()) {
                                allText.appendLine(text)
                                allText.appendLine()
                                processedCount++
                            }
                        }
                        mimeType == "application/pdf" -> {
                            val text = processPdfUri(uri)
                            if (text.isNotBlank()) {
                                allText.appendLine(text)
                                allText.appendLine()
                                processedCount++
                            }
                        }
                        mimeType.startsWith("text/") -> {
                            val text = processTextUri(uri)
                            if (text.isNotBlank()) {
                                allText.appendLine(text)
                                allText.appendLine()
                                processedCount++
                            }
                        }
                        else -> errorCount++
                    }
                } catch (e: Exception) {
                    errorCount++
                    activity?.runOnUiThread {
                        Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            activity?.runOnUiThread {
                if (allText.isNotBlank()) {
                    editSourceText.append(allText.toString())
                    tvStatus.text = "Processed $processedCount file(s). Tap Generate."
                } else {
                    tvStatus.text = "No text extracted from files."
                }
                progressBar.visibility = View.GONE
            }
        }.start()
    }

    private fun getFileSize(uri: Uri): Long {
        val cursor = requireContext().contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val sizeIndex = it.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (sizeIndex >= 0) return it.getLong(sizeIndex)
            }
        }
        return 0L
    }

    private fun processImageUri(uri: Uri): String {
        val inputStream = requireContext().contentResolver.openInputStream(uri) ?: return ""
        val bytes = inputStream.readBytes()
        inputStream.close()
        if (bytes.isEmpty()) return ""

        var result = ""
        val latch = java.util.concurrent.CountDownLatch(1)
        api.ocrImage(bytes) { ocrResult ->
            ocrResult.onSuccess { text -> result = text }
            latch.countDown()
        }
        latch.await(60, java.util.concurrent.TimeUnit.SECONDS)
        return result
    }

    private fun processPdfUri(uri: Uri): String {
        val inputStream = requireContext().contentResolver.openInputStream(uri) ?: return ""
        val document = PDDocument.load(inputStream)
        val text = PDFTextStripper().getText(document)
        document.close()
        inputStream.close()
        return text.trim()
    }

    private fun processTextUri(uri: Uri): String {
        val inputStream = requireContext().contentResolver.openInputStream(uri) ?: return ""
        val reader = BufferedReader(InputStreamReader(inputStream))
        val text = reader.readText()
        reader.close()
        return text.trim()
    }

    private fun generate() {
        val deckName = editDeckName.text.toString().trim()
        val sourceText = editSourceText.text.toString().trim()
        val numCards = editNumCards.text.toString().toIntOrNull() ?: 5
        val numMcqs = editNumMcqs.text.toString().toIntOrNull() ?: 2
        val tags = editTags.text.toString().split(",").map { it.trim() }.filter { it.isNotEmpty() }

        if (deckName.isEmpty()) { editDeckName.error = "Required"; return }
        if (sourceText.isEmpty()) { editSourceText.error = "Required"; return }

        btnGenerate.isEnabled = false
        progressBar.visibility = View.VISIBLE
        tvStatus.text = "Generating cards..."

        val deckId = DeckRepository(DatabaseHelper(requireContext())).create(deckName, tags)

        api.generateFlashcards(sourceText, numCards, tags) { result ->
            result.onSuccess { cards ->
                flashcardRepo.add(deckId, cards)
                activity?.runOnUiThread { tvStatus.text = "Cards done. Generating MCQs..." }

                api.generateMCQs(sourceText, numMcqs, tags) { mcqResult ->
                    mcqResult.onSuccess { mcqs ->
                        mcqRepo.add(deckId, mcqs)
                        activity?.runOnUiThread {
                            tvStatus.text = "Done! ${cards.size} cards, ${mcqs.size} MCQs created."
                            progressBar.visibility = View.GONE
                            btnGenerate.isEnabled = true
                            editSourceText.text.clear()
                            editDeckName.text.clear()
                        }
                    }
                    mcqResult.onFailure { e ->
                        activity?.runOnUiThread {
                            tvStatus.text = "MCQs failed: ${cleanError(e.message)}. Cards saved."
                            progressBar.visibility = View.GONE
                            btnGenerate.isEnabled = true
                        }
                    }
                }
            }
            result.onFailure { e ->
                activity?.runOnUiThread {
                    tvStatus.text = "Error: ${cleanError(e.message)}"
                    progressBar.visibility = View.GONE
                    btnGenerate.isEnabled = true
                }
            }
        }
    }

    private fun cleanError(msg: String?): String {
        if (msg == null) return "Unknown error"
        return when {
            msg.contains("429") || msg.contains("Queue full") -> "Server busy. Wait a few seconds and try again."
            msg.contains("Unterminated") || msg.contains("malformed") || msg.contains("parse") -> "AI response cut off. Try again."
            msg.contains("timeout") -> "Request timed out. Try shorter text."
            msg.contains("Could not find card data") -> "AI returned empty response. Try again."
            else -> msg.take(120)
        }
    }
}
