package com.moodly.moodly

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth

class SignUp : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sign_up)

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()

        // UI References (Changed TextView to EditText so we can read user input)
        val createAccountButton = findViewById<Button>(R.id.button_signup)
        val goToLoginButton = findViewById<TextView>(R.id.text_login_link)

        val nameInput = findViewById<EditText>(R.id.input_name)
        val emailInput = findViewById<EditText>(R.id.input_email)
        val passwordInput = findViewById<EditText>(R.id.input_password)
        val confirmPasswordInput = findViewById<EditText>(R.id.input_confirm_password)

        // Handle "Go to Login" click
        goToLoginButton.setOnClickListener {
            startActivity(Intent(this, Login::class.java))
            finish()
        }

        // Handle "Create Account" click
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

        OnlineDbHelper.executeQuery(query, params) { response, error ->
            if(error != null) {
                Log.e("SignUp", "Database Insert Error", error)
                Toast.makeText(this, "Error: ${error.message}", Toast.LENGTH_LONG).show()
            }
            if (response?.status == 1) {
                // Both Firebase and Database are updated
                Log.d("SignUp", "User saved to Supabase!")
                Toast.makeText(this, "Account created successfully.", Toast.LENGTH_LONG).show()
                // Navigate to Login
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
                    return@executeQuery
                }

                user.delete().addOnCompleteListener { deleteTask ->
                    if (deleteTask.isSuccessful) {
                        Log.d("SignUp", "Firebase user deleted after DB failure")
                        Toast.makeText(this, "Account creation rolled back due to database error.", Toast.LENGTH_LONG).show()
                        auth.signOut()
                    } else {
                        Log.e("SignUp", "Failed to delete firebase user", deleteTask.exception)
                        Toast.makeText(this, "Account created but database failed. Please try logging out and removing the account from settings, or contact support.", Toast.LENGTH_LONG).show()
                        auth.signOut()
                    }
                }
            }
        }
    }
}