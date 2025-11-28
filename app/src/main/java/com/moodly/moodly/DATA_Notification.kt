package com.moodly.moodly

data class DATA_Notification(
    val notificationId: String,
    val type: String,        // "LIKE" or "SAVE"
    val senderName: String,
    val senderPfp: String,
    val pinId: String,
    val pinImageUrl: String,
    val pinAspectRatio: Float,
    val createdAt: String    // Raw timestamp
)