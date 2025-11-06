package com.moodly.moodly

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.flexbox.FlexboxLayout
import com.google.android.material.bottomnavigation.BottomNavigationView

class PinDetails : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_pin_details)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        setupNavigations()

        //flex box container that stores keywords
        var keywords = ArrayList<String>()
        keywords.add("Kuromi")
        keywords.add("Sanrio")
        keywords.add("Nagasaki")
        keywords.add("Hiroshimia")
        keywords.add("Zhong xing")
        val keywordsContainer = findViewById<FlexboxLayout>(R.id.keywords_container)
        keywordsContainer.removeAllViews()
        for (keyword in keywords) {
            val chipView = LayoutInflater.from(this)
                .inflate(R.layout.item_keyword, keywordsContainer, false) as TextView
            chipView.text = "#$keyword"
            keywordsContainer.addView(chipView)
        }
    }
    private fun setupNavigations()
    {
        var backBtn = findViewById<ImageView>(R.id.btn_back)
        backBtn.setOnClickListener {
            finish()
        }
    }
}