package com.sonder.app.reminder

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.sonder.app.MainActivity
import com.sonder.app.R
import com.sonder.app.SonderApp
import com.sonder.app.data.local.SonderDatabase
import com.sonder.app.data.local.entity.OpenWhenEnvelopeEntity
import com.sonder.app.data.prefs.SonderPreferences
import com.sonder.app.util.DateDisplayFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val prefs = SonderPreferences(context)
                val settings = prefs.settings.first()
                if (settings.reminderEnabled) {
                    val today = DateDisplayFormatter.toStorageIso(LocalDate.now())
                    val dao = SonderDatabase.getInstance(context).momentDao()
                    val momentCount = dao.countMomentsForDate(today)
                    if (momentCount == 0) {
                        notifyQuietly(context)
                    }
                    notifyReadyEnvelopes(context, prefs, settings.lastEnvelopeNoticeIso, today, dao.getAllEnvelopes())
                    ReminderScheduler.schedule(
                        context,
                        settings.reminderHour,
                        settings.reminderMinute
                    )
                }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun notifyReadyEnvelopes(
        context: Context,
        prefs: SonderPreferences,
        lastNoticeIso: String?,
        todayIso: String,
        envelopes: List<OpenWhenEnvelopeEntity>
    ) {
        if (lastNoticeIso == todayIso) return
        val ready = envelopes.filter { !it.isOpened && it.unlockDate <= todayIso }
        if (ready.isEmpty()) return
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val openIntent = PendingIntent.getActivity(
            context,
            1,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val text = if (ready.size == 1) {
            "From ${DateDisplayFormatter.isoToDisplayString(ready.first().unlockDate)}"
        } else {
            "${ready.size} sealed notes are waiting"
        }
        val notification = NotificationCompat.Builder(context, SonderApp.CHANNEL_REMINDER)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("An envelope is ready to open")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(4211, notification)
        prefs.setLastEnvelopeNoticeIso(todayIso)
    }

    private fun notifyQuietly(context: Context) {        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val openIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, SonderApp.CHANNEL_REMINDER)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("A quiet moment for today")
            .setContentText("Set the day down before it slips away.")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(4101, notification)
    }
}
