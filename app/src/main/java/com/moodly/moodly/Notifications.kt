package com.moodly.moodly

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView

class Notifications : AppCompatActivity() {

    //UI Elements
    lateinit var notificationsRecycler : RecyclerView
    lateinit var notificationsAdapter : ADAPTER_Notifications
    lateinit var bottomNav : BottomNavigationView
    lateinit var moreOptionsBtn : ImageView
    lateinit var nothingToShowText : TextView
    lateinit var progressBar : ProgressBar

    //Data Elements
    var notifications = ArrayList<DATA_Notification>()
    var currentUserId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_notifications)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        // Get User ID
        val prefs = getSharedPreferences(Globals.prefs, MODE_PRIVATE)
        currentUserId = prefs.getString("user_id", "") ?: ""

        // Init UI Elements
        bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        notificationsRecycler = findViewById<RecyclerView>(R.id.notifications_recycler)
        bottomNav.selectedItemId = R.id.nav_notifications
        moreOptionsBtn = findViewById<ImageView>(R.id.more_options)
        nothingToShowText = findViewById<TextView>(R.id.nothing_to_show_text)
        progressBar = findViewById<ProgressBar>(R.id.progress_bar)
        setupNavigations()
        setupRecyclerView()

        // Load Data
        if (currentUserId.isNotEmpty()) {
            loadNotifications()
        }
    }
    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        bottomNav.selectedItemId = R.id.nav_notifications
        bottomNav.menu.findItem(R.id.nav_notifications).isChecked = true
    }
    override fun onStart() {
        super.onStart()
        bottomNav.selectedItemId = R.id.nav_notifications
    }
    private fun setupNavigations() {
        BottomNavManager.setup(this, bottomNav)
        var backBtn = findViewById<ImageView>(R.id.back_button)
        backBtn.setOnClickListener {
            finish()
        }

        moreOptionsBtn.setOnClickListener {
            showMarkAllAsReadDialog()
        }
    }

    private fun setupRecyclerView() {
        val layoutManager = LinearLayoutManager(this)
        notificationsRecycler.layoutManager = layoutManager

        notificationsAdapter = ADAPTER_Notifications(notifications) { notification ->
            // On Click: Go to Pin Details
            val intent = Intent(this, PinDetails::class.java)
            intent.putExtra("pin_id", notification.pinId)
            intent.putExtra("image_url", notification.pinImageUrl)
            intent.putExtra("aspect_ratio", notification.pinAspectRatio)
            startActivity(intent)
        }
        notificationsRecycler.adapter = notificationsAdapter

        // swipe to mark as read
        val swipeHandler = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(r: RecyclerView, v: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder): Boolean {
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val notif = notifications[position]

                // Remove from List (Visual)
                notificationsAdapter.removeItem(position)

                // Remove notification in DB
                deleteNotification(notif.notificationId)
            }
        }

        val itemTouchHelper = ItemTouchHelper(swipeHandler)
        itemTouchHelper.attachToRecyclerView(notificationsRecycler)
    }

    private fun loadNotifications() {
        val query = """
            SELECT 
                n.notification_id, n.type, n.created_at, n.pin_id,
                u.username, u.profile_pic_url,
                p.image_url, p.aspect_ratio
            FROM notifications n
            JOIN users u ON n.sender_id = u.user_id
            JOIN pins p ON n.pin_id = p.pin_id
            WHERE n.receiver_id = ? AND n.is_seen = FALSE
            ORDER BY n.created_at DESC
        """.trimIndent()

        progressBar.visibility = View.VISIBLE
        notificationsRecycler.visibility = View.GONE

        OnlineDbHelper.executeQuery(query, listOf(currentUserId)) { response, error ->
            progressBar.visibility = View.GONE
            if (response?.status == 1) {
                val rows = response.data?.rows
                notifications.clear()

                if (rows != null) {
                    for (row in rows) {
                        val id = row["notification_id"] as? String ?: ""
                        val type = row["type"] as? String ?: "LIKE"
                        val senderName = row["username"] as? String ?: "Someone"
                        val senderPfp = row["profile_pic_url"] as? String ?: ""
                        val pinId = row["pin_id"] as? String ?: ""
                        val pinImage = row["image_url"] as? String ?: ""
                        val date = row["created_at"] as? String ?: ""

                        // Handle Aspect Ratio
                        val rawRatio = row["aspect_ratio"]
                        val ratio = when (rawRatio) {
                            is Number -> rawRatio.toFloat()
                            is String -> rawRatio.toFloatOrNull() ?: 1.0f
                            else -> 1.0f
                        }
                        notifications.add(
                            DATA_Notification(id, type, senderName, senderPfp, pinId, pinImage, ratio, date)
                        )
                    }
                }
                notificationsAdapter.notifyDataSetChanged()

                if(notifications.isEmpty())
                {
                    nothingToShowText.visibility = View.VISIBLE
                }
                else
                {
                    nothingToShowText.visibility = View.GONE
                    notificationsRecycler.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun deleteNotification(notificationId: String) {
        val query = "DELETE FROM notifications WHERE notification_id = ?"
        OnlineDbHelper.executeQueryFireAndForget(query, listOf(notificationId))
    }

    private fun showMarkAllAsReadDialog() {
        if (notifications.isEmpty()) {
            Toast.makeText(this, "No notifications to clear", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Mark all as read?")
            .setMessage("This will clear all your notifications. This action cannot be undone.")
            .setPositiveButton("Clear All") { dialog, _ ->
                deleteAllNotifications()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun deleteAllNotifications() {
        val query = "DELETE FROM notifications WHERE receiver_id = ?"

        // Clear list immediately
        notifications.clear()
        notificationsAdapter.notifyDataSetChanged()

        // Perform DB deletion in background
        OnlineDbHelper.executeQueryFireAndForget(query, listOf(currentUserId))
        Toast.makeText(this, "All notifications cleared", Toast.LENGTH_SHORT).show()
    }
}