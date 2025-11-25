package com.moodly.moodly

import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.io.InputStream


class CreatePin : AppCompatActivity() {
    // UI Elements
    lateinit var bottomNav: BottomNavigationView
    private lateinit var imgUploadContainer: RelativeLayout
    private lateinit var placeholderElements: LinearLayout
    private lateinit var imagePreview: ImageView
    private lateinit var btnPublish: TextView
    private lateinit var edittextTitle: EditText
    private lateinit var edittextDescription: EditText
    private lateinit var edittextKeyword: EditText

    // Pin Data
    private var selectedImageUri: Uri? = null
    private var pinImageBase64: String? = null

    // Activity Result Launcher for image selection (Replaced displaySelectedImage call)
    private val getContent = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            selectedImageUri = uri

            // 1. Update UI (using direct setImageURI as requested)
            placeholderElements.visibility = View.GONE
            imagePreview.visibility = View.VISIBLE
            imagePreview.setImageURI(uri)

            // Set container background to black/null and remove padding for a full-bleed look
            imgUploadContainer.background = null
            imgUploadContainer.setPadding(0, 0, 0, 0)

            // 2. Convert image to Base64 string (as per Add_post example)
            try {
                // Open InputStream using contentResolver
                val ins: InputStream? = contentResolver.openInputStream(uri)
                if (ins != null) {
                    val bytes = ins.readBytes()
                    // store post image for uploading
                    pinImageBase64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    ins.close()
                }
            } catch (e: Exception) {
                Log.e("CreatePin", "Error converting image to Base64", e)
                Toast.makeText(this, "Failed to process image.", Toast.LENGTH_SHORT).show()
                pinImageBase64 = null
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_pin)

        // Initialize UI components
        bottomNav = findViewById(R.id.bottom_navigation)
        imgUploadContainer = findViewById(R.id.image_upload_container)
        placeholderElements = findViewById(R.id.image_upload_placeholder_elements)
        imagePreview = findViewById(R.id.image_preview)
        btnPublish = findViewById(R.id.btn_publish)
        edittextTitle = findViewById(R.id.edittext_title)
        edittextDescription = findViewById(R.id.edittext_description)
        edittextKeyword = findViewById(R.id.edittext_keyword)

        // Add listeners
        setupNavigations()
        setupPublishButton()
    }

    // Lifecycle setup...
    override fun onStart() {
        super.onStart()
        bottomNav.selectedItemId = R.id.nav_create
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        bottomNav.selectedItemId = R.id.nav_create
        bottomNav.menu.findItem(R.id.nav_create).isChecked = true
    }


    private fun setupNavigations() {
        BottomNavManager.setup(this, bottomNav)

        val backBtn = findViewById<ImageView>(R.id.btn_back)
        backBtn.setOnClickListener {
            finish()
        }

        // Image selection click listener (to launch getContent)
        imgUploadContainer.setOnClickListener {
            getContent.launch("image/*")
        }
    }


    private fun setupPublishButton() {
        btnPublish.setOnClickListener {
            if (validatePinData()) {
                uploadPin()
            }
        }
    }

    private fun validatePinData(): Boolean {
        if (pinImageBase64 == null) {
            Toast.makeText(this, "Please select an image first.", Toast.LENGTH_SHORT).show()
            return false
        }
        if (edittextTitle.text.isNullOrBlank()) {
            Toast.makeText(this, "Please enter a title for your pin.", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun uploadPin() {
        val title = edittextTitle.text.toString().trim()
        val description = edittextDescription.text.toString().trim()
        val keywords = edittextKeyword.text.toString().trim()

        val prefs = getSharedPreferences(Globals.prefs, MODE_PRIVATE)
        val userId = prefs.getString("user_id", null)

        if (userId.isNullOrEmpty()) {
            Toast.makeText(this, "User ID not found. Please log in.", Toast.LENGTH_SHORT).show()
            return
        }

        // Show loading dialog
        val progressDialog = android.app.ProgressDialog(this).apply {
            setTitle("Publishing Pin")
            setMessage("Uploading image...")
            setCancelable(false)
            show()
        }

        // Step 1: Upload Image
        OnlineDbHelper.uploadImage(pinImageBase64!!, compressionThresholdInBytes = 1_000_000) { imageUrl, exception ->
            if (exception != null || imageUrl == null) {
                progressDialog.dismiss()
                Toast.makeText(this, "Failed to upload image: ${exception?.message}", Toast.LENGTH_LONG).show()
                return@uploadImage
            }

            // Step 2: Insert Pin into Database
            progressDialog.setMessage("Saving pin data...")
            insertPinToDatabase(userId, title, description, keywords, imageUrl) { success, errorMsg ->
                progressDialog.dismiss()
                if (success) {
                    Toast.makeText(this, "Pin published successfully!", Toast.LENGTH_SHORT).show()
                    finish() // Close the activity after success
                } else {
                    Toast.makeText(this, "Failed to save pin: $errorMsg", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun insertPinToDatabase(
        userId: String,
        title: String,
        description: String,
        keywords: String,
        imageUrl: String,
        onComplete: (Boolean, String?) -> Unit
    ) {

        val aspectRatio = 1.0

        val query = """
            INSERT INTO pins (user_id, title, description, keywords, image_url, aspect_ratio) 
            VALUES (?, ?, ?, ?, ?, ?)
        """.trimIndent()

        val params = listOf(userId, title, description, keywords, imageUrl, aspectRatio)

        OnlineDbHelper.executeQuery(query, params) { response, exception ->
            if (exception != null) {
                onComplete(false, exception.message)
                return@executeQuery
            }

            if (response?.status == 1 && response.error == null) {
                onComplete(true, null)
            } else {
                val error = response?.error?.message ?: "Unknown database error"
                onComplete(false, error)
            }
        }
    }
}