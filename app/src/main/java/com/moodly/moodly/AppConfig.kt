package com.moodly.moodly

object AppConfig {

    //--------- REPLACE WITH YOUR LOCAL IP ADDRESS ---------
    //const val PC_IP = "192.168.96.35"
    const val PC_IP = "192.168.18.28"
    //------------------------------------------------------

    const val SQL_API_PATH = "Moodly-Api/runSql.php"
    const val UPLOAD_API_PATH = "Moodly-Api/uploadImage.php"

    val SQL_API_URL get() = "http://$PC_IP/$SQL_API_PATH"
    val UPLOAD_API_URL get() = "http://$PC_IP/$UPLOAD_API_PATH"
}
