package com.syedali.flashquiz

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.syedali.flashquiz.theme.ThemeHelper
import com.syedali.flashquiz.theme.ThemeManager
import com.syedali.flashquiz.theme.ThemePickerDialogFragment
import com.syedali.flashquiz.ui.generate.GenerateFragment
import com.syedali.flashquiz.ui.home.HomeFragment
import com.syedali.flashquiz.ui.review.ReviewFragment

class MainActivity : AppCompatActivity(), ThemePickerDialogFragment.ThemeSelectionListener {

    private lateinit var currentTheme: com.syedali.flashquiz.theme.AppTheme

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        currentTheme = ThemeManager.getCurrentTheme(this)
        applyTheme()

        setSupportActionBar(findViewById(R.id.toolbar))

        val bottomNav = findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottom_nav)
        bottomNav.setOnItemSelectedListener { item ->
            val fragment: Fragment = when (item.itemId) {
                R.id.nav_home -> HomeFragment()
                R.id.nav_generate -> GenerateFragment()
                R.id.nav_review -> ReviewFragment()
                else -> HomeFragment()
            }
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit()
            true
        }

        findViewById<ImageButton>(R.id.btn_theme).setOnClickListener {
            val dialog = ThemePickerDialogFragment()
            dialog.setCurrentTheme(currentTheme.id)
            dialog.setListener(this)
            dialog.show(supportFragmentManager, "theme_picker")
        }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, HomeFragment())
                .commit()
        }
    }

    private fun applyTheme() {
        ThemeHelper.applyTheme(this, currentTheme)

        val bottomNav = findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottom_nav)
        bottomNav.setBackgroundColor(currentTheme.surface)
        bottomNav.itemIconTintList = android.content.res.ColorStateList.valueOf(currentTheme.textMuted)
        bottomNav.itemTextColor = android.content.res.ColorStateList.valueOf(currentTheme.textMuted)

        val toolbar = findViewById<View>(R.id.toolbar)
        toolbar.setBackgroundColor(currentTheme.surface)
    }

    override fun onThemeSelected(theme: com.syedali.flashquiz.theme.AppTheme) {
        currentTheme = theme
        ThemeManager.setTheme(this, theme.id)
        applyTheme()

        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, HomeFragment())
            .commit()
    }
}
