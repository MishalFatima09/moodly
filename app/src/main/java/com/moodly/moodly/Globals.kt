package com.moodly.moodly

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import com.google.gson.Gson
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.ExistingWorkPolicy
import androidx.work.BackoffPolicy
import java.util.concurrent.TimeUnit

object Globals {
    const val prefs = "com.moodly.moodly.prefs"
    val gson: Gson = Gson()

    fun scheduleSyncWorker(context: Context) {
        val constraints = Constraints.Builder()
            // Run only when the device has an internet connection
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            // If the task fails, retry with an exponential backoff policy
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                10, // Initial delay of 10 seconds
                TimeUnit.SECONDS
            )
            .addTag("SYNC_OFFLINE_ACTIONS")
            .build()

        // Enqueue the work. REPLACE ensures only one sync job is ever pending or running.
        WorkManager.getInstance(context).enqueueUniqueWork(
            "OfflineSync",
            ExistingWorkPolicy.REPLACE, // Safely cancels and replaces any existing work with the same name
            syncRequest
        )
    }

    fun isInternetAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // Android M (API 23) and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false

            return when {
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
                else -> false
            }
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo
            return networkInfo != null && networkInfo.isConnected
        }
    }
}