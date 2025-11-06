package com.moodly.moodly

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.android.material.bottomnavigation.BottomNavigationView

class CreatePin : AppCompatActivity() {
    lateinit var bottomNav: BottomNavigationView
    private lateinit var imgUploadContainer: RelativeLayout
    private lateinit var placeholderElements: LinearLayout
    private lateinit var imagePreview: ImageView

    private val getContent = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            displaySelectedImage(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_pin)

        bottomNav = findViewById(R.id.bottom_navigation)
        imgUploadContainer = findViewById(R.id.image_upload_container)
        placeholderElements = findViewById(R.id.image_upload_placeholder_elements)
        imagePreview = findViewById(R.id.image_preview)

        bottomNav.selectedItemId = R.id.nav_create
        setupNavigations()
    }
    override fun onStart() {
        super.onStart()
        bottomNav.selectedItemId = R.id.nav_create
    }
    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        bottomNav.selectedItemId = R.id.nav_create
        bottomNav.menu.findItem(R.id.nav_create).isChecked = true
    }
    private fun displaySelectedImage(imageUri: Uri) {
        placeholderElements.visibility = View.GONE
        imagePreview.visibility = View.VISIBLE

        Glide.with(this)
            .load(imageUri)
            .into(imagePreview)

        imgUploadContainer.background = null
        imgUploadContainer.setPadding(0, 0, 0, 0)
    }

    private fun setupNavigations() {
        BottomNavManager.setup(this, bottomNav)
        val backBtn = findViewById<ImageView>(R.id.btn_back)
        backBtn.setOnClickListener {
            finish()
        }
        imgUploadContainer.setOnClickListener {
            getContent.launch("image/*")
        }
    }
}
