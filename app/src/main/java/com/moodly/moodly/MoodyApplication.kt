package com.moodly.moodly

import android.app.Application
import com.moodly.moodly.Globals.scheduleSyncWorker

class MoodlyApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Call 1: Runs every time the app process starts to check for pending work.
        scheduleSyncWorker(this)
    }
}