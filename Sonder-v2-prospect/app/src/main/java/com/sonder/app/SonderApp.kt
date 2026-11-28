package com.sonder.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class SonderApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_REMINDER,
            "Quiet evening reminder",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "One optional, gentle evening nudge. No streaks, no guilt."
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_REMINDER = "sonder_reminder"
    }
}
