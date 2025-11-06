package com.moodly.moodly

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomnavigation.BottomNavigationView

class Settings : AppCompatActivity() {
    lateinit var bottomNav : BottomNavigationView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_settings)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.ep)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav.selectedItemId = R.id.nav_profile
        setupNavigations()
    }
    override fun onStart() {
        super.onStart()
        bottomNav.selectedItemId = R.id.nav_profile
    }
    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        bottomNav.selectedItemId = R.id.nav_profile
        bottomNav.menu.findItem(R.id.nav_profile).isChecked = true
    }
    private fun clearBottomNavSelection() {
        val menu = bottomNav.menu
        for (i in 0 until menu.size()) {
            menu.getItem(i).isChecked = false
        }
    }
    private fun setupNavigations()
    {
        BottomNavManager.setup(this, bottomNav)
        var backBtn = findViewById<ImageView>(R.id.back_button)
        backBtn.setOnClickListener {
            finish()
        }
        var ep = findViewById<RelativeLayout>(R.id.edit_profile_item)
        ep.setOnClickListener {
            var intent = Intent(this, EditProfile::class.java)
            startActivity(intent)
        }
    }
}