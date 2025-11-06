package com.moodly.moodly

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bumptech.glide.Glide

class EditProfile : AppCompatActivity() {
    lateinit var profileImage : ImageView
    lateinit var changePictureText : TextView

    private val getContent = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
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
        profileImage = findViewById<ImageView>(R.id.profile_image)
        changePictureText = findViewById<TextView>(R.id.change_picture_text)

        setupNavigations()
    }
    private fun setupNavigations() {
        changePictureText.setOnClickListener {
            getContent.launch("image/*")
        }
    }
}