package com.sonder.app.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sonder.app.data.prefs.SonderPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class RescheduleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action in setOf(
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_TIME_SET,
                Intent.ACTION_TIMEZONE_CHANGED,
                Intent.ACTION_MY_PACKAGE_REPLACED
            )
        ) {
            val pendingResult = goAsync()
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            scope.launch {
                try {
                    val settings = SonderPreferences(context).settings.first()
                    if (settings.reminderEnabled) {
                        ReminderScheduler.schedule(context, settings.reminderHour, settings.reminderMinute)
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
