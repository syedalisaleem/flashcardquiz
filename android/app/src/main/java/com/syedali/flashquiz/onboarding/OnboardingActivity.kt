package com.syedali.flashquiz.onboarding

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.syedali.flashquiz.MainActivity
import com.syedali.flashquiz.R
import java.io.File

class OnboardingActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var skipButton: TextView
    private lateinit var nextContainer: FrameLayout
    private lateinit var bottomBar: LinearLayout
    private lateinit var dots: Array<ImageView>

    private val pageLayouts = listOf(
        R.layout.onboarding_page1,
        R.layout.onboarding_page2,
        R.layout.onboarding_page3,
        R.layout.onboarding_page4
    )

    private var selectedPictureUri: Uri? = null
    private var photoUri: Uri? = null

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            // Take persistent permission
            contentResolver.takePersistableUriPermission(
                it, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            onProfilePictureSelected(it)
        }
    }

    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success && photoUri != null) {
            onProfilePictureSelected(photoUri!!)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        viewPager = findViewById(R.id.view_pager)
        skipButton = findViewById(R.id.btn_skip)
        nextContainer = findViewById(R.id.btn_next_container)
        bottomBar = findViewById(R.id.bottom_bar)

        dots = arrayOf(
            findViewById(R.id.dot_0),
            findViewById(R.id.dot_1),
            findViewById(R.id.dot_2),
            findViewById(R.id.dot_3)
        )

        val adapter = OnboardingPagerAdapter(
            pageLayouts,
            onProfilePictureTap = { showPicturePickerBottomSheet() },
            onGetStarted = { completeOnboarding() }
        )
        viewPager.adapter = adapter
        viewPager.isUserInputEnabled = true

        // Set up page change callback
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateIndicators(position)
                updateSkipButton(position)
                updateNextButton(position)
                updateBottomBar(position)

                // Animate card flip on page 1 when it appears
                if (position == 0) {
                    startCardFlipAnimation()
                }
            }
        })

        // Skip button
        skipButton.setOnClickListener {
            completeOnboarding()
        }

        // Next button
        nextContainer.setOnClickListener {
            val current = viewPager.currentItem
            if (current < pageLayouts.size - 1) {
                viewPager.currentItem = current + 1
            } else {
                completeOnboarding()
            }
        }

        // Start initial animation
        startCardFlipAnimation()
    }

    private fun updateIndicators(position: Int) {
        for (i in dots.indices) {
            dots[i].isSelected = i == position
        }
    }

    private fun updateSkipButton(position: Int) {
        // Hide skip on last page
        skipButton.visibility = if (position < pageLayouts.size - 1) {
            View.VISIBLE
        } else {
            View.INVISIBLE
        }
    }

    private fun updateNextButton(position: Int) {
        // Hide next button on last page (Get Started button handles it)
        nextContainer.visibility = if (position < pageLayouts.size - 1) {
            View.VISIBLE
        } else {
            View.INVISIBLE
        }
    }

    private fun updateBottomBar(position: Int) {
        bottomBar.visibility = if (position < pageLayouts.size - 1) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun startCardFlipAnimation() {
        val handler = Handler(Looper.getMainLooper())
        val delay = 1500L // delay between flips

        val flipRunnable = object : Runnable {
            override fun run() {
                val cardContainer = findViewById<FrameLayout>(R.id.card_container) ?: return
                val cardFront = cardContainer.findViewById<View>(R.id.card_front) ?: return
                val cardBack = cardContainer.findViewById<View>(R.id.card_back) ?: return

                // Flip to back
                cardFront.animate()
                    .rotationY(-90f)
                    .setDuration(300)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .withEndAction {
                        cardFront.alpha = 0f
                        cardBack.alpha = 1f
                        cardBack.rotationY = 90f

                        cardBack.animate()
                            .rotationY(0f)
                            .setDuration(300)
                            .setInterpolator(AccelerateDecelerateInterpolator())
                            .withEndAction {
                                // Hold for a moment, then flip back
                                handler.postDelayed({
                                    // Flip back to front
                                    cardBack.animate()
                                        .rotationY(-90f)
                                        .setDuration(300)
                                        .setInterpolator(AccelerateDecelerateInterpolator())
                                        .withEndAction {
                                            cardBack.alpha = 0f
                                            cardFront.alpha = 1f
                                            cardFront.rotationY = 90f

                                            cardFront.animate()
                                                .rotationY(0f)
                                                .setDuration(300)
                                                .setInterpolator(AccelerateDecelerateInterpolator())
                                                .start()
                                        }
                                        .start()
                                }, 2000)
                            }
                            .start()
                    }
                    .start()

                // Repeat after delay
                handler.postDelayed(this, delay + 2600)
            }
        }

        // Start the first flip after a short delay
        handler.postDelayed(flipRunnable, 800)
    }

    private fun showPicturePickerBottomSheet() {
        val dialog = BottomSheetDialog(this)

        val bottomSheetView = layoutInflater.inflate(R.layout.bottom_sheet_picture_picker, null)
        dialog.setContentView(bottomSheetView)

        // Style the bottom sheet
        val bottomSheet = bottomSheetView.parent as View
        bottomSheet.background = ContextCompat.getDrawable(this, R.drawable.onboarding_preview_card)

        bottomSheetView.findViewById<View>(R.id.option_take_photo)?.setOnClickListener {
            dialog.dismiss()
            openCamera()
        }

        bottomSheetView.findViewById<View>(R.id.option_choose_gallery)?.setOnClickListener {
            dialog.dismiss()
            openGallery()
        }

        dialog.show()
    }

    private fun openCamera() {
        val photoFile = File(cacheDir, "profile_photo_${System.currentTimeMillis()}.jpg")
        photoUri = FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            photoFile
        )
        takePictureLauncher.launch(photoUri!!)
    }

    private fun openGallery() {
        pickImageLauncher.launch("image/*")
    }

    private fun onProfilePictureSelected(uri: Uri) {
        selectedPictureUri = uri

        // Update the profile picture in the ViewPager's current view
        val currentPage = viewPager.getChildAt(viewPager.currentItem)

        currentPage?.let { view ->
            val ivProfile = view.findViewById<ImageView>(R.id.iv_profile_picture)
            val cameraOverlay = view.findViewById<View>(R.id.camera_overlay)

            ivProfile?.setImageURI(uri)
            cameraOverlay?.alpha = 0f
        }

        // Store in ProfileManager
        ProfileManager.get(this).setPictureUri(this, uri)
    }

    private fun completeOnboarding() {
        // Save name from page 4
        saveProfileName()

        // Mark onboarding as done
        getSharedPreferences("flashquiz_prefs", MODE_PRIVATE)
            .edit()
            .putBoolean("onboarding_done", true)
            .apply()

        // Navigate to MainActivity
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    private fun saveProfileName() {
        val currentPage = viewPager.getChildAt(viewPager.currentItem)
        currentPage?.let { view ->
            val nameInput = view.findViewById<EditText>(R.id.edit_name)
            nameInput?.text?.toString()?.trim()?.let { name ->
                if (name.isNotEmpty()) {
                    ProfileManager.get(this).setName(this, name)
                }
            }
        }
    }
}
