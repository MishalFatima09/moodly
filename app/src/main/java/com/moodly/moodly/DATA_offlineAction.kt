package com.moodly.moodly

data class OfflineAction(
    val actionId: Int,
    val actionType: String,
    val payloadJson: String,
    val timestamp: Long
)