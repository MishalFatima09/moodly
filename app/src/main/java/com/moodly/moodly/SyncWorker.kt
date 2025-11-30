package com.moodly.moodly

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


class SyncWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    private val offlineDbHelper = OfflineDbHelper.getInstance(appContext)
    private val gson = Gson()
    private val TAG = "SyncWorker"

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {

        // 1. Connectivity check
        if (!Globals.isInternetAvailable(applicationContext)) {
            Log.d(TAG, "No network available. Retrying later.")
            return@withContext Result.retry()
        }

        // 2. Get all pending actions
        val pendingActions = offlineDbHelper.getPendingActions()

        if (pendingActions.isEmpty()) {
            Log.d(TAG, "No pending actions found.")
            return@withContext Result.success()
        }

        var allSucceeded = true

        for (action in pendingActions) {
            val success = processAction(action)

            if (success) {
                // 3. Delete the action from the local queue upon successful sync
                offlineDbHelper.deleteAction(action.actionId)
            } else {
                // If one action fails, mark the whole run for retry
                allSucceeded = false
                Log.e(TAG, "Failed to process action ${action.actionId}. Retaining in queue.")
            }
        }

        return@withContext if (allSucceeded) Result.success() else Result.retry()
    }

    /**
     * Executes the specific database operation based on the action type.
     */
    private suspend fun processAction(action: OfflineAction): Boolean {
        // Deserialize payload
        val payloadMap: Map<String, Any> = try {
            // Using a generic Map conversion for flexibility
            // NOTE: Gson maps numbers to Double by default, handle type casting carefully.
            gson.fromJson(action.payloadJson, Map::class.java) as Map<String, Any>
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse JSON for action ${action.actionId}", e)
            return true // Treat as success to delete malformed data and prevent perpetual retries
        }

        when (action.actionType) {
            // CORRECTED: Using payloadMap instead of the undefined 'payload'
            "PIN_CREATE" -> return syncCreatePin(payloadMap)
            "BOARD_CREATE" -> return syncCreateBoard(payloadMap) // <-- Added BOARD_CREATE handling
            "PIN_UPDATE" -> return syncUpdatePin(payloadMap)
            "PIN_DELETE" -> return syncDeletePin(payloadMap)
            "BOARD_UPDATE" -> return syncUpdateBoard(payloadMap) // <-- Added BOARD_UPDATE handling
            "BOARD_DELETE" -> return syncDeleteBoard(payloadMap) // <-- Added BOARD_DELETE handling
            "BOARD_PIN_REMOVE" -> return syncRemovePinFromBoard(payloadMap) // <-- Added BOARD_PIN_REMOVE handling

            else -> {
                Log.w(TAG, "Unknown action type: ${action.actionType}. Deleting.")
                return true // Unknown action, delete it to clear the queue
            }
        }
    }

    // --- Synchronization Implementations ---

    private suspend fun syncCreatePin(payload: Map<String, Any>): Boolean {
        // 1. Extract necessary data, handling type casting from Map<String, Any>
        val userId = payload["user_id"] as? String ?: return true
        val title = payload["title"] as? String ?: ""
        val description = payload["description"] as? String ?: ""
        val keywords = payload["keywords"] as? String ?: ""
        val base64Image = payload["image_base64"] as? String ?: return true
        val aspectRatio = (payload["aspect_ratio"] as? Number)?.toFloat() ?: 1.0f
        val boardId = payload["board_id"] as? String // Optional board to save to

        var imageUrl: String? = null

        // Step 1: Upload Image
        try {
            imageUrl = OnlineDbHelper.uploadImageSync(base64Image)
        } catch (e: Exception) {
            Log.e(TAG, "Image upload failed: ${e.message}")
            return false // Retry on image failure
        }
        if (imageUrl == null) return false // Retry if URL is null

        // Step 2: Insert Pin and get the new Pin ID
        val insertQuery = """
            INSERT INTO pins (user_id, title, description, keywords, image_url, aspect_ratio) 
            VALUES (?, ?, ?, ?, ?, ?)
            RETURNING pin_id
        """.trimIndent()
        val insertParams = listOf(userId, title, description, keywords, imageUrl, aspectRatio)

        val insertResult = OnlineDbHelper.executeSyncQuery(insertQuery, insertParams)

        val newPinId = insertResult.data?.rows?.firstOrNull()?.get("pin_id") as? String

        if (insertResult.status != 1 || newPinId == null) {
            Log.e(TAG, "Pin insertion failed: ${insertResult.error?.message}")
            return false // Retry on DB failure
        }

        // After successful online creation, save minimal pin details locally for board display
        offlineDbHelper.savePinDetails(newPinId, imageUrl, aspectRatio)

        // Step 3: Insert into Board (If specified)
        if (boardId != null) {
            val boardQuery = "INSERT INTO board_pins (board_id, pin_id) VALUES (?, ?)"
            val boardParams = listOf(boardId, newPinId)
            val boardResult = OnlineDbHelper.executeSyncQuery(boardQuery, boardParams)

            // If board save succeeds, save the link locally too.
            if (boardResult.status == 1) {
                offlineDbHelper.addPinBoardLink(boardId, newPinId)
            }
            // Failure to save to board is not critical enough to fail the entire pin creation, so we return true regardless.
        }

        return true // Success
    }

    // --- NEW BOARD CREATION SYNC ---
    private suspend fun syncCreateBoard(payload: Map<String, Any>): Boolean {
        val userId = payload["user_id"] as? String ?: return true
        val title = payload["title"] as? String ?: ""
        val description = payload["description"] as? String ?: ""

        val query = "INSERT INTO boards (user_id, title, description) VALUES (?, ?, ?) RETURNING board_id"
        val params = listOf(userId, title, description)

        val result = OnlineDbHelper.executeSyncQuery(query, params)

        val newBoardId = result.data?.rows?.firstOrNull()?.get("board_id") as? String

        if (result.status != 1 || newBoardId == null) {
            Log.e(TAG, "Board creation failed: ${result.error?.message}")
            return false // Retry
        }

        // Success: Add the newly created board to the local DB (with server-generated ID)
        offlineDbHelper.addBoard(newBoardId, userId, title, description)

        return true
    }

    // --- PIN UPDATE SYNC ---
    private suspend fun syncUpdatePin(payload: Map<String, Any>): Boolean {
        val pinId = payload["pin_id"] as? String ?: return true
        val title = payload["title"] as? String ?: ""
        val description = payload["description"] as? String ?: ""

        val query = "UPDATE pins SET title = ?, description = ? WHERE pin_id = ?"
        val params = listOf(title, description, pinId)

        val result = OnlineDbHelper.executeSyncQuery(query, params)

        // Success: Update the local copy immediately
        if (result.status == 1) {
            // Note: You might need a local function to update pin details beyond title/desc
            return true
        }
        return false
    }

    // --- BOARD UPDATE SYNC ---
    private suspend fun syncUpdateBoard(payload: Map<String, Any>): Boolean {
        val boardId = payload["board_id"] as? String ?: return true
        val title = payload["title"] as? String ?: ""
        val description = payload["description"] as? String ?: ""

        val query = "UPDATE boards SET title = ?, description = ? WHERE board_id = ?"
        val params = listOf(title, description, boardId)

        val result = OnlineDbHelper.executeSyncQuery(query, params)

        // Success: Update local copy immediately
        if (result.status == 1) {
            offlineDbHelper.updateBoardDetails(boardId, title, description)
            return true
        }
        return false
    }

    // --- PIN DELETE SYNC ---
    private suspend fun syncDeletePin(payload: Map<String, Any>): Boolean {
        val pinId = payload["pin_id"] as? String ?: return true

        val query = "DELETE FROM pins WHERE pin_id = ?"
        val params = listOf(pinId)

        val result = OnlineDbHelper.executeSyncQuery(query, params)

        // Note: We don't delete locally here because the local DB only holds minimal display data
        // and doesn't manage cascade deletion for external pins.
        // We rely on the server to handle the full deletion and the local app handles the queue.
        return result.status == 1
    }

    // --- BOARD DELETE SYNC ---
    private suspend fun syncDeleteBoard(payload: Map<String, Any>): Boolean {
        val boardId = payload["board_id"] as? String ?: return true

        val query = "DELETE FROM boards WHERE board_id = ?"
        val params = listOf(boardId)

        val result = OnlineDbHelper.executeSyncQuery(query, params)

        // Success: Perform the local cleanup
        if (result.status == 1) {
            offlineDbHelper.deleteBoardAndPins(boardId)
            return true
        }
        return false
    }

    // --- PIN REMOVE FROM BOARD SYNC ---
    private suspend fun syncRemovePinFromBoard(payload: Map<String, Any>): Boolean {
        val boardId = payload["board_id"] as? String ?: return true
        val pinId = payload["pin_id"] as? String ?: return true

        val query = "DELETE FROM board_pins WHERE board_id = ? AND pin_id = ?"
        val params = listOf(boardId, pinId)

        val result = OnlineDbHelper.executeSyncQuery(query, params)

        // Success: Perform the local cleanup
        if (result.status == 1) {
            offlineDbHelper.removePinBoardLink(boardId, pinId)
            return true
        }
        return false
    }
}