package com.moodly.moodly

data class DATA_PinDetails(
    val pinId: String,
    val title: String,
    val description: String,
    val keywords: String,
    val imageUrl: String,
    val aspectRatio: Float,
    val creatorId: String,
    val creatorUsername: String,
    val creationDate: String,
    val likeCount: Int,
    val isLiked: Boolean
    // Note: Local DB might not need all fields, but the Update/Delete queue needs the IDs.
)