package com.moodly.moodly

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bumptech.glide.Glide
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth

class Settings : AppCompatActivity() {
    lateinit var bottomNav : BottomNavigationView
    lateinit var profileImage: ImageView
    lateinit var usernameText: TextView
    private var loadingDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_settings)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.ep)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        bottomNav = findViewById(R.id.bottom_navigation)
        profileImage = findViewById(R.id.profile_image)
        usernameText = findViewById(R.id.username_text)

        bottomNav.selectedItemId = R.id.nav_profile
        loadUserProfile()
        setupNavigations()
    }
    override fun onStart() {
        super.onStart()
        bottomNav.selectedItemId = R.id.nav_profile
    }

    override fun onResume() {
        super.onResume()
        loadUserProfile() // Reload profile when returning from EditProfile
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
    private fun loadUserProfile() {
        val prefs = getSharedPreferences(Globals.prefs, MODE_PRIVATE)
        val username = prefs.getString("username", "Username")
        val profilePicUrl = prefs.getString("profile_pic_url", "")

        usernameText.text = username

        if (!profilePicUrl.isNullOrEmpty()) {
            Glide.with(this)
                .load(profilePicUrl)
                .circleCrop()
                .into(profileImage)
        }
    }

    private fun setupNavigations()
    {
        BottomNavManager.setup(this, bottomNav)

        var backBtn = findViewById<ImageView>(R.id.back_button)
        backBtn.setOnClickListener {
            finish()
        }

        var editProfileItem = findViewById<RelativeLayout>(R.id.edit_profile_item)
        editProfileItem.setOnClickListener {
            var intent = Intent(this, EditProfile::class.java)
            startActivity(intent)
        }

        var changePasswordItem = findViewById<RelativeLayout>(R.id.change_password_item)
        changePasswordItem.setOnClickListener {
            // Navigate to EditProfile where password can be changed
            var intent = Intent(this, EditProfile::class.java)
            startActivity(intent)
        }

        var deleteAccountItem = findViewById<RelativeLayout>(R.id.delete_account_item)
        deleteAccountItem.setOnClickListener {
            showDeleteAccountConfirmation()
        }

        var logoutItem = findViewById<RelativeLayout>(R.id.logout_item)
        logoutItem.setOnClickListener {
            showLogoutConfirmation()
        }
    }

    private fun showLogoutConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Logout") { _, _ ->
                logout()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun logout() {
        // Clear SharedPreferences
        val prefs = getSharedPreferences(Globals.prefs, MODE_PRIVATE)
        prefs.edit().clear().apply()

        // Sign out from Firebase
        FirebaseAuth.getInstance().signOut()

        // Navigate to Login
        val intent = Intent(this, Login::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()

        Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show()
    }

    private fun showDeleteAccountConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Delete Account")
            .setMessage("Are you sure you want to delete your account? This action cannot be undone.\n\nAll your pins, boards, and data will be permanently deleted.")
            .setPositiveButton("Delete") { _, _ ->
                deleteAccount()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteAccount() {
        if (!Globals.isInternetAvailable(this)) {
            Toast.makeText(this, "No internet connection", Toast.LENGTH_SHORT).show()
            return
        }

        showLoadingDialog("Deleting account...")

        val prefs = getSharedPreferences(Globals.prefs, MODE_PRIVATE)
        val user_id = prefs.getString("user_id", "") ?: ""

        if (user_id.isEmpty()) {
            hideLoadingDialog()
            Toast.makeText(this, "User ID not found", Toast.LENGTH_SHORT).show()
            return
        }

        // Step 1: Delete from database first
        val query = "DELETE FROM users WHERE user_id = ?"
        val params = listOf(user_id)

        OnlineDbHelper.executeQuery(query, params) { response, error ->
            if (error != null) {
                hideLoadingDialog()
                Log.e("Settings", "Database deletion error: $error")
                Toast.makeText(this, "Failed to delete account: ${error.message}", Toast.LENGTH_LONG).show()
                return@executeQuery
            }

            if (response?.status == 1) {
                // Step 2: Delete from Firebase Auth
                val firebaseUser = FirebaseAuth.getInstance().currentUser
                if (firebaseUser != null) {
                    firebaseUser.delete()
                        .addOnCompleteListener { task ->
                            hideLoadingDialog()
                            if (task.isSuccessful) {
                                Log.d("Settings", "Firebase user deleted successfully")
                                // Step 3: Clear SharedPreferences
                                prefs.edit().clear().apply()

                                // Step 4: Sign out and navigate to Login
                                FirebaseAuth.getInstance().signOut()
                                Toast.makeText(this, "Account deleted successfully", Toast.LENGTH_SHORT).show()

                                val intent = Intent(this, Login::class.java)
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                startActivity(intent)
                                finish()
                            } else {
                                Log.e("Settings", "Firebase deletion failed", task.exception)
                                Toast.makeText(this, "Account data deleted but Firebase error: ${task.exception?.message}", Toast.LENGTH_LONG).show()

                                // Still sign out and go to login
                                prefs.edit().clear().apply()
                                FirebaseAuth.getInstance().signOut()
                                val intent = Intent(this, Login::class.java)
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                startActivity(intent)
                                finish()
                            }
                        }
                } else {
                    hideLoadingDialog()
                    Log.w("Settings", "No Firebase user found, clearing data anyway")
                    prefs.edit().clear().apply()

                    val intent = Intent(this, Login::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
            } else {
                hideLoadingDialog()
                Toast.makeText(this, "Failed to delete account from database", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showLoadingDialog(message: String) {
        val builder = AlertDialog.Builder(this)
        builder.setMessage(message)
        builder.setCancelable(false)
        loadingDialog = builder.create()
        loadingDialog?.show()
    }

    private fun hideLoadingDialog() {
        loadingDialog?.dismiss()
    }
}