package com.moodly.moodly

import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.moodly.moodly.Globals.scheduleSyncWorker

class Boards : AppCompatActivity() {
    //UI
    lateinit var adapter: ADAPTER_Board
    lateinit var boards_rv : RecyclerView
    lateinit var bottomNav : BottomNavigationView
    lateinit var createBoardBtn : LinearLayout
    lateinit var nothingToShowText : TextView
    lateinit var progressBar : ProgressBar

    //Data
    var boards = ArrayList<DATA_Board>()
    var currentUserId: String = ""
    private lateinit var offlineDbHelper: OfflineDbHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_boards)
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
        offlineDbHelper = OfflineDbHelper.getInstance(applicationContext)



        //Initialize UI elements
        boards_rv = findViewById<RecyclerView>(R.id.boards_rv)
        bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        createBoardBtn = findViewById<LinearLayout>(R.id.btn_create)
        nothingToShowText = findViewById<TextView>(R.id.nothing_to_show_text)
        progressBar = findViewById<ProgressBar>(R.id.progress_bar)
        bottomNav.selectedItemId = R.id.nav_boards

        setupNavigations()
        setupRecyclerView()
    }
    override fun onStart() {
        super.onStart()
        bottomNav.selectedItemId = R.id.nav_boards
        if(Globals.isInternetAvailable(this)) {
            loadBoards() // Online loading
        }
        else
        {
            loadBoardsLocal()
        }
    }
    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        bottomNav.selectedItemId = R.id.nav_boards
        bottomNav.menu.findItem(R.id.nav_boards).isChecked = true
    }
    private fun setupNavigations()
    {
        BottomNavManager.setup(this, bottomNav)
        createBoardBtn.setOnClickListener {
            val intent = Intent(this, CreateBoard::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            startActivity(intent)
        }
    }
    private fun setupRecyclerView()
    {
        val orientation = resources.configuration.orientation
        val spanCount = if (orientation == Configuration.ORIENTATION_LANDSCAPE) 3 else 2
        boards_rv.layoutManager = GridLayoutManager(this, spanCount)
        adapter = ADAPTER_Board(boards) { selectedBoard ->
            showDeleteBoardDialog(selectedBoard)
        }
        boards_rv.adapter = adapter
    }

    private fun showDeleteBoardDialog(board: DATA_Board) {
        //Delete board when a board is long pressed in the boards activity
        AlertDialog.Builder(this)
            .setTitle("Delete ${board.title}?")
            .setMessage("Are you sure? All pins inside will be unsaved from this board.")
            .setPositiveButton("Delete") { dialog, _ ->
                if(Globals.isInternetAvailable(this)) {
                    deleteBoard(board)
                }
                else {
                    // OFFLINE: Queue board deletion
                    Toast.makeText(this, "No internet connection. Board deletion queued.", Toast.LENGTH_LONG).show()

                    // Prepare payload for deletion
                    val payload = mapOf("board_id" to board.board_id)
                    val payloadJson = Globals.gson.toJson(payload)

                    // Queue the action
                    offlineDbHelper.queueOfflineAction("BOARD_DELETE", payloadJson)
                    scheduleSyncWorker(this)

                    // Immediately remove from local UI
                    boards.remove(board)
                    adapter.notifyDataSetChanged()
                    updateEmptyState()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteBoard(board: DATA_Board) {
        val query = "DELETE FROM boards WHERE board_id = ? AND user_id = ?"

        val progressDialog = ProgressDialog(this).apply {
            setMessage("Deleting board...")
            setCancelable(false)
            show()
        }

        OnlineDbHelper.executeQuery(query, listOf(board.board_id, currentUserId)) { response, error ->
            progressDialog.dismiss()
            if(error != null) {
                Toast.makeText(this, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                return@executeQuery
            }
            if (response?.status == 1) {
                // Success
                Toast.makeText(this, "Board deleted", Toast.LENGTH_SHORT).show()
                offlineDbHelper.deleteBoardAndPins(board.board_id)

                // Refresh the boards list
                loadBoards()

            } else {
                Toast.makeText(this, "Failed to delete: ${error?.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateEmptyState() {
        if (boards.isEmpty()) {
            nothingToShowText.visibility = View.VISIBLE
            boards_rv.visibility = View.GONE
        } else {
            nothingToShowText.visibility = View.GONE
            boards_rv.visibility = View.VISIBLE
        }
    }

    // --- LOCAL DB LOADING ---
    private fun loadBoardsLocal() {
        boards_rv.visibility = View.GONE
        nothingToShowText.visibility = View.GONE
        progressBar.visibility = View.VISIBLE

        try {
            val localBoards = offlineDbHelper.loadUserBoards(currentUserId)
            boards.clear()
            boards.addAll(localBoards)
            adapter.notifyDataSetChanged()

            progressBar.visibility = View.GONE
            updateEmptyState()

        } catch (e: Exception) {
            progressBar.visibility = View.GONE
            nothingToShowText.visibility = View.VISIBLE
            Toast.makeText(this, "Error loading local boards: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    private fun loadBoards() {
        boards_rv.visibility = View.GONE
        nothingToShowText.visibility = View.GONE
        progressBar.visibility = View.VISIBLE
        val query = """
            SELECT 
                b.board_id, 
                b.title, 
                b.description,
                b.cover_image_url,
                (SELECT COUNT(*) FROM board_pins WHERE board_id = b.board_id) as pin_count
            FROM boards b
            WHERE b.user_id = ?
            ORDER BY b.created_at DESC
        """.trimIndent()

        OnlineDbHelper.executeQuery(query, listOf(currentUserId)) { response, error ->
            if (error != null) {
                progressBar.visibility = View.GONE
                nothingToShowText.visibility = View.VISIBLE
                Toast.makeText(this, "$error", Toast.LENGTH_LONG).show()
                return@executeQuery
            }

            if (response?.status == 1) {
                val rows = response.data?.rows
                boards.clear()
                if (rows != null) {
                    for (row in rows) {
                        val id = row["board_id"] as? String ?: ""
                        val title = row["title"] as? String ?: "Untitled"
                        val description = row["description"] as? String ?: ""
                        val cover = row["cover_image_url"] as? String ?: ""
                        val count = (row["pin_count"] as? Number)?.toInt() ?: 0

                        boards.add(DATA_Board(id, cover, title,description, count))
                    }
                }

                adapter.notifyDataSetChanged()
                progressBar.visibility = View.GONE
                if (boards.isEmpty()) {
                    nothingToShowText.visibility = View.VISIBLE
                    boards_rv.visibility = View.GONE
                } else {
                    nothingToShowText.visibility = View.GONE
                    boards_rv.visibility = View.VISIBLE
                }
            } else {
                progressBar.visibility = View.GONE
                nothingToShowText.visibility = View.VISIBLE
                Toast.makeText(this, "Failed to load boards.", Toast.LENGTH_LONG).show()
            }
        }
    }
}