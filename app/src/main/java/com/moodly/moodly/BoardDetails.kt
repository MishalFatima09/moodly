package com.moodly.moodly

import android.content.res.Configuration
import android.graphics.Typeface
import android.os.Bundle
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
            boardDescriptionText = "No description"
            boardDescription.setTypeface(null, Typeface.ITALIC)
        }
        else{
            boardDescription.text = boardDescriptionText
            boardDescription.setTypeface(null, Typeface.NORMAL)
        }
        boardPinsCount.text = "$boardPinCountText Pins"

        setupRecyclerView()
        setupNavigations()

        if(Globals.isInternetAvailable(this)) {
            loadBoardPins()
        }
        else{
            //TODO(Mishal): Load board pins from local db
        }
    }
    private fun setupRecyclerView()
    {
        val layoutManager = StaggeredGridLayoutManager(getSpanCount(), StaggeredGridLayoutManager.VERTICAL)
        recyclerView.layoutManager = layoutManager
        recyclerView.addItemDecoration(UTILITY_SpacingItemDecorator(2))
        adapter = ADAPTER_Pin(pins)
        recyclerView.adapter = adapter
    }
    private fun setupNavigations() {
        backBtn.setOnClickListener {
            finish()
        }
    }
    private fun getSpanCount(): Int {
        return if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 3 else 2
    }
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
                        }
                    }
                }

                adapter.notifyDataSetChanged()
                boardPinsCount.text = "${pins.size} Pins"
            }
        }
    }
}