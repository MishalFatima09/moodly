package com.moodly.moodly

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object NotificationSender {
    val url = URL("https://socially-notification-server.vercel.app/send-notification")
    suspend fun sendNotification(
        fcmToken: String,
        title: String,
        body: String,
        notificationType: String,
        pinId: String,
        imageUrl: String?
    ) {
        withContext(Dispatchers.IO) {
            try {
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.doOutput = true

                // Create the main JSON payload
                val jsonPayload = JSONObject()
                jsonPayload.put("fcmToken", fcmToken)
                jsonPayload.put("title", title)
                jsonPayload.put("body", body)
                jsonPayload.put("notificationType", notificationType)
                jsonPayload.put("pinId", pinId)
                jsonPayload.put("imageUrl", imageUrl)

                val writer = OutputStreamWriter(conn.outputStream)
                writer.write(jsonPayload.toString())
                writer.flush()
                writer.close()

                conn.responseCode
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}


