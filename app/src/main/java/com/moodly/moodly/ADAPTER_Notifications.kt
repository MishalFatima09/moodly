package com.moodly.moodly

import android.content.Intent
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class ADAPTER_Notifications(
    private val notifications: ArrayList<DATA_Notification>,
    private val onItemClicked: (DATA_Notification) -> Unit
) : RecyclerView.Adapter<ADAPTER_Notifications.NotificationViewHolder>() {

    inner class NotificationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val senderPfp: ImageView = itemView.findViewById(R.id.iv_sender_pfp)
        val message: TextView = itemView.findViewById(R.id.tv_notif_message)
        val time: TextView = itemView.findViewById(R.id.tv_notif_time)
        val pinPreview: ImageView = itemView.findViewById(R.id.iv_pin_preview)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notification, parent, false)
        return NotificationViewHolder(view)
    }

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        val notif = notifications[position]
        val context = holder.itemView.context

        // Construct Message
        val actionText = if (notif.type == "SAVE") "saved" else "liked"
        val htmlText = "<b>${notif.senderName}</b> $actionText your pin."
        holder.message.text = Html.fromHtml(htmlText, Html.FROM_HTML_MODE_LEGACY)

        // Calculate Time Ago
        holder.time.text = getTimeAgo(notif.createdAt)

        // Load Sender PFP
        Glide.with(context)
            .load(notif.senderPfp)
            .circleCrop()
            .placeholder(R.drawable.pfp_placeholder)
            .into(holder.senderPfp)

        // Load Pin Preview
        Glide.with(context)
            .load(notif.pinImageUrl)
            .centerCrop()
            .into(holder.pinPreview)

        // Click Listener -> Navigate to Pin Details
        holder.itemView.setOnClickListener {
            onItemClicked(notif)
        }
    }

    override fun getItemCount() = notifications.size

    // Helper to convert "2023-10-25 14:30:00" to "5 mins ago"
    private fun getTimeAgo(dateString: String): String {
        try {
            val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            format.timeZone = TimeZone.getTimeZone("UTC")
            val past = format.parse(dateString) ?: return "Just now"
            val now = Date()

            val seconds = TimeUnit.MILLISECONDS.toSeconds(now.time - past.time)
            val minutes = TimeUnit.MILLISECONDS.toMinutes(now.time - past.time)
            val hours = TimeUnit.MILLISECONDS.toHours(now.time - past.time)
            val days = TimeUnit.MILLISECONDS.toDays(now.time - past.time)

            return when {
                seconds < 60 -> "Just now"
                minutes < 60 -> "${minutes}m ago"
                hours < 24 -> "${hours}h ago"
                else -> "$days d ago"
            }
        } catch (e: Exception) {
            return "Recently"
        }
    }

    // Helper to remove item from list (for Swipe logic)
    fun removeItem(position: Int) {
        if (position >= 0 && position < notifications.size) {
            notifications.removeAt(position)
            notifyItemRemoved(position)
        }
    }
}