package com.moodly.moodly

import android.app.AlertDialog
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.Button
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
import com.bumptech.glide.Glide
import com.google.android.material.bottomnavigation.BottomNavigationView
import de.hdodenhof.circleimageview.CircleImageView

class Profile : AppCompatActivity() {
    lateinit var bottomNav : BottomNavigationView
    lateinit var profileImage: CircleImageView
    lateinit var usernameText: TextView
    lateinit var emailText: TextView
    lateinit var recyclerView: RecyclerView
    lateinit var adapter: ADAPTER_Pin
    lateinit var progressBar: ProgressBar
    lateinit var nothingToShowText: TextView
    lateinit var searchInput: EditText
    lateinit var createdTab: TextView
    lateinit var savedTab: TextView
    lateinit var user_id: String
    var allPins = ArrayList<DATA_Pin>() // All pins (created or saved)
    var filteredPins = ArrayList<DATA_Pin>() // Filtered pins for display
    var currentTab = "created" // Track current tab: "created" or "saved"
    var currentSortOrder = "newest" // Track sort order: "newest", "oldest", "title"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_profile)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        profileImage = findViewById(R.id.profile_image)
        usernameText = findViewById(R.id.username_text)
        emailText = findViewById(R.id.email_text)
        recyclerView = findViewById(R.id.recycler_view_posts)
        progressBar = findViewById(R.id.progress_bar)
        nothingToShowText = findViewById(R.id.nothing_to_show_text)
        searchInput = findViewById(R.id.search_input)
        createdTab = findViewById(R.id.created_tab)
        savedTab = findViewById(R.id.saved_tab)

        val prefs = getSharedPreferences(Globals.prefs, MODE_PRIVATE)
        user_id = prefs.getString("user_id", "") ?: ""

        if(user_id.isEmpty()) {
            Toast.makeText(this, "User ID not found. Please log in again.", Toast.LENGTH_LONG).show()
            val intent = Intent(this, Login::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            finish()
            return
        }

        bottomNav.selectedItemId = R.id.nav_profile
        loadUserProfile()
        setupRecyclerView()
        setupNavigations()
        setupTabs()
        setupSearch()
    }
    override fun onStart() {
        super.onStart()
        bottomNav.selectedItemId = R.id.nav_profile
        loadPinsForCurrentTab()
    }

    override fun onResume() {
        super.onResume()
        loadUserProfile() // Reload profile data when returning from EditProfile
        loadPinsForCurrentTab() // Reload pins when returning to profile
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        bottomNav.selectedItemId = R.id.nav_profile
        bottomNav.menu.findItem(R.id.nav_profile).isChecked = true
    }
    private fun loadUserProfile() {
        val prefs = getSharedPreferences(Globals.prefs, MODE_PRIVATE)
        val username = prefs.getString("username", "Username")
        val email = prefs.getString("email", "email@example.com")
        val profilePicUrl = prefs.getString("profile_pic_url", "")

        // Set username and email
        usernameText.text = username
        emailText.text = email

        // Load profile picture using Glide
        if (!profilePicUrl.isNullOrEmpty()) {
            Glide.with(this)
                .load(profilePicUrl)
                .placeholder(R.drawable.dp)
                .error(R.drawable.dp)
                .into(profileImage)
        } else {
            profileImage.setImageResource(R.drawable.dp)
        }
    }

    private fun setupNavigations()
    {
        BottomNavManager.setup(this, bottomNav)
        var editProfileBtn = findViewById<Button>(R.id.edit_profile_button)
        editProfileBtn.setOnClickListener {
            val intent = Intent(this, EditProfile::class.java)
            startActivity(intent)
        }
        var moreOptions = findViewById<ImageView>(R.id.more_options)
        moreOptions.setOnClickListener {
            val intent = Intent(this, Settings::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            startActivity(intent)
        }

        var filterButton = findViewById<ImageView>(R.id.filter_button)
        filterButton.setOnClickListener {
            showFilterDialog()
        }

        var addButton = findViewById<ImageView>(R.id.add_button)
        addButton.setOnClickListener {
            val intent = Intent(this, CreatePin::class.java)
            startActivity(intent)
        }
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

    private fun setupTabs() {
        createdTab.setOnClickListener {
            if (currentTab != "created") {
                currentTab = "created"
                currentSortOrder = "newest" // Reset sort order
                updateTabUI()
                searchInput.setText("") // Clear search
                loadPinsForCurrentTab()
            }
        }

        savedTab.setOnClickListener {
            if (currentTab != "saved") {
                currentTab = "saved"
                currentSortOrder = "newest" // Reset sort order
                updateTabUI()
                searchInput.setText("") // Clear search
                loadPinsForCurrentTab()
            }
        }
    }

    private fun updateTabUI() {
        if (currentTab == "created") {
            createdTab.setBackgroundResource(R.drawable.tab_selected_bg)
            createdTab.setTextColor(resources.getColor(android.R.color.white, null))
            savedTab.setBackgroundResource(android.R.color.transparent)
            savedTab.setTextColor(resources.getColor(android.R.color.darker_gray, null))
        } else {
            savedTab.setBackgroundResource(R.drawable.tab_selected_bg)
            savedTab.setTextColor(resources.getColor(android.R.color.white, null))
            createdTab.setBackgroundResource(android.R.color.transparent)
            createdTab.setTextColor(resources.getColor(android.R.color.darker_gray, null))
        }
    }

    private fun setupSearch() {
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val query = s.toString().trim()
                filterPins(query)
            }
        })
    }

    private fun loadPinsForCurrentTab() {
        if (currentTab == "created") {
            loadCreatedPins()
        } else {
            loadSavedPins()
        }
    }

    private fun loadCreatedPins() {
        progressBar.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        nothingToShowText.visibility = View.GONE

        // Load pins with title, description, keywords for search
        val query = "SELECT pin_id, image_url, aspect_ratio, title, description, keywords FROM pins WHERE user_id = ? ORDER BY created_at DESC LIMIT 100"
        val params = listOf(user_id)

        OnlineDbHelper.executeQuery(query, params) { response, error ->
            if (error != null) {
                Log.e("Profile", "Error loading pins: $error")
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
                            pin.title = title
                            pin.description = description
                            pin.keywords = keywords
                            allPins.add(pin)
                        }
                    }
                }
                filteredPins.addAll(allPins)
                sortPins() // Apply current sort order
                adapter.notifyDataSetChanged()

                progressBar.visibility = View.GONE
                if (filteredPins.isEmpty()) {
                    nothingToShowText.text = "You haven't created any pins yet"
                    nothingToShowText.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                } else {
                    nothingToShowText.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun loadSavedPins() {
        progressBar.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        nothingToShowText.visibility = View.GONE

        // Load saved pins from board_pins joined with pins table
        val query = """
            SELECT DISTINCT p.pin_id, p.image_url, p.aspect_ratio, p.title, p.description, p.keywords
            FROM pins p
            INNER JOIN board_pins bp ON p.pin_id = bp.pin_id
            INNER JOIN boards b ON bp.board_id = b.board_id
            WHERE b.user_id = ?
            ORDER BY bp.created_at DESC
            LIMIT 100
        """.trimIndent()
        val params = listOf(user_id)

        OnlineDbHelper.executeQuery(query, params) { response, error ->
            if (error != null) {
                Log.e("Profile", "Error loading saved pins: $error")
                Toast.makeText(this, "Error loading saved pins", Toast.LENGTH_LONG).show()
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
                            pin.title = title
                            pin.description = description
                            pin.keywords = keywords
                            allPins.add(pin)
                        }
                    }
                }
                filteredPins.addAll(allPins)
                sortPins() // Apply current sort order
                adapter.notifyDataSetChanged()

                progressBar.visibility = View.GONE
                if (filteredPins.isEmpty()) {
                    nothingToShowText.text = "You haven't saved any pins yet"
                    nothingToShowText.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                } else {
                    nothingToShowText.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun filterPins(query: String) {
        filteredPins.clear()
        if (query.isEmpty()) {
            // Show all pins
            filteredPins.addAll(allPins)
        } else {
            // Filter pins by title, description, keywords
            val lowerQuery = query.lowercase()
            for (pin in allPins) {
                val matchesTitle = pin.title?.lowercase()?.contains(lowerQuery) == true
                val matchesDescription = pin.description?.lowercase()?.contains(lowerQuery) == true
                val matchesKeywords = pin.keywords?.lowercase()?.contains(lowerQuery) == true

                if (matchesTitle || matchesDescription || matchesKeywords) {
                    filteredPins.add(pin)
                }
            }
        }

        // Apply current sort order
        sortPins()

        adapter.notifyDataSetChanged()

        // Update UI
        if (filteredPins.isEmpty()) {
            nothingToShowText.text = if (query.isEmpty()) {
                if (currentTab == "created") "You haven't created any pins yet" else "You haven't saved any pins yet"
            } else {
                "No pins found matching \"$query\""
            }
            nothingToShowText.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            nothingToShowText.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }

    private fun showFilterDialog() {
        val options = arrayOf("Newest First", "Oldest First", "By Title (A-Z)")
        val currentSelection = when (currentSortOrder) {
            "newest" -> 0
            "oldest" -> 1
            "title" -> 2
            else -> 0
        }

        AlertDialog.Builder(this)
            .setTitle("Sort By")
            .setSingleChoiceItems(options, currentSelection) { dialog, which ->
                currentSortOrder = when (which) {
                    0 -> "newest"
                    1 -> "oldest"
                    2 -> "title"
                    else -> "newest"
                }
                sortPins()
                adapter.notifyDataSetChanged()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun sortPins() {
        when (currentSortOrder) {
            "newest" -> {
                // Already sorted by created_at DESC from query, just reverse if needed
                // Since we load with ORDER BY created_at DESC, newest is default
            }
            "oldest" -> {
                filteredPins.reverse()
            }
            "title" -> {
                filteredPins.sortBy { it.title?.lowercase() ?: "" }
            }
        }
    }
}