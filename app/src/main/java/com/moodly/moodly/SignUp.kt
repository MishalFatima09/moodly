package com.moodly.moodly

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.MotionEvent
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging

class SignUp : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    lateinit var createAccountButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sign_up)

        auth = FirebaseAuth.getInstance()
        createAccountButton = findViewById<Button>(R.id.button_signup)
        val goToLoginButton = findViewById<TextView>(R.id.text_login_link)
        val nameInput = findViewById<EditText>(R.id.input_name)
        val emailInput = findViewById<EditText>(R.id.input_email)
        val passwordInput = findViewById<EditText>(R.id.input_password)
        val confirmPasswordInput = findViewById<EditText>(R.id.input_confirm_password)
        setupPasswordVisibility(passwordInput)
        setupPasswordVisibility(confirmPasswordInput)

        goToLoginButton.setOnClickListener {
            startActivity(Intent(this, Login::class.java))
            finish()
        }

        createAccountButton.setOnClickListener {
            val fullName = nameInput.text.toString().trim()
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()
            val confirmPass = confirmPasswordInput.text.toString().trim()

            // Validation
            if (fullName.isEmpty() || email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password != confirmPass) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password.length < 6) {
                Toast.makeText(this, "Password must be at least 6 chars", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Create User in Firebase
            createAccountButton.isEnabled = false
            createAccountButton.text = "Creating Account..."
            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        val firebaseUser = auth.currentUser
                        val uid = firebaseUser?.uid

                        if (uid != null) {
                            // Success in Firebase -> save to Supabase
                            saveUserToBackend(uid, email, fullName)
                        }
                    } else {
                        // Firebase Failed
                        createAccountButton.isEnabled = true
                        createAccountButton.text = "Create Account"
                        Log.e("SignUp", "Firebase Auth Failed", task.exception)
                        Toast.makeText(this, "Sign Up Failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                    }
                }
        }
    }

    private fun saveUserToBackend(uid: String, email: String, fullName: String) {
        // Generate a username from email
        // john.doe@gmail.com -> john.doe_4821
        val username = "${email.split("@")[0]}_${(1000..9999).random()}"

        val query = "INSERT INTO users (user_id, username, email, full_name) VALUES (?, ?, ?, ?)"
        val params = listOf(uid, username, email, fullName)

        createAccountButton.text = "Saving info..."
        OnlineDbHelper.executeQuery(query, params) { response, error ->
            if(error != null) {
                Log.e("SignUp", "Database Insert Error", error)
                Toast.makeText(this, "Error: ${error.message}", Toast.LENGTH_LONG).show()
            }
            if (response?.status == 1) {
                // Both Firebase and Database are updated
                Log.d("SignUp", "User saved to Supabase")
                Toast.makeText(this, "Account created successfully.", Toast.LENGTH_LONG).show()

                // Save to user data to prefs
                val prefs = getSharedPreferences(Globals.prefs, Context.MODE_PRIVATE)
                val editor = prefs.edit()
                editor.putString("user_id", uid)
                editor.putString("username", username)
                editor.putString("email", email)
                editor.putString("full_name", fullName)
                editor.putString("phone_number", "")
                editor.putString("profile_pic_url", "")
                editor.apply()

                syncFcmToken()
                val intent = Intent(this, Login::class.java)
                intent.putExtra("email", email)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            } else {
                // DB insert failed -> try to delete Firebase user and handle result
                val user = auth.currentUser
                if (user == null) {
                    Log.e("SignUp", "Database Insert Failed and no firebase user to delete: ${response?.error?.message ?: error?.message}")
                    Toast.makeText(this, "Account created but database failed.", Toast.LENGTH_LONG).show()
                    createAccountButton.text = "Create Account"
                    createAccountButton.isEnabled = true
                    return@executeQuery
                }

                user.delete().addOnCompleteListener { deleteTask ->
                    if (deleteTask.isSuccessful) {
                        Log.d("SignUp", "Firebase user deleted after DB failure")
                        Toast.makeText(this, "Account creation rolled back due to database error.", Toast.LENGTH_LONG).show()
                        createAccountButton.text = "Create Account"
                        createAccountButton.isEnabled = true
                        auth.signOut()
                    } else {
                        Log.e("SignUp", "Failed to delete firebase user", deleteTask.exception)
                        Toast.makeText(this, "Account created but database failed. Please try logging out and removing the account from settings, or contact support.", Toast.LENGTH_LONG).show()
                        createAccountButton.text = "Create Account"
                        createAccountButton.isEnabled = true
                        auth.signOut()
                    }
                }
            }
        }
    }
    @SuppressLint("ClickableViewAccessibility")
    fun setupPasswordVisibility(editText: EditText) {
        editText.setOnTouchListener { v, event ->
            val DRAWABLE_RIGHT = 2

            // Check if the click action is "UP" (finger lifted)
            if (event.action == MotionEvent.ACTION_UP) {
                // Check if touch point is within the bounds of the right drawable
                if (event.rawX >= (editText.right - editText.compoundPaddingEnd)) {

                    // Toggle Logic
                    val selectionStart = editText.selectionStart // Save cursor position

                    if (editText.inputType == (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)) {
                        // Show Password
                        editText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    } else {
                        // Hide Password
                        editText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                    }
                    editText.setSelection(selectionStart)
                    return@setOnTouchListener true
                }
            }
            // Return false to let normal typing clicks pass through
            return@setOnTouchListener false
        }
    }
    private fun syncFcmToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                Log.d("Login", "Fetching FCM Token: $token")
                // Use the static helper from your Service
                MyFirebaseMessagingService.sendTokenToServer(token, this)
            } else {
                Log.e("Login", "Fetching FCM Token failed", task.exception)
            }
        }
    }
}