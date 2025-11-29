package com.moodly.moodly

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
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
    lateinit var user_id: String
    var pins = ArrayList<DATA_Pin>()

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
    }
    override fun onStart() {
        super.onStart()
        bottomNav.selectedItemId = R.id.nav_profile
        loadPins()
    }

    override fun onResume() {
        super.onResume()
        loadUserProfile() // Reload profile data when returning from EditProfile
        loadPins() // Reload pins when returning to profile
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
    }

    private fun setupRecyclerView() {
        val layoutManager = StaggeredGridLayoutManager(getSpanCount(), StaggeredGridLayoutManager.VERTICAL)
        recyclerView.layoutManager = layoutManager
        recyclerView.addItemDecoration(UTILITY_SpacingItemDecorator(2))
        adapter = ADAPTER_Pin(pins)
        recyclerView.adapter = adapter
    }

    private fun getSpanCount(): Int {
        return if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 3 else 2
    }

    private fun loadPins() {
        progressBar.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        nothingToShowText.visibility = View.GONE

        // Changed query: WHERE user_id = ? (only show current user's pins)
        val query = "SELECT pin_id, image_url, aspect_ratio FROM pins WHERE user_id = ? ORDER BY created_at DESC LIMIT 100"
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
}