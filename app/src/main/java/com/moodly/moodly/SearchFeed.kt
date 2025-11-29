package com.moodly.moodly

import android.content.res.Configuration
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.google.android.material.bottomnavigation.BottomNavigationView

class SearchFeed : AppCompatActivity() {
    lateinit var bottomNav : BottomNavigationView
    lateinit var searchEditText: EditText
    lateinit var recyclerView: RecyclerView
    lateinit var adapter: ADAPTER_Pin
    lateinit var progressBar: ProgressBar
    lateinit var nothingToShowText: TextView
    lateinit var recentSearchesSection: TextView
    lateinit var popularTagsSection: TextView
    lateinit var rvRecentSearches: RecyclerView
    lateinit var rvPopularTags: RecyclerView

    var allPins = ArrayList<DATA_Pin>()
    var filteredPins = ArrayList<DATA_Pin>()
    var user_id: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_search_feed)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        bottomNav = findViewById(R.id.bottom_navigation)
        searchEditText = findViewById(R.id.edit_text_search)
        recyclerView = findViewById(R.id.rv_ideas_grid)
        progressBar = findViewById(R.id.progress_bar)
        nothingToShowText = findViewById(R.id.nothing_to_show_text)
        recentSearchesSection = findViewById(R.id.recent_searches_title)
        popularTagsSection = findViewById(R.id.popular_tags_title)
        rvRecentSearches = findViewById(R.id.rv_recent_searches)
        rvPopularTags = findViewById(R.id.rv_popular_tags)

        // Get user_id
        val prefs = getSharedPreferences(Globals.prefs, MODE_PRIVATE)
        user_id = prefs.getString("user_id", "") ?: ""

        // Hide recent searches and popular tags initially
        recentSearchesSection.visibility = View.GONE
        popularTagsSection.visibility = View.GONE
        rvRecentSearches.visibility = View.GONE
        rvPopularTags.visibility = View.GONE

        bottomNav.selectedItemId = R.id.nav_home
        setupRecyclerView()
        setupSearch()
        setupNavigations()
        loadAllPins()
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
    private fun clearBottomNavSelection() {
        val menu = bottomNav.menu
        for (i in 0 until menu.size()) {
            menu.getItem(i).isChecked = false
        }
    }
    private fun setupNavigations()
    {
        BottomNavManager.setup(this, bottomNav)
    }

    private fun setupRecyclerView() {
        val layoutManager = StaggeredGridLayoutManager(getSpanCount(), StaggeredGridLayoutManager.VERTICAL)
        recyclerView.layoutManager = layoutManager
        recyclerView.addItemDecoration(UTILITY_SpacingItemDecorator(2))
        adapter = ADAPTER_Pin(filteredPins)
        recyclerView.adapter = adapter
    }

    private fun getSpanCount(): Int {
        return if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 3 else 2
    }

    private fun setupSearch() {
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val query = s.toString().trim()
                if (query.isEmpty()) {
                    // Show all pins
                    filteredPins.clear()
                    filteredPins.addAll(allPins)
                    adapter.notifyDataSetChanged()
                    updateUI()
                } else {
                    // Filter pins
                    performSearch(query)
                }
            }
        })
    }

    private fun loadAllPins() {
        progressBar.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        nothingToShowText.visibility = View.GONE

        // Load all pins with title, description, and keywords for searching
        val query = "SELECT pin_id, image_url, aspect_ratio, title, description, keywords FROM pins ORDER BY created_at DESC LIMIT 100"
        val params = emptyList<String>()

        OnlineDbHelper.executeQuery(query, params) { response, error ->
            if (error != null) {
                Log.e("SearchFeed", "Error loading pins: $error")
                Toast.makeText(this, "Error loading pins", Toast.LENGTH_LONG).show()
                progressBar.visibility = View.GONE
                nothingToShowText.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
                return@executeQuery
            }

            if (response?.status == 1) {
                val rows = response.data?.rows
                allPins.clear()
                filteredPins.clear()
                if (rows != null) {
                    for (row in rows) {
                        val pinId = row["pin_id"] as? String ?: ""
                        val imageUrl = row["image_url"] as? String ?: ""
                        val rawRatio = row["aspect_ratio"]
                        val aspectRatio = when (rawRatio) {
                            is Number -> rawRatio.toFloat()
                            is String -> rawRatio.toFloatOrNull() ?: 1.0f
                            else -> 1.0f
                        }
                        val title = row["title"] as? String ?: ""
                        val description = row["description"] as? String ?: ""
                        val keywords = row["keywords"] as? String ?: ""

                        if (pinId.isNotEmpty()) {
                            val pin = DATA_Pin(pinId, imageUrl, aspectRatio)
                            // Store additional data for search (we'll use a custom property if needed)
                            pin.title = title
                            pin.description = description
                            pin.keywords = keywords
                            allPins.add(pin)
                        }
                    }
                }
                filteredPins.addAll(allPins)
                adapter.notifyDataSetChanged()
                updateUI()
            }
        }
    }

    private fun performSearch(query: String) {
        filteredPins.clear()
        val lowerQuery = query.lowercase()

        for (pin in allPins) {
            val matchesTitle = pin.title?.lowercase()?.contains(lowerQuery) == true
            val matchesDescription = pin.description?.lowercase()?.contains(lowerQuery) == true
            val matchesKeywords = pin.keywords?.lowercase()?.contains(lowerQuery) == true

            if (matchesTitle || matchesDescription || matchesKeywords) {
                filteredPins.add(pin)
            }
        }

        adapter.notifyDataSetChanged()
        updateUI()
    }

    private fun updateUI() {
        progressBar.visibility = View.GONE
        if (filteredPins.isEmpty()) {
            nothingToShowText.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            nothingToShowText.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }
}