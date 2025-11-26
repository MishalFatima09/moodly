package com.moodly.moodly

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth

class Login : AppCompatActivity() {

    private lateinit var signupPrompt: TextView
    private lateinit var loginButton: Button
    private lateinit var forgot: TextView
    private lateinit var emailField: EditText
    private lateinit var passwordField: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_login)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        signupPrompt = findViewById(R.id.text_login_link)
        loginButton = findViewById(R.id.button_login)
        forgot = findViewById(R.id.forgot)
        emailField = findViewById(R.id.input_email)
        passwordField = findViewById(R.id.input_password)

        setupPasswordVisibility(passwordField)

        setNavigation()
        val email = intent.getStringExtra("email")
        if (email != null) {
            emailField.setText(email)
        }
    }

    override fun onStart() {
        super.onStart()
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            // User is already logged in, navigate to HomeScreen
            val intent = Intent(this, HomeScreen::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    private fun setNavigation() {
        signupPrompt.setOnClickListener {
            val intent = Intent(this, SignUp::class.java)
            startActivity(intent)
            finish()
        }
        loginButton.setOnClickListener {
            login()
        }
        forgot.setOnClickListener {
            val intent = Intent(this, ForgotPassword::class.java)
            startActivity(intent)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun login() {
        val email = emailField.text.toString().trim()
        val password = passwordField.text.toString().trim()

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
            return
        }

        hideKeyboard()
        loginButton.isEnabled = false
        loginButton.text = "Authenticating..."

        // Authenticate with Firebase
        FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = FirebaseAuth.getInstance().currentUser
                    if (user != null) {
                        // Fetch Profile from SQL
                        fetchAndSaveUserData(user.uid)
                    }
                } else {
                    loginButton.isEnabled = true
                    loginButton.text = "Login"
                    val errorMessage = task.exception?.message ?: "Login failed"
                    Log.e("Login", errorMessage)
                    Toast.makeText(this, "Invalid credentials", Toast.LENGTH_SHORT).show()
                }
            }
    }

    @SuppressLint("SetTextI18n")
    private fun fetchAndSaveUserData(userId: String) {
        loginButton.text = "Fetching Profile..."

        val query = "SELECT * FROM users WHERE user_id = ?"
        val params = listOf(userId)

        OnlineDbHelper.executeQuery(query, params) { response, error ->
            if (response != null && response.status == 1) {
                val rows = response.data?.rows
                if (!rows.isNullOrEmpty()) {
                    // Save to SharedPreferences
                    val userRow = rows[0]
                    saveToPrefs(userRow)
                    Log.d("Login", "User data saved to Prefs")
                    navigateToHome()
                } else {
                    Log.w("Login", "User not found in SQL database")
                    Toast.makeText(this, "Profile doesn't exist in database.", Toast.LENGTH_LONG).show()
                    FirebaseAuth.getInstance().signOut()
                    loginButton.isEnabled = true
                    loginButton.text = "Login"
                }

            } else {
                // Network Error or Server Error
                Log.e("Login", "Failed to fetch user data: ${error?.message}")
                Toast.makeText(this, "Couldn't load profile.", Toast.LENGTH_LONG).show()
                FirebaseAuth.getInstance().signOut()
                loginButton.isEnabled = true
                loginButton.text = "Login"
            }
        }
    }

    private fun saveToPrefs(data: Map<String, Any>) {
        val prefs = getSharedPreferences(Globals.prefs, Context.MODE_PRIVATE)
        val editor = prefs.edit()

        editor.putString("user_id", data["user_id"]?.toString() ?: "")
        editor.putString("username", data["username"]?.toString() ?: "")
        editor.putString("email", data["email"]?.toString() ?: "")
        editor.putString("full_name", data["full_name"]?.toString() ?: "")
        editor.putString("phone_number", data["phone_number"]?.toString() ?: "")
        editor.putString("profile_pic_url", data["profile_pic_url"]?.toString() ?: "")
        editor.apply()
    }

    private fun navigateToHome() {
        val intent = Intent(this, HomeScreen::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun hideKeyboard() {
        val view = this.currentFocus
        if (view != null) {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(view.windowToken, 0)
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
}