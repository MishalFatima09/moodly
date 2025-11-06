package com.moodly.moodly

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.google.android.material.bottomnavigation.BottomNavigationView

class HomeScreen : AppCompatActivity() {
    lateinit var recyclerView: RecyclerView
    lateinit var adapter: ADAPTER_Pin
    lateinit var bottomNav : BottomNavigationView
    lateinit var searchbar : EditText
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home_screen)

        bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        searchbar = findViewById<EditText>(R.id.search_bar)

        bottomNav.selectedItemId = R.id.nav_home

        setupRecyclerView()
        setupNavigations()
    }
    override fun onStart() {
        super.onStart()
        bottomNav.selectedItemId = R.id.nav_home
    }
    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        bottomNav.selectedItemId = R.id.nav_home
        bottomNav.menu.findItem(R.id.nav_home).isChecked = true
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
    private fun setupNavigations()
    {
        BottomNavManager.setup(this, bottomNav)
        searchbar.setOnClickListener {
            val intent = Intent(this, SearchFeed::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            startActivity(intent)
        }
    }
    private fun getSpanCount(): Int {
        return if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 3 else 2
    }
}
