package com.moodly.moodly

import android.content.res.Configuration
import android.os.Bundle
import android.widget.ImageView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.google.android.material.bottomnavigation.BottomNavigationView

class BoardDetails : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ADAPTER_Pin
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_board_details)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        setupRecyclerView()
        setupNavigations()
    }
    private fun setupRecyclerView()
    {
        recyclerView = findViewById(R.id.pins_rv)
        val layoutManager = StaggeredGridLayoutManager(getSpanCount(), StaggeredGridLayoutManager.VERTICAL)
        recyclerView.layoutManager = layoutManager
        recyclerView.addItemDecoration(UTILITY_SpacingItemDecorator(2))
        val pins = listOf(
            DATA_Pin(R.drawable.placeholder_image),
            DATA_Pin(R.drawable.placeholder_image_2),
            DATA_Pin(R.drawable.placeholder_image_3),
            DATA_Pin(R.drawable.placeholder_image_4),
            DATA_Pin(R.drawable.placeholder_image_5),
            DATA_Pin(R.drawable.placeholder_image_6),
            DATA_Pin(R.drawable.placeholder_image_7),
            DATA_Pin(R.drawable.placeholder_image_8),
            DATA_Pin(R.drawable.placeholder_image_9)
        )
        adapter = ADAPTER_Pin(pins)
        recyclerView.adapter = adapter
    }
    private fun setupNavigations() {
        var backBtn = findViewById<ImageView>(R.id.btn_back)
        backBtn.setOnClickListener {
            finish()
        }
    }


    private fun getSpanCount(): Int {
        return if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 3 else 2
    }
}