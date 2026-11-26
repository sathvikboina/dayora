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

/**
 * Re-arms the evening reminder after reboot, time changes, timezone changes,
 * and app updates (Indian OEMs aggressively clear alarms otherwise).
 */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val settings = SonderPreferences(context).settings.first()
                if (settings.reminderEnabled) {
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
}
