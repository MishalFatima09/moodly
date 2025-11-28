package com.moodly.moodly

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.google.android.material.bottomnavigation.BottomNavigationView

class HomeScreen : AppCompatActivity() {
    lateinit var recyclerView: RecyclerView
    lateinit var adapter: ADAPTER_Pin
    lateinit var bottomNav : BottomNavigationView
    lateinit var searchbar : EditText
    lateinit var nothingToShowText : TextView
    lateinit var progressBar : ProgressBar
    lateinit var user_id: String
    lateinit var prefs: SharedPreferences
    var pins = ArrayList<DATA_Pin>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home_screen)

        // Request notification permission for Android 13 and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        searchbar = findViewById<EditText>(R.id.search_bar)
        nothingToShowText = findViewById<TextView>(R.id.nothing_to_show_text)
        progressBar = findViewById<ProgressBar>(R.id.progress_bar)
        prefs = getSharedPreferences(Globals.prefs, MODE_PRIVATE)

        user_id = prefs.getString("user_id", "") ?: ""
        if(user_id.isEmpty())
        {
            Toast.makeText(this, "User ID not found. Please log in again.", Toast.LENGTH_LONG).show()
            val intent = Intent(this, Login::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            finish()
            return
        }

        setupRecyclerView()
        setupNavigations()
    }
    override fun onStart() {
        super.onStart()
        bottomNav.selectedItemId = R.id.nav_home
        loadPins()
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

    private fun loadPins() {
        progressBar.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        nothingToShowText.visibility = View.GONE

        val query = "SELECT pin_id, image_url, aspect_ratio FROM pins WHERE user_id != ? ORDER BY created_at DESC LIMIT 100"
        val params = listOf(user_id)

        OnlineDbHelper.executeQuery(query, params) { response, error ->
            if (error != null) {
                Log.e("HomeScreen", "Error loading pins: $error")
                Toast.makeText(this, "Error loading pins ", Toast.LENGTH_LONG).show()
                progressBar.visibility = View.GONE
                nothingToShowText.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
                return@executeQuery
            }

            if (response?.status == 1) {
                val rows = response.data?.rows
                pins.clear()
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

                        if (pinId.isNotEmpty()) {
                            pins.add(DATA_Pin(pinId, imageUrl, aspectRatio))
                        }
                    }
                }
                adapter.notifyDataSetChanged()

                progressBar.visibility = View.GONE
                if (pins.isEmpty()) {
                    nothingToShowText.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                } else {
                    nothingToShowText.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                }
            }
        }
    }
}
