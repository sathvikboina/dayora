package com.sonder.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.sonder.app.MainActivity
import com.sonder.app.R
import com.sonder.app.data.local.SonderDatabase
import com.sonder.app.data.local.entity.EmergentDayWeather
import com.sonder.app.util.DateDisplayFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * A deliberately quiet home-screen widget: today's emergent weather and the
 * number of Moments kept, never entry text. Uses classic RemoteViews so the
 * base APK stays lean (no Glance dependency).
 *
 * Refreshes every 30 minutes (`updatePeriodMillis`) and immediately whenever
 * the app saves, deletes or opens something.
 */
class SonderWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                updateAll(context, appWidgetManager, appWidgetIds)
            } finally {
                pending.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val pending = goAsync()
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    val manager = AppWidgetManager.getInstance(context)
                    val ids = manager.getAppWidgetIds(
                        ComponentName(context, SonderWidgetProvider::class.java)
                    )
                    if (ids.isNotEmpty()) updateAll(context, manager, ids)
                } finally {
                    pending.finish()
                }
            }
        }
    }

    private suspend fun updateAll(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val dao = SonderDatabase.getInstance(context).momentDao()
        val today = DateDisplayFormatter.toStorageIso(LocalDate.now())
        val moments = withContext(Dispatchers.IO) {
            runCatching { dao.observeMomentsForDate(today).first() }.getOrDefault(emptyList())
        }
        val weather = EmergentDayWeather.derive(moments)
        val count = moments.size

        val launchIntent = PendingIntent.getActivity(
            context,
            REQUEST_LAUNCH,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        appWidgetIds.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget_sonder)
            val weatherLabel = weather?.label ?: context.getString(R.string.widget_quiet_day)
            val countLabel = when (count) {
                0 -> context.getString(R.string.widget_no_moments)
                1 -> "1 moment today"
                else -> "$count moments today"
            }
            views.setTextViewText(R.id.widget_weather, weatherLabel)
            views.setTextViewText(R.id.widget_count, countLabel)
            views.setOnClickPendingIntent(R.id.widget_root, launchIntent)
            appWidgetManager.updateAppWidget(id, views)
        }
    }

    companion object {
        private const val ACTION_REFRESH = "com.sonder.app.widget.REFRESH"
        private const val REQUEST_LAUNCH = 8801

        /** Fire-and-forget refresh; safe to call from the main thread. */
        fun refresh(context: Context) {
            runCatching {
                context.sendBroadcast(
                    Intent(context, SonderWidgetProvider::class.java)
                        .setAction(ACTION_REFRESH)
                )
            }
        }
    }
}
