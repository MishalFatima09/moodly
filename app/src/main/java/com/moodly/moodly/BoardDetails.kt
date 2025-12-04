package com.moodly.moodly

import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.res.Configuration
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.moodly.moodly.Globals.scheduleSyncWorker

class BoardDetails : AppCompatActivity() {
    // UI Elements
    private lateinit var editBtn : ImageView
    private lateinit var backBtn: ImageView
    private lateinit var boardTitle : TextView
    private lateinit var boardDescription : TextView
    private lateinit var boardPinsCount : TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ADAPTER_Pin

    //Data
    var currentUserId : String = ""
    var pins = ArrayList<DATA_Pin>()
    var boardId : String = ""
    var boardTitleText : String = ""
    var boardDescriptionText : String = ""
    var boardPinCountText : String = ""
    private lateinit var offlineDbHelper: OfflineDbHelper // Initialize the helper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_board_details)
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

        // Initialize Offline Helper
        offlineDbHelper = OfflineDbHelper.getInstance(applicationContext)

        // get data from intent (coming from ADAPTER_Board)
        boardId = intent.getStringExtra("board_id") ?: ""
        boardTitleText = intent.getStringExtra("board_title") ?: ""
        boardDescriptionText = intent.getStringExtra("board_description") ?: ""
        boardPinCountText = intent.getIntExtra("board_pin_count", 0).toString()
        if(boardId.isEmpty())
        {
            Toast.makeText(this, "Invalid board data.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }


        //init UI
        editBtn = findViewById<ImageView>(R.id.btn_options)
        backBtn = findViewById<ImageView>(R.id.btn_back)
        boardTitle = findViewById<TextView>(R.id.tv_board_name)
        boardDescription = findViewById<TextView>(R.id.tv_board_description)
        boardPinsCount = findViewById<TextView>(R.id.tv_board_pins_count)
        recyclerView = findViewById(R.id.pins_rv)

        //set data to UI
        boardTitle.text = boardTitleText
        if(boardDescriptionText.isEmpty())
        {
            boardDescription.text = "No description"
        }
        else{
            boardDescription.text = boardDescriptionText
        }
        boardPinsCount.text = "$boardPinCountText Pins"

        setupRecyclerView()
        setupNavigations()

        if(Globals.isInternetAvailable(this)) {
            loadBoardPins()
        }
        else{
            // DONE: Load board pins from local db
            loadBoardPinsLocal()
        }
    }
    private fun setupRecyclerView()
    {
        val layoutManager = StaggeredGridLayoutManager(getSpanCount(), StaggeredGridLayoutManager.VERTICAL)
        recyclerView.layoutManager = layoutManager
        recyclerView.addItemDecoration(UTILITY_SpacingItemDecorator(2))
        adapter = ADAPTER_Pin(pins, boardId) { selectedPin ->
            showRemovePinDialog(selectedPin)
        }
        recyclerView.adapter = adapter
    }
    private fun setupNavigations() {
        backBtn.setOnClickListener {
            finish()
        }
        editBtn.setOnClickListener {
            showEditBoardDialog()
        }
    }
    private fun getSpanCount(): Int {
        return if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 3 else 2
    }

    // --- OFFLINE PIN LOADING ---
    private fun loadBoardPinsLocal() {
        try {
            val localPins = offlineDbHelper.loadBoardPinsLocal(boardId)
            pins.clear()
            pins.addAll(localPins)
            adapter.notifyDataSetChanged()
            boardPinsCount.text = "${pins.size} Pins"
        } catch (e: Exception) {
            Toast.makeText(this, "Error loading local pins: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // --- ONLINE PIN LOADING ---
    private fun loadBoardPins() {
        // Get pin details WHERE the pin is linked to this board_id
        val query = """
            SELECT p.pin_id, p.image_url, p.aspect_ratio 
            FROM pins p
            INNER JOIN board_pins bp ON p.pin_id = bp.pin_id
            WHERE bp.board_id = ?
            ORDER BY bp.saved_at DESC
        """.trimIndent()

        OnlineDbHelper.executeQuery(query, listOf(boardId)) { response, error ->
            if (error != null) {
                Toast.makeText(this, "Error loading pins", Toast.LENGTH_SHORT).show()
                return@executeQuery
            }

            if (response?.status == 1) {
                val rows = response.data?.rows
                pins.clear()
                if (rows != null) {
                    for (row in rows) {
                        val id = row["pin_id"] as? String ?: ""
                        val url = row["image_url"] as? String ?: ""
                        val rawRatio = row["aspect_ratio"]
                        val ratio = when (rawRatio) {
                            is Number -> rawRatio.toFloat()
                            is String -> rawRatio.toFloatOrNull() ?: 1.0f
                            else -> 1.0f
                        }

                        if (id.isNotEmpty()) {
                            pins.add(DATA_Pin(id, url, ratio))
                            // Cache pin data locally upon successful retrieval (if not already there)
                            offlineDbHelper.savePinDetails(id, url, ratio)
                        }
                    }
                }

                adapter.notifyDataSetChanged()
                boardPinsCount.text = "${pins.size} Pins"
            }
        }
    }

    private fun showEditBoardDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_board, null)

        val etTitle = dialogView.findViewById<EditText>(R.id.et_board_title)
        val etDesc = dialogView.findViewById<EditText>(R.id.et_board_description)
        val btnSave = dialogView.findViewById<Button>(R.id.btn_save_board)

        // fill current data
        etTitle.setText(boardTitleText)
        etDesc.setText(boardDescriptionText)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        btnSave.setOnClickListener {
            val newTitle = etTitle.text.toString().trim()
            val newDesc = etDesc.text.toString().trim()

            if (newTitle.isEmpty()) {
                Toast.makeText(this, "Board title cannot be empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Prepare payload for queuing
            val payload = mapOf(
                "board_id" to boardId,
                "title" to newTitle,
                "description" to newDesc
            )
            // Assuming Globals.gson is available for JSON serialization
            val payloadJson = Globals.gson.toJson(payload)


            if(Globals.isInternetAvailable(this)) {
                updateBoard(newTitle, newDesc, dialog)
            }
            else
            {
                // DONE: Queue the board title+desc update for when online
                offlineDbHelper.queueOfflineAction("BOARD_UPDATE", payloadJson)
                scheduleSyncWorker(this)

                // Optimistic UI Update
                boardTitleText = newTitle
                boardDescriptionText = newDesc
                boardTitle.text = newTitle
                boardDescription.text = newDesc

                Toast.makeText(this, "Board update queued for sync.", Toast.LENGTH_LONG).show()
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun updateBoard(newTitle: String, newDesc: String, dialog: AlertDialog) {
        val query = "UPDATE boards SET title = ?, description = ? WHERE board_id = ? AND user_id = ?"

        val progressDialog = ProgressDialog(this).apply {
            setMessage("Updating board...")
            setCancelable(false)
            show()
        }
        dialog.setCancelable(false)

        OnlineDbHelper.executeQuery(query, listOf(newTitle, newDesc, boardId, currentUserId)) { response, error ->
            dialog.setCancelable(true)
            progressDialog.dismiss()
            if(error!=null)
            {
                Toast.makeText(this, "Error updating board: ${error.message}", Toast.LENGTH_SHORT).show()
                return@executeQuery
            }
            if (response?.status == 1) {
                Toast.makeText(this, "Board updated!", Toast.LENGTH_SHORT).show()

                // Update UI variables
                boardTitleText = newTitle
                boardDescriptionText = newDesc

                // Update TextViews
                boardTitle.text = newTitle
                boardDescription.text = if(newDesc.isEmpty()) "No description" else newDesc

                // DONE: Update board title+desc in local db
                offlineDbHelper.updateBoardDetails(boardId, newTitle, newDesc)
                dialog.dismiss()
            } else {
                Toast.makeText(this, "Failed to update: ${error?.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    private fun showRemovePinDialog(pin: DATA_Pin) {
        AlertDialog.Builder(this)
            .setTitle("Remove from Board?")
            .setMessage("This will remove the pin from '$boardTitleText'.")
            .setPositiveButton("Remove") { _, _ ->
                if(Globals.isInternetAvailable(this)) {
                    removePinFromBoard(pin)
                }
                else {
                    // OFFLINE: Queue the pin removal
                    Toast.makeText(this, "No internet connection. Pin removal queued.", Toast.LENGTH_SHORT).show()

                    // Prepare payload for queuing
                    val payload = mapOf("board_id" to boardId, "pin_id" to pin.pinId)
                    val payloadJson = Globals.gson.toJson(payload)

                    // Queue the action
                    offlineDbHelper.queueOfflineAction("BOARD_PIN_REMOVE", payloadJson)
                    scheduleSyncWorker(this)

                    // Optimistic UI update
                    pins.remove(pin)
                    adapter.notifyDataSetChanged()
                    boardPinCountText = "${pins.size} Pins"
                    boardPinsCount.text = boardPinCountText
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun removePinFromBoard(pin: DATA_Pin) {
        val query = "DELETE FROM board_pins WHERE board_id = ? AND pin_id = ?"

        OnlineDbHelper.executeQuery(query, listOf(boardId, pin.pinId)) { response, error ->
            if(error!=null)
            {
                Toast.makeText(this, "Error removing pin: ${error.message}", Toast.LENGTH_SHORT).show()
                return@executeQuery
            }
            if (response?.status == 1) {
                Toast.makeText(this, "Pin removed", Toast.LENGTH_SHORT).show()

                // DONE: Update in local DB
                offlineDbHelper.removePinBoardLink(boardId, pin.pinId)

                // Refresh the list locally without reloading everything from net
                pins.remove(pin)
                adapter.notifyDataSetChanged()

                // Update count text
                boardPinCountText = "${pins.size} Pins"
                boardPinsCount.text = boardPinCountText
            } else {
                Toast.makeText(this, "Failed to remove: ${error?.message} ", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }
}