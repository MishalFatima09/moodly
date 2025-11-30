package com.moodly.moodly

import android.app.AlertDialog
import android.app.DownloadManager
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.google.android.flexbox.FlexboxLayout
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.gson.Gson
import com.moodly.moodly.Globals.scheduleSyncWorker
import kotlinx.coroutines.launch


class PinDetails : AppCompatActivity() {
    // UI Elements
    lateinit var backBtn : ImageView

    lateinit var optionsBtn : ImageView
    lateinit var pinImage : ImageView
    lateinit var likeBtn : LinearLayout
    lateinit var likeIcon : ImageView
    lateinit var likeCountText : TextView
    lateinit var downloadBtn : ImageView
    lateinit var saveBtn : LinearLayout
    lateinit var pinTitleText : TextView
    lateinit var pinDescriptionText : TextView
    lateinit var creatorImage : ImageView
    lateinit var creatorUsernameText : TextView
    lateinit var dateText : TextView
    lateinit var keywordsContainer : FlexboxLayout
    lateinit var offlineDbHelper: OfflineDbHelper

    // Data
    lateinit var pinId : String
    lateinit var imageUrl : String
    var aspectRatio : Float = 1.0f

    // State
    var currentUserId: String = ""
    var isLiked : Boolean = false
    var creatorId: String = ""
    var creatorFcmToken: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_pin_details)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Go to home if this activity is last in the stack
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isTaskRoot) {
                    val intent = Intent(this@PinDetails, HomeScreen::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })


        // Get Current User ID
        val prefs = getSharedPreferences(Globals.prefs, Context.MODE_PRIVATE)
        currentUserId = prefs.getString("user_id", "") ?: ""
        offlineDbHelper = OfflineDbHelper.getInstance(applicationContext)

        // Initialize UI
        initViews()

        // Get data from intent
        pinId = intent.getStringExtra("pin_id") ?: ""
        imageUrl = intent.getStringExtra("image_url") ?: ""
        aspectRatio = intent.getFloatExtra("aspect_ratio", 1.0f)

        // Load image immediately (independent of metadata)
        loadPinImage()

        if (pinId.isNotEmpty()) {
            fetchPinDetails()
        } else {
            Toast.makeText(this, "Error: Invalid Pin ID", Toast.LENGTH_SHORT).show()
            setButtonsEnabled(false)
        }

        setupNavigations()
    }

    private fun initViews() {
        backBtn = findViewById(R.id.btn_back)
        optionsBtn = findViewById(R.id.btn_options)
        pinImage = findViewById(R.id.pin_image)
        likeBtn = findViewById(R.id.btn_like)
        likeIcon = findViewById(R.id.like_icon)
        likeCountText = findViewById(R.id.tv_like_count)
        downloadBtn = findViewById(R.id.btn_download)
        saveBtn = findViewById(R.id.btn_save)
        pinTitleText = findViewById(R.id.tv_title)
        pinDescriptionText = findViewById(R.id.tv_description)
        creatorImage = findViewById(R.id.user_pfp)
        creatorUsernameText = findViewById(R.id.tv_username)
        dateText = findViewById(R.id.tv_post_date)
        keywordsContainer = findViewById(R.id.keywords_container)
    }

    private fun loadPinImage() {
        // Calculate dynamic height based on aspect ratio
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val targetHeight = (screenWidth * aspectRatio).toInt()

        pinImage.layoutParams.height = targetHeight
        pinImage.requestLayout()

        Glide.with(this)
            .load(imageUrl)
            .transition(DrawableTransitionOptions.withCrossFade())
            .placeholder(R.color.black)
            .error(R.color.black)
            .into(pinImage)
    }

    private fun setupNavigations() {
        backBtn.setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        likeBtn.setOnClickListener {
            if(Globals.isInternetAvailable(this)) {
                toggleLike()
            }
            else {
                Toast.makeText(this, "No internet connection", Toast.LENGTH_SHORT).show()
            }
        }

        downloadBtn.setOnClickListener {
            if(Globals.isInternetAvailable(this)) {
                downloadImage()
            }
            else {
                Toast.makeText(this, "No internet connection", Toast.LENGTH_SHORT).show()
            }
        }

        saveBtn.setOnClickListener {
            if(Globals.isInternetAvailable(this)) {
                showSaveToBoardDialog()
            }
            else {
                Toast.makeText(this, "No internet connection", Toast.LENGTH_SHORT).show()
            }
        }

        optionsBtn.setOnClickListener {
            if (currentUserId == creatorId) {
                showEditDeleteDialog()
            } else {
                Toast.makeText(this, "You can only edit your own pins.", Toast.LENGTH_SHORT).show()
            }
        }
    }
    private fun showSaveToBoardDialog() {
        if (currentUserId.isEmpty()) {
            Toast.makeText(this, "Please log in to save pins", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_select_board, null)
        val bottomSheetDialog = BottomSheetDialog(this)
        bottomSheetDialog.setContentView(dialogView)

        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.rv_board_list)
        val progressBar = dialogView.findViewById<ProgressBar>(R.id.progressBar_boards)

        recyclerView.layoutManager = LinearLayoutManager(this)

        // Show loading state
        progressBar.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        bottomSheetDialog.show()

        val query = """
            SELECT board_id, title, cover_image_url, description,
                   (SELECT COUNT(*) FROM board_pins WHERE board_id = b.board_id) as pin_count 
            FROM boards b 
            WHERE user_id = ? 
            ORDER BY created_at DESC
        """.trimIndent()

        OnlineDbHelper.executeQuery(query, listOf(currentUserId)) { response, error ->
            progressBar.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE

            if (response?.status == 1) {
                val rows = response.data?.rows
                val boardList = ArrayList<DATA_Board>()

                if (rows != null) {
                    for (row in rows) {
                        val id = row["board_id"] as? String ?: ""
                        val title = row["title"] as? String ?: "Untitled"
                        val description = row["description"] as? String ?: ""
                        val cover = row["cover_image_url"] as? String ?: ""
                        val count = (row["pin_count"] as? Number)?.toInt() ?: 0
                        boardList.add(DATA_Board(id, cover, title, description, count))
                    }
                }

                if (boardList.isEmpty()) {
                    Toast.makeText(this, "You have no boards yet.", Toast.LENGTH_SHORT).show()
                } else {
                    val adapter = ADAPTER_BoardSelection(boardList) { selectedBoard ->
                        savePinToBoard(selectedBoard.board_id, bottomSheetDialog)
                    }
                    recyclerView.adapter = adapter
                }
            } else {
                Toast.makeText(this, "Failed to load boards.", Toast.LENGTH_SHORT).show()
                bottomSheetDialog.dismiss()
            }
        }
    }

    private fun savePinToBoard(boardId: String, dialog: BottomSheetDialog) {
        val query = "INSERT INTO board_pins (board_id, pin_id) VALUES (?, ?)"
        dialog.setCancelable(false)

        OnlineDbHelper.executeQuery(query, listOf(boardId, pinId)) { response, error ->
            dialog.setCancelable(true)
            dialog.dismiss()

            if (response?.status == 1) {
                //Save successful

                //Update board cover if not set
                val updateCoverQuery = """
                UPDATE boards 
                SET cover_image_url = ? 
                WHERE board_id = ? AND (cover_image_url IS NULL OR cover_image_url = '')
            """.trimIndent()
                OnlineDbHelper.executeQueryFireAndForget(updateCoverQuery, listOf(imageUrl, boardId))

                Toast.makeText(this, "Pin saved!", Toast.LENGTH_SHORT).show()
                //Save notification for pin save
                sendSaveNotification()

                offlineDbHelper.addPinBoardLink(boardId, pinId)
            } else {
                Toast.makeText(this, "Pin already saved to this board.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun sendSaveNotification() {
        val notifQuery = "INSERT INTO notifications (receiver_id, sender_id, pin_id, type) VALUES (?, ?, ?, 'SAVE')"
        OnlineDbHelper.executeQueryFireAndForget(notifQuery, listOf(creatorId, currentUserId, pinId))


        //Send fcm notification for board save
        if (creatorFcmToken.isNotEmpty() && currentUserId != creatorId) {
            val prefs = getSharedPreferences(Globals.prefs, Context.MODE_PRIVATE)
            val senderUsername = prefs.getString("username", "Someone") ?: "Someone"

            lifecycleScope.launch {
                NotificationSender.sendNotification(
                    fcmToken = creatorFcmToken,
                    title = "Your pin was saved",
                    body = "$senderUsername saved your pin '${pinTitleText.text}'",
                    notificationType = "SAVE",
                    pinId = pinId,
                    imageUrl = imageUrl
                )
            }
        }
    }

    private fun showEditDeleteDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_pin, null)

        val etTitle = dialogView.findViewById<EditText>(R.id.et_edit_title)
        val etDesc = dialogView.findViewById<EditText>(R.id.et_edit_description)
        val btnUpdate = dialogView.findViewById<Button>(R.id.btn_confirm_update)
        val btnDelete = dialogView.findViewById<Button>(R.id.btn_confirm_delete)

        // Pre-fill current data
        etTitle.setText(pinTitleText.text)
        etDesc.setText(pinDescriptionText.text)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        // Make background transparent if you have rounded corners in XML, otherwise optional
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // --- Update Logic ---
        btnUpdate.setOnClickListener {
            val newTitle = etTitle.text.toString().trim()
            val newDesc = etDesc.text.toString().trim()

            if (newTitle.isEmpty()) {
                Toast.makeText(this, "Title cannot be empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (Globals.isInternetAvailable(this)) {
                // Online: Execute immediately
                updatePin(newTitle, newDesc, dialog)
            } else {
                // OFFLINE: Queue the update action and apply changes locally

                // 1. Prepare JSON Payload for the queue
                val payload = mapOf(
                    "pin_id" to pinId,
                    "title" to newTitle,
                    "description" to newDesc
                )

                // Assuming Globals.gson is available for JSON serialization
                val payloadJson = Globals.gson.toJson(payload)

                // 2. Queue the action
                offlineDbHelper.queueOfflineAction("PIN_UPDATE", payloadJson)
                scheduleSyncWorker(this)

                // 3. Apply optimistic UI updates
                pinTitleText.text = newTitle
                pinDescriptionText.text = newDesc
                dialog.dismiss() // Close the edit dialog

                Toast.makeText(this, "Pin update queued for sync.", Toast.LENGTH_LONG).show()
            }
        }

        // --- Delete Logic ---
        btnDelete.setOnClickListener {
            // Confirm delete to avoid accidents
            AlertDialog.Builder(this)
                .setTitle("Delete Pin?")
                .setMessage("This action cannot be undone.")
                .setPositiveButton("Delete") { _, _ ->

                    if (Globals.isInternetAvailable(this)) {
                        // Online: Execute immediately
                        deletePin(dialog)
                    } else {
                        // OFFLINE: Queue the delete action and hide pin locally

                        // 1. Prepare JSON Payload for the queue
                        val payload = mapOf("pin_id" to pinId)
                        val payloadJson = Globals.gson.toJson(payload)

                        // 2. Queue the action
                        offlineDbHelper.queueOfflineAction("PIN_DELETE", payloadJson)
                        scheduleSyncWorker(this)

                        // 3. Close UI/Activity as if the deletion was successful
                        Toast.makeText(this, "Pin deletion queued for sync.", Toast.LENGTH_LONG).show()
                        dialog.dismiss() // Close the edit dialog
                        finish()        // Close PinDetails activity
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        dialog.show()
    }

    // --- NETWORKING ---
    private fun fetchPinDetails() {
        val query = """
            SELECT p.title, p.description, p.keywords, p.created_at, p.user_id, p.image_url, p.aspect_ratio,
                   u.username, u.profile_pic_url, u.fcm_token,
                   (SELECT COUNT(*) FROM pin_likes WHERE pin_id = p.pin_id) as like_count,
                   (SELECT COUNT(*) FROM pin_likes WHERE pin_id = p.pin_id AND user_id = ?) as is_liked
            FROM pins p
            JOIN users u ON p.user_id = u.user_id
            WHERE p.pin_id = ?
        """.trimIndent()

        // Disable buttons while loading
        setButtonsEnabled(false)

        OnlineDbHelper.executeQuery(query, listOf(currentUserId, pinId)) { response, error ->
            if(error != null) {
                Log.e("Pin Details", "Error fetching pin details: ${error.message}")
                Toast.makeText(this, "Error loading pin details", Toast.LENGTH_LONG).show()
                setButtonsEnabled(true)
                return@executeQuery
            }

            if (response?.status == 1) {
                val row = response.data?.rows?.firstOrNull()
                if (row != null) {
                    // Extract Data
                    val title = row["title"] as? String ?: ""
                    val description = row["description"] as? String ?: ""
                    val keywordsStr = row["keywords"] as? String ?: ""
                    val date = row["created_at"] as? String ?: ""
                    creatorId = row["user_id"] as? String ?: ""
                    creatorFcmToken = row["fcm_token"] as? String ?: ""
                    val username = row["username"] as? String ?: "Unknown"
                    val pfpUrl = row["profile_pic_url"] as? String ?: ""
                    val likes = (row["like_count"] as? Number)?.toInt() ?: 0
                    val isLikedInt = (row["is_liked"] as? Number)?.toInt() ?: 0
                    isLiked = (isLikedInt > 0)

                    // Update image URL and aspect ratio if not provided via intent
                    if(imageUrl.isEmpty())
                    {
                        imageUrl = row["image_url"] as? String ?: imageUrl
                        val rawRatio = row["aspect_ratio"]
                        aspectRatio = when (rawRatio) {
                            is Number -> rawRatio.toFloat()
                            is String -> rawRatio.toFloatOrNull() ?: 1.0f
                            else -> 1.0f
                        }
                        loadPinImage()
                    }

                    // Update UI
                    pinTitleText.text = title
                    pinDescriptionText.text = description
                    creatorUsernameText.text = username
                    dateText.text = date.take(10)
                    likeCountText.text = likes.toString()

                    // Update the heart icon immediately based on the DB result
                    updateLikeIcon()

                    Glide.with(this)
                        .load(pfpUrl)
                        .circleCrop()
                        .placeholder(R.drawable.pfp_placeholder)
                        .error(R.drawable.pfp_placeholder)
                        .into(creatorImage)

                    loadKeywords(keywordsStr)

                    /*
                    Hint: All details of the pin exist here in global or local vars
                    (pinId, imageUrl, title, description, keywordsString, likeCount, isLiked, aspectRatio, creatorId, creatorUsername, creatorPfpUrl, creationDate).
                    You can save them for local db in an object here (for when and if the user saves it to a board)(just insert the object).
                     */
                    setButtonsEnabled(true)
                } else {
                    Toast.makeText(this, "Pin not found.", Toast.LENGTH_SHORT).show()
                    setButtonsEnabled(true)
                }
            } else {
                setButtonsEnabled(true)
            }
        }
    }
    private fun updatePin(newTitle: String, newDesc: String, dialog: AlertDialog) {
        val query = "UPDATE pins SET title = ?, description = ? WHERE pin_id = ? AND user_id = ?"
        val progressDialog = ProgressDialog(this).apply {
            setMessage("Updating pin...")
            setCancelable(false)
            show()
        }
        setButtonsEnabled(false)
        OnlineDbHelper.executeQuery(query, listOf(newTitle, newDesc, pinId, currentUserId)) { response, error ->
            setButtonsEnabled(true)
            progressDialog.dismiss()
            if (response?.status == 1) {
                Toast.makeText(this, "Pin updated!", Toast.LENGTH_SHORT).show()
                pinTitleText.text = newTitle
                pinDescriptionText.text = newDesc
                dialog.dismiss()
            } else {
                Toast.makeText(this, "Failed to update: ${error?.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun deletePin(dialog: AlertDialog) {
        val query = "DELETE FROM pins WHERE pin_id = ? AND user_id = ?"
        setButtonsEnabled(false)
        val progressDialog = ProgressDialog(this).apply {
            setMessage("Deleting pin...")
            setCancelable(false)
            show()
        }
        OnlineDbHelper.executeQuery(query, listOf(pinId, currentUserId)) { response, error ->
            setButtonsEnabled(true)
            progressDialog.dismiss()
            if (response?.status == 1) {
                Toast.makeText(this, "Pin deleted.", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
                finish()
            } else {
                Toast.makeText(this, "Failed to delete: ${error?.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadKeywords(keywordsStr: String) {
        keywordsContainer.removeAllViews()
        if (keywordsStr.isEmpty()) return

        val list = keywordsStr.split("|")
        for (keyword in list) {
            if (keyword.isNotBlank()) {
                val chip = layoutInflater.inflate(R.layout.item_keyword, keywordsContainer, false) as TextView
                chip.text = "#$keyword"
                keywordsContainer.addView(chip)
            }
        }
    }

    private fun toggleLike() {
        if (currentUserId.isEmpty()) {
            Toast.makeText(this, "Please log in to like pins", Toast.LENGTH_SHORT).show()
            return
        }

        //disable btn
        likeBtn.isEnabled = false
        likeBtn.alpha = 0.5f

        // Optimistic UI Update (Change icon & count immediately)
        isLiked = !isLiked
        updateLikeIcon()

        var currentCount = likeCountText.text.toString().toIntOrNull() ?: 0
        if (isLiked) currentCount++ else currentCount--
        likeCountText.text = currentCount.toString()

        val query = if (isLiked) {
            // Insert Like
            "INSERT INTO pin_likes (user_id, pin_id) VALUES (?, ?)"
        } else {
            // Remove Like
            "DELETE FROM pin_likes WHERE user_id = ? AND pin_id = ?"
        }

        OnlineDbHelper.executeQuery(query, listOf(currentUserId, pinId)) { response, _ ->
            likeBtn.isEnabled = true
            likeBtn.alpha = 1.0f

            if (response?.status != 1) {
                // REVERT on failure
                isLiked = !isLiked
                updateLikeIcon()

                // Revert count
                var revertedCount = likeCountText.text.toString().toIntOrNull() ?: 0
                if (isLiked) revertedCount++ else revertedCount--
                likeCountText.text = revertedCount.toString()

                Toast.makeText(this, "Action failed. Check internet.", Toast.LENGTH_SHORT).show()
            } else if (isLiked) {
                offlineDbHelper.addLikedPin(currentUserId, pinId)
                sendLikeNotification()
            } else {
                // Success + Unliked: Remove from local DB
                offlineDbHelper.removeLikedPin(currentUserId, pinId)

            }
        }
    }

    private fun sendLikeNotification() {
        // Don't notify if liking your own post
        if (currentUserId == creatorId) return

        val query = "INSERT INTO notifications (receiver_id, sender_id, pin_id, type) VALUES (?, ?, ?, 'LIKE')"
        OnlineDbHelper.executeQueryFireAndForget(query, listOf(creatorId, currentUserId, pinId))

        //Send fcm notification for pin like
        if (creatorFcmToken.isNotEmpty() && currentUserId != creatorId) {
            val prefs = getSharedPreferences(Globals.prefs, Context.MODE_PRIVATE)
            val senderUsername = prefs.getString("username", "Someone") ?: "Someone"

            lifecycleScope.launch {
                NotificationSender.sendNotification(
                    fcmToken = creatorFcmToken,
                    title = "Your pin was liked",
                    body = "$senderUsername liked your pin '${pinTitleText.text}'",
                    notificationType = "LIKE",
                    pinId = pinId,
                    imageUrl = imageUrl
                )
            }
        }

    }

    private fun updateLikeIcon() {
        if (isLiked) {
            likeIcon.setImageResource(R.drawable.icon_heart_filled)
            likeIcon.setColorFilter(getColor(R.color.blue))
        } else {
            likeIcon.setImageResource(R.drawable.icon_heart)
            likeIcon.setColorFilter(getColor(R.color.blue))
        }
    }

    private fun downloadImage() {
        downloadBtn.isEnabled = false
        downloadBtn.alpha = 0.5f

        val request = DownloadManager.Request(Uri.parse(imageUrl))
            .setTitle("Moodly Pin")
            .setDescription("Downloading image...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_PICTURES, "Moodly_${System.currentTimeMillis()}.jpg")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadManager.enqueue(request)
        Toast.makeText(this, "Download started...", Toast.LENGTH_SHORT).show()

        downloadBtn.isEnabled = true
        downloadBtn.alpha = 1.0f
    }

    private fun setButtonsEnabled(enabled: Boolean) {
        likeBtn.isEnabled = enabled
        saveBtn.isEnabled = enabled
        downloadBtn.isEnabled = enabled
        optionsBtn.isEnabled = enabled

        // Optional: Change opacity to give visual feedback
        val alpha = if (enabled) 1.0f else 0.5f
        likeBtn.alpha = alpha
        saveBtn.alpha = alpha
        downloadBtn.alpha = alpha
        optionsBtn.alpha = alpha
    }
}