package com.syedali.flashquiz

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.syedali.flashquiz.auth.AccountDialogFragment
import com.syedali.flashquiz.auth.AccountManager
import com.syedali.flashquiz.onboarding.OnboardingActivity
import com.syedali.flashquiz.theme.ThemeHelper
import com.syedali.flashquiz.theme.ThemeManager
import com.syedali.flashquiz.theme.ThemePickerDialogFragment
import com.syedali.flashquiz.ui.generate.GenerateFragment
import com.syedali.flashquiz.ui.home.HomeFragment
import com.syedali.flashquiz.ui.leaderboard.LeaderboardFragment
import com.syedali.flashquiz.ui.review.ReviewFragment
import com.syedali.flashquiz.ui.stats.StatsFragment
import com.syedali.flashquiz.ui.subscription.SubscriptionFragment
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity(), ThemePickerDialogFragment.ThemeSelectionListener,
    AccountDialogFragment.AccountListener {

    private lateinit var currentTheme: com.syedali.flashquiz.theme.AppTheme

    override fun onCreate(savedInstanceState: Bundle?) {
        // Redirect to onboarding if first launch
        if (!isOnboardingDone()) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        currentTheme = ThemeManager.getCurrentTheme(this)
        applyTheme()

        // GDPR: update consent info and show Google's consent form when
        // required (EEA/UK). Non-EEA devices no-op silently. Runs once per
        // cold start, before any banner request fires from Home/Generate.
        if (BuildConfig.ADS_ENABLED) {
            try {
                val params = com.google.android.ump.ConsentRequestParameters.Builder().build()
                val consentInfo = com.google.android.ump.UserMessagingPlatform.getConsentInformation(this)
                consentInfo.requestConsentInfoUpdate(
                    this,
                    params,
                    {
                        com.google.android.ump.UserMessagingPlatform.loadAndShowConsentFormIfRequired(this) { }
                    },
                    { }
                )
            } catch (_: Exception) {
                // Consent flow must never block the UI.
            }
        }

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav)
        bottomNav.setOnItemSelectedListener { item ->
            val fragment: Fragment = when (item.itemId) {
                R.id.nav_home -> HomeFragment()
                R.id.nav_generate -> GenerateFragment()
                R.id.nav_review -> ReviewFragment()
                R.id.nav_stats -> StatsFragment()
                R.id.nav_leaderboard -> LeaderboardFragment()
                else -> HomeFragment()
            }
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit()
            true
        }

        // Default to Home fragment
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, HomeFragment())
                .commit()
        }
    }

    private fun applyTheme() {
        ThemeHelper.applyTheme(this, currentTheme)
    }

    override fun onThemeSelected(theme: com.syedali.flashquiz.theme.AppTheme) {
        currentTheme = theme
        ThemeManager.setTheme(this, theme.id)
        applyTheme()
        recreate()
    }

    override fun onAccountCreated(name: String, email: String) {
        // Refresh current fragment
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, HomeFragment())
            .commit()
    }

    override fun onLoggedIn(name: String, email: String) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, HomeFragment())
            .commit()
    }

    fun showAccountDialog() {
        val dialog = AccountDialogFragment()
        dialog.setListener(this)
        dialog.show(supportFragmentManager, "account")
    }

    fun showSubscription() {
        if (!BuildConfig.PAYMENTS_ENABLED) {
            android.widget.Toast.makeText(
                this,
                "Premium is coming soon — check back later!",
                android.widget.Toast.LENGTH_SHORT
            ).show()
            return
        }
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, SubscriptionFragment())
            .addToBackStack(null)
            .commit()
    }

    private fun isOnboardingDone(): Boolean {
        return getSharedPreferences("flashquiz_prefs", MODE_PRIVATE)
            .getBoolean("onboarding_done", false)
    }
}
