package com.moodly.moodly

data class DATA_Pin(
    val pinId: String,
    val imageUrl : String,
    val aspectRatio: Float = 1.0f,
    var title: String? = null,
    var description: String? = null,
    var keywords: String? = null
)

