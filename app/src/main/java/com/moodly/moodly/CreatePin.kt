package com.moodly.moodly

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
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
import com.google.android.flexbox.FlexboxLayout
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.io.ByteArrayOutputStream
import java.io.InputStream


class CreatePin : AppCompatActivity() {
    // UI Elements
    lateinit var bottomNav: BottomNavigationView
    private lateinit var imgUploadContainer: RelativeLayout
    private lateinit var placeholderElements: LinearLayout
    private lateinit var imagePreview: ImageView
    private lateinit var btnPublish: TextView
    private lateinit var btnAddKeyword: ImageView
    private lateinit var edittextTitle: EditText
    private lateinit var edittextDescription: EditText
    private lateinit var edittextKeyword: EditText
    private lateinit var keywordsContainer: FlexboxLayout
    //TODO: Save to board drop box
    var keywords = ArrayList<String>()

    // Pin Data
    private var selectedImageUri: Uri? = null
    private var pinImageBase64: String? = null

    // Activity Result Launcher for image selection (Replaced displaySelectedImage call)
    private val getContent = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            selectedImageUri = uri

            // 1. Update UI
            placeholderElements.visibility = View.GONE
            imagePreview.visibility = View.VISIBLE
            imagePreview.setImageURI(uri)

            imgUploadContainer.background = null
            imgUploadContainer.setPadding(0, 0, 0, 0)

            // 2. Process Image (Rotate & Convert to Base64)
            pinImageBase64 = processImageWithRotation(uri)

            if (pinImageBase64 == null) {
                Toast.makeText(this, "Failed to process image.", Toast.LENGTH_SHORT).show()
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
        btnAddKeyword = findViewById(R.id.btn_add_keyword)
        edittextTitle = findViewById(R.id.edittext_title)
        edittextDescription = findViewById(R.id.edittext_description)
        edittextKeyword = findViewById(R.id.edittext_keyword)
        keywordsContainer= findViewById(R.id.keywords_container)

        bottomNav.selectedItemId = R.id.nav_create

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

        btnAddKeyword.setOnClickListener {
            addKeyword()
        }
    }
    private fun addKeyword() {
        val keywordText = edittextKeyword.text.toString().trim()
        if (keywordText.isNotEmpty()) {
            keywords.add(keywordText)
            val chipView = layoutInflater.inflate(R.layout.item_keyword, keywordsContainer, false) as TextView
            chipView.text = "#$keywordText"
            keywordsContainer.addView(chipView)
            edittextKeyword.text.clear()
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
        OnlineDbHelper.uploadImage(pinImageBase64!!) { imageUrl, exception ->
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
        keywords: List<String>,
        imageUrl: String,
        onComplete: (Boolean, String?) -> Unit
    ) {
        // Join list into a single comma seperated string
        val finalKeywords = keywords.joinToString("|")

        val aspectRatio = try {
            val decodedBytes = android.util.Base64.decode(pinImageBase64, android.util.Base64.DEFAULT)
            val options = android.graphics.BitmapFactory.Options()
            options.inJustDecodeBounds = true
            android.graphics.BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size, options)

            if (options.outWidth > 0) {
                options.outHeight.toFloat() / options.outWidth.toFloat()
            } else {
                1.0f
            }
        } catch (e: Exception) {
            1.0f
        }
        val query = """
            INSERT INTO pins (user_id, title, description, keywords, image_url, aspect_ratio) 
            VALUES (?, ?, ?, ?, ?, ?)
        """.trimIndent()

        val params = listOf(userId, title, description, finalKeywords, imageUrl, aspectRatio)

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
            Log.e("CreatePin", "Error rotating image", e)
            null
        }
    }
}