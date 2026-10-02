package com.syedali.flashquiz.ui.generate

import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import com.syedali.flashquiz.BuildConfig
import com.syedali.flashquiz.R
import com.syedali.flashquiz.data.repository.DeckRepository
import com.syedali.flashquiz.data.repository.FlashcardRepository
import com.syedali.flashquiz.data.repository.McqRepository
import com.syedali.flashquiz.network.AiRepository
import com.syedali.flashquiz.network.BackendService
import com.syedali.flashquiz.network.BackendStudyRequest
import com.syedali.flashquiz.network.OcrRepository
import com.syedali.flashquiz.network.toFlashcardOrNull
import com.syedali.flashquiz.network.toMcqOrNull
import com.syedali.flashquiz.theme.ThemeManager
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject

@AndroidEntryPoint
class GenerateFragment : Fragment() {
    private val viewModel: GenerateViewModel by viewModels()

    @Inject lateinit var flashcardRepo: FlashcardRepository
    @Inject lateinit var mcqRepo: McqRepository
    @Inject lateinit var deckRepo: DeckRepository
    @Inject lateinit var ocrRepository: OcrRepository
    @Inject lateinit var backendService: BackendService

    private lateinit var editDeckName: EditText
    private lateinit var editSourceText: EditText
    private lateinit var editNumCards: EditText
    private lateinit var editNumMcqs: EditText
    private lateinit var editTags: EditText
    private lateinit var editStudyPrompt: EditText
    private lateinit var editStudyClass: EditText
    private lateinit var editStudySchool: EditText
    private lateinit var editStudyCity: EditText
    private lateinit var editStudyCountry: EditText
    private lateinit var btnStudy: MaterialButton
    private lateinit var progressBar: ProgressBar
    private lateinit var tvStatus: TextView
    private lateinit var ivSuccess: ImageView
    private lateinit var btnGenerate: MaterialButton
    private lateinit var layoutProgress: View

    private val mainHandler = Handler(Looper.getMainLooper())
    private var progressRunnable: Runnable? = null

    companion object {
        private const val MAX_FILE_SIZE_MB = 50
        private const val MAX_FILE_SIZE_BYTES = MAX_FILE_SIZE_MB * 1024L * 1024L

        private val PROGRESS_MESSAGES = arrayOf(
            "Analyzing your text...",
            "Creating flashcards...",
            "Almost done!"
        )
        private const val PROGRESS_MESSAGE_INTERVAL_MS = 2500L
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

        editDeckName = view.findViewById(R.id.edit_deck_name)
        editSourceText = view.findViewById(R.id.edit_source_text)
        editNumCards = view.findViewById(R.id.edit_num_cards)
        editNumMcqs = view.findViewById(R.id.edit_num_mcqs)
        editTags = view.findViewById(R.id.edit_tags)
        editStudyPrompt = view.findViewById(R.id.edit_study_prompt)
        editStudyClass = view.findViewById(R.id.edit_study_class)
        editStudySchool = view.findViewById(R.id.edit_study_school)
        editStudyCity = view.findViewById(R.id.edit_study_city)
        editStudyCountry = view.findViewById(R.id.edit_study_country)
        btnStudy = view.findViewById(R.id.btn_study)
        progressBar = view.findViewById(R.id.progress_bar)
        tvStatus = view.findViewById(R.id.tv_status)
        ivSuccess = view.findViewById(R.id.iv_success)
        btnGenerate = view.findViewById(R.id.btn_generate)
        layoutProgress = view.findViewById(R.id.layout_progress)

        applyTheme(view)
        com.syedali.flashquiz.theme.ThemeUtils.applyThemeToViews(view, ThemeManager.getCurrentTheme(requireContext()))

        // Auto-suggest deck name if field is empty
        autoSuggestDeckName()

        view.findViewById<MaterialButton>(R.id.btn_ocr).setOnClickListener {
            tvStatus.text = "Opening image picker..."
            pickFiles.launch(arrayOf("image/*"))
        }

        view.findViewById<MaterialButton>(R.id.btn_import_pdf).setOnClickListener {
            tvStatus.text = "Opening file picker..."
            pickFiles.launch(arrayOf("application/pdf", "text/*"))
        }

        btnGenerate.setOnClickListener { generate() }
        btnStudy.setOnClickListener { generateFromStudyPlan() }

        // Subtle press animation for generate button
        setupPressAnimation(btnGenerate)
        setupPressAnimation(btnStudy)

        // Rewarded ad: watch for +5 cards (ads ship disabled until AdMob is live)
        val btnWatchAd = view.findViewById<TextView>(R.id.btn_watch_ad)
        if (!BuildConfig.ADS_ENABLED) {
            btnWatchAd.visibility = View.GONE
        }
        btnWatchAd.setOnClickListener {
            com.syedali.flashquiz.ads.RewardedAdManager.loadAd(requireContext())
            com.syedali.flashquiz.ads.RewardedAdManager.showAdIfAvailable(
                requireActivity(),
                onReward = {
                    activity?.runOnUiThread {
                        Toast.makeText(requireContext(), "+5 bonus cards added!", Toast.LENGTH_SHORT).show()
                        generate(extraCards = 5, extraMcqs = 2)
                    }
                },
                onFailed = {
                    activity?.runOnUiThread {
                        Toast.makeText(requireContext(), "No ad available. Try again later.", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopProgressAnimation()
    }

    private fun autoSuggestDeckName() {
        if (editDeckName.text.isNullOrBlank()) {
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val count = deckRepo.getDecks().size
                    if (editDeckName.text.isNullOrBlank()) {
                        editDeckName.setText("My Deck #${count + 1}")
                        editDeckName.setSelection(editDeckName.text.length)
                    }
                } catch (_: Exception) {
                    // Silently fail — deck name remains empty for user to type
                }
            }
        }
    }

    private fun setupPressAnimation(button: View) {
        button.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate()
                        .scaleX(0.95f)
                        .scaleY(0.95f)
                        .setDuration(100)
                        .setInterpolator(AccelerateDecelerateInterpolator())
                        .start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(150)
                        .setInterpolator(OvershootInterpolator())
                        .start()
                }
            }
            false // Let click still fire
        }
    }

    private fun startProgressAnimation() {
        var messageIndex = 0
        tvStatus.text = PROGRESS_MESSAGES[0]

        progressRunnable = object : Runnable {
            override fun run() {
                messageIndex = (messageIndex + 1) % PROGRESS_MESSAGES.size
                tvStatus.text = PROGRESS_MESSAGES[messageIndex]
                mainHandler.postDelayed(this, PROGRESS_MESSAGE_INTERVAL_MS)
            }
        }
        mainHandler.postDelayed(progressRunnable!!, PROGRESS_MESSAGE_INTERVAL_MS)
    }

    private fun stopProgressAnimation() {
        progressRunnable?.let { mainHandler.removeCallbacks(it) }
        progressRunnable = null
    }

    private fun showSuccessState() {
        stopProgressAnimation()
        tvStatus.text = "All done!"
        ivSuccess.visibility = View.VISIBLE
        ivSuccess.alpha = 0f
        ivSuccess.scaleX = 0.3f
        ivSuccess.scaleY = 0.3f
        ivSuccess.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(400)
            .setInterpolator(OvershootInterpolator(2f))
            .start()
    }

    private fun applyTheme(view: View) {
        val theme = ThemeManager.getCurrentTheme(requireContext())
        view.setBackgroundColor(theme.background)

        view.findViewById<TextView>(R.id.tv_title)?.setTextColor(theme.textPrimary)
        view.findViewById<TextView>(R.id.tv_subtitle)?.setTextColor(theme.textSecondary)
        view.findViewById<TextView>(R.id.tv_study_title)?.setTextColor(theme.textPrimary)
        tvStatus.setTextColor(theme.textSecondary)

        applyInputStyle(editDeckName, theme)
        applyInputStyle(editSourceText, theme)
        applyInputStyle(editNumCards, theme)
        applyInputStyle(editNumMcqs, theme)
        applyInputStyle(editTags, theme)
        applyInputStyle(editStudyPrompt, theme)
        applyInputStyle(editStudyClass, theme)
        applyInputStyle(editStudySchool, theme)
        applyInputStyle(editStudyCity, theme)
        applyInputStyle(editStudyCountry, theme)

        val btnOcr = view.findViewById<MaterialButton>(R.id.btn_ocr)
        val btnPdf = view.findViewById<MaterialButton>(R.id.btn_import_pdf)
        applyButtonStyle(btnOcr, theme)
        applyButtonStyle(btnPdf, theme)
        applyButtonStyle(btnStudy, theme)
        btnStudy.setTextColor(theme.textPrimary)

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
        view.findViewById<TextView>(R.id.tv_study_hint)?.setTextColor(theme.textMuted)
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
        layoutProgress.visibility = View.VISIBLE

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
                            val result = processImageUri(uri)
                            result.onSuccess { text ->
                                if (text.isNotBlank()) {
                                    allText.appendLine(text)
                                    allText.appendLine()
                                    processedCount++
                                } else {
                                    errorCount++
                                }
                            }
                            result.onFailure { e ->
                                errorCount++
                                activity?.runOnUiThread {
                                    Toast.makeText(
                                        requireContext(),
                                        "OCR failed: ${e.message ?: "try another image"}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
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
                        Toast.makeText(
                            requireContext(),
                            "Error: ${e.message ?: "could not read file"}",
                            Toast.LENGTH_SHORT
                        ).show()
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

    private fun processImageUri(uri: Uri): Result<String> {
        val inputStream = requireContext().contentResolver.openInputStream(uri)
            ?: return Result.failure(Exception("Cannot read image"))
        val bytes = inputStream.readBytes()
        inputStream.close()
        if (bytes.isEmpty()) return Result.failure(Exception("Image file is empty"))

        // OcrRepository owns the per-attempt and per-image timeouts. Wrapping it
        // here in one shared `withTimeout` made TimeoutCancellationException
        // (message == null) leak out as "OCR failed: null".
        return kotlinx.coroutines.runBlocking {
            ocrRepository.recognizeText(bytes)
        }
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

    /**
     * Study plan flow: prompt + class/school/city/country -> backend fetches
     * textbook notes (Firecrawl/Gemini) -> server-side generation job -> poll
     * -> pull the finished deck back and save it into the local Room library.
     */
    private fun generateFromStudyPlan() {
        val prompt = editStudyPrompt.text.toString().trim()
        if (prompt.isEmpty()) {
            editStudyPrompt.error = "Required"
            return
        }
        if (BuildConfig.BACKEND_URL.isBlank()) {
            Toast.makeText(
                requireContext(),
                "Cloud study plans are not configured for this build.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val numCards = (editNumCards.text.toString().toIntOrNull() ?: 10).coerceIn(1, 200)
        val numMcqs = (editNumMcqs.text.toString().toIntOrNull() ?: 3).coerceIn(0, 100)
        val tags = editTags.text.toString().split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val className = editStudyClass.text.toString().trim().ifBlank { null }
        val school = editStudySchool.text.toString().trim().ifBlank { null }
        val city = editStudyCity.text.toString().trim().ifBlank { null }
        val country = editStudyCountry.text.toString().trim().ifBlank { null }

        btnGenerate.isEnabled = false
        btnStudy.isEnabled = false
        progressBar.visibility = View.VISIBLE
        ivSuccess.visibility = View.GONE
        layoutProgress.visibility = View.VISIBLE
        stopProgressAnimation()
        tvStatus.text = "Finding your textbook..."

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val study = backendService.studyRequest(
                    BackendStudyRequest(
                        prompt = prompt,
                        className = className,
                        school = school,
                        city = city,
                        country = country,
                        deckName = prompt.take(80),
                        numCards = numCards,
                        numMcqs = numMcqs,
                        tags = tags,
                    )
                )
                val jobId = study.jobId ?: error("Server did not start a generation job")

                var status = ""
                var attempts = 0
                while (status != "done") {
                    delay(2000)
                    attempts++
                    if (attempts > 300) error("Timed out waiting for generation") // ~10 min
                    val job = backendService.job(jobId)
                    status = job.status.orEmpty()
                    when (status) {
                        "error" -> error(job.error ?: "Generation failed")
                        "paused" -> tvStatus.text =
                            "AI provider is busy — retrying in ${job.wait_s ?: 10}s..."
                        "done" -> Unit
                        else -> {
                            val target = job.targetCards ?: 0
                            val made = job.cards ?: 0
                            tvStatus.text = when {
                                target > 0 && made < target -> "Creating flashcards... $made/$target"
                                (job.targetMcqs ?: 0) > 0 -> "Creating quiz questions..."
                                else -> "Creating your deck..."
                            }
                        }
                    }
                }

                tvStatus.text = "Downloading your deck..."
                val serverDeck = backendService.deck(study.deckId ?: error("Deck missing"))
                val deckName = serverDeck.name?.trim().orEmpty().ifBlank { prompt.take(80) }
                val cards = (serverDeck.cards ?: emptyList()).mapNotNull { it.toFlashcardOrNull(tags) }
                val mcqs = (serverDeck.mcqs ?: emptyList()).mapNotNull { it.toMcqOrNull(tags) }
                if (cards.isEmpty() && mcqs.isEmpty()) {
                    error("The AI returned no usable cards. Try rephrasing your prompt.")
                }

                val deckId = deckRepo.create(deckName, tags)
                if (cards.isNotEmpty()) flashcardRepo.add(deckId, cards)
                if (mcqs.isNotEmpty()) mcqRepo.add(deckId, mcqs)

                showSuccessState()
                progressBar.visibility = View.GONE
                btnGenerate.isEnabled = true
                btnStudy.isEnabled = true
                Toast.makeText(
                    requireContext(),
                    "Deck ready: ${cards.size} cards, ${mcqs.size} quiz questions",
                    Toast.LENGTH_LONG
                ).show()
                mainHandler.postDelayed({ editStudyPrompt.text.clear() }, 1500)
            } catch (e: retrofit2.HttpException) {
                stopProgressAnimation()
                val msg = when (e.code()) {
                    429 -> "Server busy. Wait a few seconds and try again."
                    502 -> "Couldn't find study material for that prompt. Try rephrasing."
                    in 500..599 -> "Server error. Try again in a moment."
                    else -> "Request failed (${e.code()})."
                }
                tvStatus.text = "Error: $msg"
                progressBar.visibility = View.GONE
                btnGenerate.isEnabled = true
                btnStudy.isEnabled = true
            } catch (e: Exception) {
                stopProgressAnimation()
                tvStatus.text = "Error: ${cleanError(e.message)}"
                progressBar.visibility = View.GONE
                btnGenerate.isEnabled = true
                btnStudy.isEnabled = true
            }
        }
    }

    private fun generate(extraCards: Int = 0, extraMcqs: Int = 0) {
        val deckName = editDeckName.text.toString().trim()
        val fullSource = editSourceText.text.toString().trim()
        val sourceText = fullSource.take(AiRepository.MAX_SOURCE_CHARS)
        val numCards = (editNumCards.text.toString().toIntOrNull() ?: 5) + extraCards
        val numMcqs = (editNumMcqs.text.toString().toIntOrNull() ?: 2) + extraMcqs
        val tags = editTags.text.toString().split(",").map { it.trim() }.filter { it.isNotEmpty() }

        if (deckName.isEmpty()) { editDeckName.error = "Required"; return }
        if (sourceText.isEmpty()) { editSourceText.error = "Required"; return }

        if (sourceText.length < fullSource.length) {
            Toast.makeText(
                requireContext(),
                "Source limited to ${AiRepository.MAX_SOURCE_CHARS} characters.",
                Toast.LENGTH_LONG
            ).show()
        }

        btnGenerate.isEnabled = false
        progressBar.visibility = View.VISIBLE
        ivSuccess.visibility = View.GONE
        startProgressAnimation()

        viewLifecycleOwner.lifecycleScope.launch {
            val deckId = deckRepo.create(deckName, tags)

            viewModel.generateFlashcards(deckId, sourceText, numCards, tags)
            val flashState = viewModel.state.value
            if (flashState.error != null) {
                stopProgressAnimation()
                tvStatus.text = "Error: ${cleanError(flashState.error)}"
                progressBar.visibility = View.GONE
                btnGenerate.isEnabled = true
                viewModel.clearError()
                return@launch
            }

            tvStatus.text = "Creating flashcards..."
            viewModel.generateMcqs(deckId, sourceText, numMcqs, tags)
            val mcqState = viewModel.state.value
            if (mcqState.error != null) {
                stopProgressAnimation()
                tvStatus.text = "MCQs failed: ${cleanError(mcqState.error)}. Cards saved."
                progressBar.visibility = View.GONE
                btnGenerate.isEnabled = true
                viewModel.clearError()
                return@launch
            }

            showSuccessState()
            progressBar.visibility = View.GONE
            btnGenerate.isEnabled = true
            viewModel.resetGenerated()
            mainHandler.postDelayed({
                editSourceText.text.clear()
                editDeckName.text.clear()
                autoSuggestDeckName()
            }, 1500)
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
