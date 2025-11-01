package com.moodly.moodly

import android.content.res.Configuration
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.google.android.material.bottomnavigation.BottomNavigationView

class HomeScreen : AppCompatActivity() {
    lateinit var recyclerView: RecyclerView
    lateinit var adapter: ADAPTER_Pin
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home_screen)

        setupRecyclerView()
        var bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav.selectedItemId = R.id.nav_home
    }
    private fun setupRecyclerView()
    {
        recyclerView = findViewById(R.id.recycler_view_posts)
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
    private fun getSpanCount(): Int {
        return if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 3 else 2
    }
}
