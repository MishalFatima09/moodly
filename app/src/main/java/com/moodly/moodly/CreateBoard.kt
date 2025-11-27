package com.moodly.moodly

import android.app.ProgressDialog
import android.os.Bundle
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomnavigation.BottomNavigationView

class CreateBoard : AppCompatActivity() {
    //UI Elements
    lateinit var backBtn : ImageView
    lateinit var createBoardBtn : TextView
    lateinit var boardNameInput : EditText
    lateinit var descriptionInput: EditText
    lateinit var bottomNav : BottomNavigationView

    //Data
    var currentUserId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_create_board)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Get User ID
        val prefs = getSharedPreferences(Globals.prefs, MODE_PRIVATE)
        currentUserId = prefs.getString("user_id", "") ?: ""
        if (currentUserId.isEmpty()) {
            Toast.makeText(this, "Please log in first.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        //Initialize UI Elements
        backBtn = findViewById<ImageView>(R.id.btn_back)
        createBoardBtn = findViewById<TextView>(R.id.btn_create)
        boardNameInput = findViewById<EditText>(R.id.edittext_board_name)
        descriptionInput = findViewById<EditText>(R.id.edittext_description)
        bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav.selectedItemId = R.id.nav_boards
        setupNavigations()
        setupCreateBoardButton()
    }
    override fun onStart() {
        super.onStart()
        bottomNav.selectedItemId = R.id.nav_boards
    }
    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        bottomNav.selectedItemId = R.id.nav_boards
        bottomNav.menu.findItem(R.id.nav_boards).isChecked = true
    }
    private fun setupNavigations()
    {
        BottomNavManager.setup(this, bottomNav)
        backBtn.setOnClickListener {
            finish()
        }
    }
    private fun setupCreateBoardButton()
    {
        createBoardBtn.setOnClickListener {
            //Validation
            val title = boardNameInput.text.toString().trim()
            val description = descriptionInput.text.toString().trim()
            if(title.isEmpty())
            {
                Toast.makeText(this, "Please enter a board name", Toast.LENGTH_SHORT).show()
                boardNameInput.requestFocus()
                return@setOnClickListener
            }

            if(Globals.isInternetAvailable(this))
            {
                createBoard(title, description)
            }
            else
            {
                Toast.makeText(this, "No internet connection", Toast.LENGTH_SHORT).show()
                //TODO(Mishal): queue board creation (and create in local db when successful online)
            }

        }
    }
    private fun createBoard(title: String, description: String)
    {
        val progressDialog = ProgressDialog(this)
        progressDialog.setMessage("Creating board...")
        progressDialog.setCancelable(false)
        progressDialog.show()

        val query = "INSERT INTO boards (user_id, title, description) VALUES (?, ?, ?)"
        val params = listOf(currentUserId, title, description)
        OnlineDbHelper.executeQuery(query, params) { response, error ->
            progressDialog.dismiss()
            if(error != null)
            {
                Toast.makeText(this, "$error", Toast.LENGTH_LONG).show()
                return@executeQuery
            }
            if(response?.status == 1)
            {
                Toast.makeText(this, "Board created successfully", Toast.LENGTH_SHORT).show()
                //TODO(Mishal): create board in local db (online is successful)
                finish()
            }
            else
            {
                Toast.makeText(this, "Failed to create board", Toast.LENGTH_LONG).show()
            }
        }
    }
}