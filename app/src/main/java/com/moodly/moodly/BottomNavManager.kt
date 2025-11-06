package com.moodly.moodly

import android.content.Context
import android.content.Intent
import com.google.android.material.bottomnavigation.BottomNavigationView

object BottomNavManager {

    var currentSelectedId = R.id.nav_home
    fun setup(context: Context, bottomNavView: BottomNavigationView) {
        bottomNavView.isSaveEnabled = false
        bottomNavView.setOnItemReselectedListener { /* Do nothing to prevent re-instantiating the same activity */ }
        bottomNavView.setOnItemSelectedListener { item ->
            val currentActivity = context::class.java
            var targetActivity: Class<*>? = null

            when (item.itemId) {
                R.id.nav_home -> {
                    if (currentActivity != HomeScreen::class.java) {
                        targetActivity = HomeScreen::class.java
                        currentSelectedId = R.id.nav_home
                    }
                }
                R.id.nav_boards -> {
                    if (currentActivity != Boards::class.java) {
                        targetActivity = Boards::class.java
                        currentSelectedId = R.id.nav_boards
                    }
                }
                R.id.nav_create -> {
                    if (currentActivity != CreatePin::class.java) {
                        targetActivity = CreatePin::class.java
                        currentSelectedId = R.id.nav_create
                    }
                }
                R.id.nav_notifications -> {
                    if (currentActivity != Notifications::class.java) {
                        targetActivity = Notifications::class.java
                        currentSelectedId = R.id.nav_notifications
                    }
                }
                R.id.nav_profile -> {
                    if (currentActivity != Profile::class.java) {
                        targetActivity = Profile::class.java
                        currentSelectedId = R.id.nav_profile
                    }
                }
            }

            if (targetActivity != null) {
                val intent = Intent(context, targetActivity)
                intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                context.startActivity(intent)
                return@setOnItemSelectedListener false
            }
            return@setOnItemSelectedListener false
        }
    }
}
