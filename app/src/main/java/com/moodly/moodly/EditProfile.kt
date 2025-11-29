package com.moodly.moodly

import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.exifinterface.media.ExifInterface
import com.bumptech.glide.Glide
import java.io.ByteArrayOutputStream

class EditProfile : AppCompatActivity() {
    lateinit var profileImage : ImageView
    lateinit var changePictureText : TextView
    lateinit var usernameInput: EditText
    lateinit var nicknameInput: EditText
    lateinit var emailInput: EditText
    lateinit var phoneInput: EditText
    lateinit var passwordInput: EditText
    lateinit var updateButton: Button

    private var selectedImageUri: Uri? = null
    private var user_id: String = ""
    private var loadingDialog: AlertDialog? = null

    private val getContent = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            selectedImageUri = uri
            Glide.with(this)
                .load(uri)
                .circleCrop()
                .into(profileImage)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_edit_profile)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialize views
        profileImage = findViewById(R.id.profile_image)
        changePictureText = findViewById(R.id.change_picture_text)
        usernameInput = findViewById(R.id.username_input)
        nicknameInput = findViewById(R.id.nickname_input)
        emailInput = findViewById(R.id.email_input)
        phoneInput = findViewById(R.id.phone_input)
        passwordInput = findViewById(R.id.password_input)
        updateButton = findViewById(R.id.update_button)

        // Load current user data
        val prefs = getSharedPreferences(Globals.prefs, MODE_PRIVATE)
        user_id = prefs.getString("user_id", "") ?: ""

        if(user_id.isEmpty()) {
            Toast.makeText(this, "User ID not found. Please log in again.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        loadUserData()
        setupNavigations()
    }
    private fun setupNavigations() {
        changePictureText.setOnClickListener {
            getContent.launch("image/*")
        }

        updateButton.setOnClickListener {
            updateProfile()
        }
    }

    private fun loadUserData() {
        val prefs = getSharedPreferences(Globals.prefs, MODE_PRIVATE)
        val username = prefs.getString("username", "")
        val fullName = prefs.getString("full_name", "")
        val email = prefs.getString("email", "")
        val phone = prefs.getString("phone_number", "")
        val profilePicUrl = prefs.getString("profile_pic_url", "")

        usernameInput.setText(username)
        nicknameInput.setText(fullName)
        emailInput.setText(email)
        phoneInput.setText(phone)

        // Load profile picture
        if (!profilePicUrl.isNullOrEmpty()) {
            Glide.with(this)
                .load(profilePicUrl)
                .circleCrop()
                .into(profileImage)
        }
    }

    private fun updateProfile() {
        if (!Globals.isInternetAvailable(this)) {
            Toast.makeText(this, "No internet connection", Toast.LENGTH_SHORT).show()
            return
        }

        val username = usernameInput.text.toString().trim()
        val fullName = nicknameInput.text.toString().trim()
        val email = emailInput.text.toString().trim()
        val phone = phoneInput.text.toString().trim()
        val newPassword = passwordInput.text.toString().trim()

        // Validation
        if (username.isEmpty() || fullName.isEmpty() || email.isEmpty()) {
            Toast.makeText(this, "Username, nickname and email are required", Toast.LENGTH_SHORT).show()
            return
        }

        showLoadingDialog()

        // Check if user selected a new profile picture
        if (selectedImageUri != null) {
            // Upload new profile picture first
            val base64Image = processImageWithRotation(selectedImageUri!!)
            if (base64Image == null) {
                hideLoadingDialog()
                Toast.makeText(this, "Failed to process image", Toast.LENGTH_SHORT).show()
                return
            }

            OnlineDbHelper.uploadImage(base64Image) { imageUrl, exception ->
                if (exception != null || imageUrl == null) {
                    hideLoadingDialog()
                    Toast.makeText(this, "Image upload failed: ${exception?.message}", Toast.LENGTH_LONG).show()
                    return@uploadImage
                }

                // Image uploaded successfully, now update user data with new image URL
                updateUserInDatabase(username, fullName, email, phone, imageUrl, newPassword)
            }
        } else {
            // No new image, just update other fields (keep existing profile_pic_url)
            val prefs = getSharedPreferences(Globals.prefs, MODE_PRIVATE)
            val existingProfilePicUrl = prefs.getString("profile_pic_url", "") ?: ""
            updateUserInDatabase(username, fullName, email, phone, existingProfilePicUrl, newPassword)
        }
    }

    private fun updateUserInDatabase(username: String, fullName: String, email: String, phone: String, profilePicUrl: String, newPassword: String) {
        // Build UPDATE query
        val query = if (newPassword.isNotEmpty()) {
            // TODO: Also update Firebase password if needed
            "UPDATE users SET username = ?, full_name = ?, email = ?, phone_number = ?, profile_pic_url = ? WHERE user_id = ?"
        } else {
            "UPDATE users SET username = ?, full_name = ?, email = ?, phone_number = ?, profile_pic_url = ? WHERE user_id = ?"
        }

        val params = listOf(username, fullName, email, phone, profilePicUrl, user_id)

        OnlineDbHelper.executeQuery(query, params) { response, error ->
            hideLoadingDialog()

            if (error != null) {
                Log.e("EditProfile", "Database update error: $error")
                Toast.makeText(this, "Update failed: ${error.message}", Toast.LENGTH_LONG).show()
                return@executeQuery
            }

            if (response?.status == 1) {
                // Update SharedPreferences
                val prefs = getSharedPreferences(Globals.prefs, MODE_PRIVATE)
                prefs.edit().apply {
                    putString("username", username)
                    putString("full_name", fullName)
                    putString("email", email)
                    putString("phone_number", phone)
                    putString("profile_pic_url", profilePicUrl)
                    apply()
                }

                Toast.makeText(this, "Profile updated successfully!", Toast.LENGTH_SHORT).show()
                finish() // Go back to profile
            } else {
                Toast.makeText(this, "Update failed", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun processImageWithRotation(uri: Uri): String? {
        return try {
            val contentResolver = applicationContext.contentResolver

            // 1. Read EXIF Data to find orientation
            var inputStream = contentResolver.openInputStream(uri) ?: return null
            val exif = ExifInterface(inputStream)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            inputStream.close()

            // 2. Decode the Bitmap
            inputStream = contentResolver.openInputStream(uri) ?: return null
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            // 3. Determine rotation in degrees
            val rotationInDegrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }

            // 4. Rotate the Bitmap if necessary
            val finalBitmap = if (rotationInDegrees != 0) {
                val matrix = Matrix()
                matrix.postRotate(rotationInDegrees.toFloat())
                Bitmap.createBitmap(
                    originalBitmap, 0, 0,
                    originalBitmap.width, originalBitmap.height,
                    matrix, true
                )
            } else {
                originalBitmap
            }

            // 5. Compress to JPEG and convert to Base64
            val outputStream = ByteArrayOutputStream()
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
            val bytes = outputStream.toByteArray()

            Base64.encodeToString(bytes, Base64.NO_WRAP)

        } catch (e: Exception) {
            Log.e("EditProfile", "Error rotating image", e)
            null
        }
    }

    private fun showLoadingDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setMessage("Updating profile...")
        builder.setCancelable(false)
        loadingDialog = builder.create()
        loadingDialog?.show()
    }

    private fun hideLoadingDialog() {
        loadingDialog?.dismiss()
    }
}