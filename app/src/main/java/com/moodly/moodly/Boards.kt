package com.moodly.moodly

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView

class Boards : AppCompatActivity() {
    lateinit var boards_rv : RecyclerView
    lateinit var bottomNav : BottomNavigationView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_boards)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav.selectedItemId = R.id.nav_boards
        setupNavigations()

        //recycler view
        boards_rv = findViewById<RecyclerView>(R.id.boards_rv)
        val orientation = resources.configuration.orientation
        val spanCount = if (orientation == Configuration.ORIENTATION_LANDSCAPE) 3 else 2
        boards_rv.layoutManager = GridLayoutManager(this, spanCount)
        val boards = listOf(
            DATA_Board("Meow", 21),
            DATA_Board("Home decor", 17),
            DATA_Board("Vroom", 46)
        )
        boards_rv.adapter = ADAPTER_Board(boards)
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
        val btnCreate = findViewById<LinearLayout>(R.id.btn_create)
        btnCreate.setOnClickListener {
            val intent = Intent(this, CreateBoard::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            startActivity(intent)
        }
    }
}