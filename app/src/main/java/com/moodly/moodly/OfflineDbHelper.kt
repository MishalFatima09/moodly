package com.moodly.moodly

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log

class OfflineDbHelper private constructor(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    // --- Companion Object / Constants ---
    companion object {
        private const val DATABASE_NAME = "MoodlyOffline.db"
        private const val DATABASE_VERSION = 1
        private const val TAG = "OfflineDbHelper"

        // Table and Column Names
        // 1. PIN_LIKES table (to track current user's liked pins for offline UI)
        private const val TABLE_LIKES = "pin_likes"
        private const val COL_USER_ID = "user_id"
        private const val COL_PIN_ID = "pin_id"
        private const val COL_LIKED_AT = "liked_at"

        // 2. OFFLINE_ACTIONS table (for queuing sync operations)
        private const val TABLE_ACTIONS = "offline_actions"
        private const val COL_ACTION_ID = "action_id"
        private const val COL_ACTION_TYPE = "action_type"
        private const val COL_ACTION_PAYLOAD = "payload_json"
        private const val COL_ACTION_TIMESTAMP = "timestamp"

        // 3. BOARD_PINS table (to track local pin-board links for offline visibility)
        private const val TABLE_BOARD_PINS = "board_pins" // Use constant here
        private const val COL_BOARD_ID = "board_id" // New column constant

        private const val TABLE_BOARDS = "boards"
        private const val COL_TITLE = "title"
        private const val COL_DESCRIPTION = "description"
        private const val COL_COVER_URL = "cover_image_url"
        private const val COL_CREATED_AT = "created_at"

        private const val TABLE_PINS = "pins"
        private const val COL_IMAGE_URL = "image_url"
        private const val COL_ASPECT_RATIO = "aspect_ratio"


        @Volatile
        private var INSTANCE: OfflineDbHelper? = null

        fun getInstance(context: Context): OfflineDbHelper =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: OfflineDbHelper(context.applicationContext).also { INSTANCE = it }
            }
    }

    // --- Database Creation ---

    override fun onCreate(db: SQLiteDatabase) {
        // 1. Create PIN_LIKES table
        val CREATE_LIKES_TABLE = """
            CREATE TABLE $TABLE_LIKES (
                $COL_USER_ID TEXT NOT NULL,
                $COL_PIN_ID TEXT NOT NULL,
                $COL_LIKED_AT INTEGER DEFAULT (STRFTIME('%s', 'now')),
                PRIMARY KEY ($COL_USER_ID, $COL_PIN_ID)
            )
        """.trimIndent()
        db.execSQL(CREATE_LIKES_TABLE)

        // 2. Create OFFLINE_ACTIONS table
        val CREATE_ACTIONS_TABLE = """
            CREATE TABLE $TABLE_ACTIONS (
                $COL_ACTION_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_ACTION_TYPE TEXT NOT NULL, 
                $COL_ACTION_PAYLOAD TEXT NOT NULL,
                $COL_ACTION_TIMESTAMP INTEGER DEFAULT (STRFTIME('%s', 'now'))
            )
        """.trimIndent()
        db.execSQL(CREATE_ACTIONS_TABLE)

        // 3. Create BOARD_PINS table (The missing table)
        val CREATE_BOARD_PINS_TABLE = """
            CREATE TABLE $TABLE_BOARD_PINS (
                $COL_BOARD_ID TEXT NOT NULL,
                $COL_PIN_ID TEXT NOT NULL,
                PRIMARY KEY ($COL_BOARD_ID, $COL_PIN_ID)
            )
        """.trimIndent()
        db.execSQL(CREATE_BOARD_PINS_TABLE)

        //4. Create BOARDS table
        val CREATE_BOARDS_TABLE = """
        CREATE TABLE $TABLE_BOARDS (
            $COL_BOARD_ID TEXT PRIMARY KEY,
            $COL_USER_ID TEXT NOT NULL,
            $COL_TITLE TEXT NOT NULL,
            $COL_DESCRIPTION TEXT,
            $COL_COVER_URL TEXT,
            $COL_CREATED_AT INTEGER DEFAULT (STRFTIME('%s', 'now'))
        )
    """.trimIndent()
        db.execSQL(CREATE_BOARDS_TABLE)

        val CREATE_PINS_TABLE = """
            CREATE TABLE $TABLE_PINS (
                $COL_PIN_ID TEXT PRIMARY KEY,
                $COL_IMAGE_URL TEXT NOT NULL,
                $COL_ASPECT_RATIO REAL DEFAULT 1.0
            )
        """.trimIndent()
        db.execSQL(CREATE_PINS_TABLE)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Simple upgrade strategy: drop tables and recreate them
        db.execSQL("DROP TABLE IF EXISTS $TABLE_LIKES")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_ACTIONS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_BOARDS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_BOARD_PINS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_PINS")
        onCreate(db)
    }

    fun savePinDetails(pinId: String, imageUrl: String, aspectRatio: Float) {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COL_PIN_ID, pinId)
            put(COL_IMAGE_URL, imageUrl)
            put(COL_ASPECT_RATIO, aspectRatio)
        }
        // Use REPLACE to update existing pins (like if the aspect ratio was wrong)
        db.insertWithOnConflict(TABLE_PINS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    /**
     * Loads all pin previews associated with a specific board ID from local storage.
     */
    fun loadBoardPinsLocal(boardId: String): List<DATA_Pin> {
        val pinsList = mutableListOf<DATA_Pin>()
        val db = this.readableDatabase

        // Join BOARD_PINS (junction) with PINS (data)
        val query = """
            SELECT p.$COL_PIN_ID, p.$COL_IMAGE_URL, p.$COL_ASPECT_RATIO 
            FROM $TABLE_PINS p
            INNER JOIN $TABLE_BOARD_PINS bp ON p.$COL_PIN_ID = bp.$COL_PIN_ID
            WHERE bp.$COL_BOARD_ID = ?
        """
        val cursor = db.rawQuery(query, arrayOf(boardId))

        with(cursor) {
            while (moveToNext()) {
                val pinId = getString(getColumnIndexOrThrow(COL_PIN_ID))
                val imageUrl = getString(getColumnIndexOrThrow(COL_IMAGE_URL))
                val ratio = getFloat(getColumnIndexOrThrow(COL_ASPECT_RATIO))

                pinsList.add(DATA_Pin(pinId, imageUrl, ratio))
            }
        }
        cursor.close()
        return pinsList
    }

    // --- BOARD Management Functions (Existing/Updated) ---

    fun addBoard(boardId: String, userId: String, title: String, description: String, createdAt: Long = System.currentTimeMillis() / 1000) {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COL_BOARD_ID, boardId)
            put(COL_USER_ID, userId)
            put(COL_TITLE, title)
            put(COL_DESCRIPTION, description)
            put(COL_CREATED_AT, createdAt)
        }
        db.insertWithOnConflict(TABLE_BOARDS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
        Log.d(TAG, "Local board added: $boardId ($title)")
    }

    /**
     * Updates the title and description of a board in the local DB.
     */
    fun updateBoardDetails(boardId: String, newTitle: String, newDescription: String) {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COL_TITLE, newTitle)
            put(COL_DESCRIPTION, newDescription)
        }
        db.update(
            TABLE_BOARDS,
            values,
            "$COL_BOARD_ID = ?",
            arrayOf(boardId)
        )
        Log.d(TAG, "Local board details updated for: $boardId")
    }

    fun loadUserBoards(userId: String): List<DATA_Board> {
        val boardsList = mutableListOf<DATA_Board>()
        val db = this.readableDatabase

        val query = """
            SELECT 
                b.$COL_BOARD_ID, 
                b.$COL_TITLE, 
                b.$COL_DESCRIPTION, 
                b.$COL_COVER_URL, 
                COUNT(bp.$COL_PIN_ID) as pin_count
            FROM $TABLE_BOARDS b
            LEFT JOIN $TABLE_BOARD_PINS bp ON b.$COL_BOARD_ID = bp.$COL_BOARD_ID
            WHERE b.$COL_USER_ID = ?
            GROUP BY b.$COL_BOARD_ID, b.$COL_TITLE, b.$COL_DESCRIPTION, b.$COL_COVER_URL
            ORDER BY b.$COL_CREATED_AT DESC
        """
        val cursor = db.rawQuery(query, arrayOf(userId))

        with(cursor) {
            while (moveToNext()) {
                val boardId = getString(getColumnIndexOrThrow(COL_BOARD_ID))
                val title = getString(getColumnIndexOrThrow(COL_TITLE))
                val description = getString(getColumnIndexOrThrow(COL_DESCRIPTION))
                val coverUrl = getString(getColumnIndexOrThrow(COL_COVER_URL)) ?: ""
                val pinCount = getInt(getColumnIndexOrThrow("pin_count"))

                boardsList.add(DATA_Board(boardId, coverUrl, title, description, pinCount))
            }
        }
        cursor.close()
        return boardsList
    }

    /**
     * Deletes a board and all its associated pin links locally.
     */
    fun deleteBoardAndPins(boardId: String) {
        val db = this.writableDatabase
        db.beginTransaction()
        try {
            // 1. Delete all links associated with the board
            db.delete(
                TABLE_BOARD_PINS,
                "$COL_BOARD_ID = ?",
                arrayOf(boardId)
            )

            // 2. Delete the board record itself
            db.delete(
                TABLE_BOARDS,
                "$COL_BOARD_ID = ?",
                arrayOf(boardId)
            )

            db.setTransactionSuccessful()
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting local board: $boardId", e)
        } finally {
            db.endTransaction()
        }
    }

    fun addPinBoardLink(boardId: String, pinId: String) {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COL_BOARD_ID, boardId)
            put(COL_PIN_ID, pinId)
        }
        db.insertWithOnConflict(TABLE_BOARD_PINS, null, values, SQLiteDatabase.CONFLICT_IGNORE)
        Log.d(TAG, "Local pin saved to board: $pinId -> $boardId")
    }

    /**
     * Removes a pin's link from a specific board locally.
     */
    fun removePinBoardLink(boardId: String, pinId: String) {
        val db = this.writableDatabase
        val rowsDeleted = db.delete(
            TABLE_BOARD_PINS,
            "$COL_BOARD_ID = ? AND $COL_PIN_ID = ?",
            arrayOf(boardId, pinId)
        )
        Log.d(TAG, "Removed $rowsDeleted link(s) locally: $pinId from $boardId")
    }






























    // --- PIN_LIKES Management Functions---

    /**
     * Adds a pin/user combination to the local PIN_LIKES table.
     */
    fun addLikedPin(userId: String, pinId: String) {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COL_USER_ID, userId)
            put(COL_PIN_ID, pinId)
        }
        db.insertWithOnConflict(TABLE_LIKES, null, values, SQLiteDatabase.CONFLICT_IGNORE)
        Log.d(TAG, "Local liked pin added: $pinId")
    }

    /**
     * Removes a pin/user combination from the local PIN_LIKES table.
     */
    fun removeLikedPin(userId: String, pinId: String) {
        val db = this.writableDatabase
        db.delete(
            TABLE_LIKES,
            "$COL_USER_ID = ? AND $COL_PIN_ID = ?",
            arrayOf(userId, pinId)
        )
        Log.d(TAG, "Local liked pin removed: $pinId")
    }

    /**
     * Checks if a pin is locally liked by the user.
     */
    fun isPinLiked(userId: String, pinId: String): Boolean {
        val db = this.readableDatabase
        val cursor = db.query(
            TABLE_LIKES,
            arrayOf(COL_PIN_ID),
            "$COL_USER_ID = ? AND $COL_PIN_ID = ?",
            arrayOf(userId, pinId),
            null, null, null
        )
        val isLiked = cursor.count > 0
        cursor.close()
        return isLiked
    }


    // --- OFFLINE_ACTIONS Queue Functions ---

    /**
     * Queues an action (e.g., pin creation, board creation) to be synced later.
     */
    fun queueOfflineAction(actionType: String, payloadJson: String) {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COL_ACTION_TYPE, actionType)
            put(COL_ACTION_PAYLOAD, payloadJson)
        }
        val newRowId = db.insert(TABLE_ACTIONS, null, values)
        Log.d(TAG, "Action queued. ID: $newRowId, Type: $actionType")
    }

    fun getPendingActions(): List<OfflineAction> {
        val actions = mutableListOf<OfflineAction>()
        val db = this.readableDatabase
        val cursor = db.query(
            TABLE_ACTIONS,
            arrayOf(COL_ACTION_ID, COL_ACTION_TYPE, COL_ACTION_PAYLOAD, COL_ACTION_TIMESTAMP),
            null, null, null, null, "$COL_ACTION_TIMESTAMP ASC" // Process oldest first
        )

        with(cursor) {
            while (moveToNext()) {
                actions.add(OfflineAction(
                    getInt(getColumnIndexOrThrow(COL_ACTION_ID)),
                    getString(getColumnIndexOrThrow(COL_ACTION_TYPE)),
                    getString(getColumnIndexOrThrow(COL_ACTION_PAYLOAD)),
                    getLong(getColumnIndexOrThrow(COL_ACTION_TIMESTAMP))
                ))
            }
        }
        cursor.close()
        return actions
    }

    /**
     * Deletes a processed action from the queue.
     */
    fun deleteAction(actionId: Int) {
        val db = this.writableDatabase
        db.delete(
            TABLE_ACTIONS,
            "$COL_ACTION_ID = ?",
            arrayOf(actionId.toString())
        )
        Log.d(TAG, "Deleted processed action ID: $actionId")
    }


}