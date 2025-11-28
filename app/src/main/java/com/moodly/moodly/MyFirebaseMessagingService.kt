package com.moodly.moodly

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.bumptech.glide.Glide
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import java.io.IOException

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM_TOKEN", "New token generated: $token")
        // Pass the application context to the function
        sendTokenToServer(token, applicationContext)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d("FCM_TEST", "Message received from: ${remoteMessage.from}")
        val data = remoteMessage.data
        val notificationType = data["notificationType"]
        val title = data["title"]
        val body = data["body"]
        val pinId = data["pinId"]
        val image_url = data["imageUrl"]
        if (title != null && body != null) {
            showNotification(title, body, notificationType, pinId, image_url)
        }
    }

    private fun showNotification(title: String, body: String, notificationType: String?, pinId: String?, image_url: String?) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "moodly_notification_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Moodly Notifications", NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(channel)
        }

        val prefs = getSharedPreferences(Globals.prefs, Context.MODE_PRIVATE)
        val currentUserId = prefs.getString("user_id", "") ?: ""
        if (currentUserId.isEmpty()) return

        val resultIntent = when (notificationType) {
            "LIKE", "SAVE" -> {
                Intent(this, PinDetails::class.java).apply {
                    putExtra("pin_id", pinId)
                }
            }
            else -> {
                Intent(this, HomeScreen::class.java)
            }
        }
        resultIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)

        val pendingIntent = PendingIntent.getActivity(this, 0, resultIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)

        if (!image_url.isNullOrEmpty()) {
            try {
                val bitmap: Bitmap = Glide.with(this)
                    .asBitmap()
                    .load(image_url)
                    .submit()
                    .get()
                notificationBuilder.setLargeIcon(bitmap)
            } catch (e: IOException) {
                Log.e("FCM_Notification", "Failed to load notification image: ${e.message}")
            }
        }

        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }

    companion object {
        fun sendTokenToServer(token: String?, context: Context) {
            if (token == null) return

            val prefs = context.getSharedPreferences(Globals.prefs, Context.MODE_PRIVATE)
            val currentUserId = prefs.getString("user_id", "") ?: ""

            if (currentUserId.isEmpty()) {
                Log.w("FCM_TOKEN", "User ID not found in SharedPreferences. Token not sent.")
                return
            }

            val query = "UPDATE users SET fcm_token = ? WHERE user_id = ?"
            val params = listOf(token, currentUserId.toString())

            OnlineDbHelper.executeQuery(query, params) { response, exception ->
                if (exception != null) {
                    Log.e("FCM_TOKEN", "Failed to update token for user: $currentUserId", exception)
                    return@executeQuery
                }

                if (response?.status == 1) {
                    Log.d("FCM_TOKEN", "Token successfully updated for user: $currentUserId")
                } else {
                    Log.e("FCM_TOKEN", "Failed to update token: ${response?.error?.message}")
                }
            }
        }
    }
}
