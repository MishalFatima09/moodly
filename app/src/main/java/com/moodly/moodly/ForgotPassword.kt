package com.moodly.moodly

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth

class ForgotPassword : AppCompatActivity() {
    lateinit var backImage: ImageView
    lateinit var backText: TextView
    lateinit var emailField : EditText
    lateinit var sendLinkButton : Button
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_forgot_password)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.forgotpass)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        backImage = findViewById(R.id.back_arrow)
        backText = findViewById(R.id.back_text)
        emailField = findViewById(R.id.email_input)
        sendLinkButton = findViewById(R.id.send_reset_link_button)

        setNavigation()
    }

    private fun setNavigation() {
        backImage.setOnClickListener {
            finish()
        }

        backText.setOnClickListener {
            finish()
        }

        sendLinkButton.setOnClickListener {
            sendResetLink()
        }
    }
    private fun sendResetLink() {
        val email = emailField.text.toString().trim()

        if (email.isEmpty()) {
            Toast.makeText(this, "Please enter your registered email", Toast.LENGTH_SHORT).show()
            return
        }

        sendLinkButton.isEnabled = false
        sendLinkButton.text = "Sending..."

        FirebaseAuth.getInstance().sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Toast.makeText(this, "Link sent on registered email.", Toast.LENGTH_LONG).show()
                    finish()
                } else {
                    sendLinkButton.isEnabled = true
                    sendLinkButton.text = "Send Reset Link"
                    val errorMsg = task.exception?.message ?: "Failed to send email"
                    Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
                }
            }
    }
}