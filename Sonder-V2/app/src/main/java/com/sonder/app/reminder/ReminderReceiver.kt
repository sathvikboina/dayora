package com.sonder.app.reminder

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.sonder.app.MainActivity
import com.sonder.app.R
import com.sonder.app.SonderApp
import com.sonder.app.data.local.SonderDatabase
import com.sonder.app.data.prefs.SonderPreferences
import com.sonder.app.util.DateDisplayFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                val prefs = SonderPreferences(context)
                val settings = prefs.settings.first()
                if (!settings.reminderEnabled) return@launch

                val todayIso = DateDisplayFormatter.toStorageIso(LocalDate.now())
                val db = SonderDatabase.getInstance(context)
                val todayMoments = db.momentDao().observeMomentsForDate(todayIso).first()
                if (todayMoments.isEmpty() && canPostNotifications(context)) {
                    val openIntent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    val contentPending = PendingIntent.getActivity(
                        context,
                        9002,
                        openIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    val notification = NotificationCompat.Builder(context, SonderApp.CHANNEL_REMINDER)
                        .setSmallIcon(R.drawable.ic_notification)
                        .setContentTitle("A quiet moment before sleep?")
                        .setContentText("Leave a small note or let the day rest.")
                        .setContentIntent(contentPending)
                        .setAutoCancel(true)
                        .setPriority(NotificationCompat.PRIORITY_LOW)
                        .build()

                    NotificationManagerCompat.from(context).notify(9003, notification)
                }

                ReminderScheduler.schedule(context, settings.reminderHour, settings.reminderMinute)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }
}
