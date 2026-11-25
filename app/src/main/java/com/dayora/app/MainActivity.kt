package com.dayora.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dayora.app.data.local.DayoraDatabase
import com.dayora.app.data.local.entity.DailyEntryEntity
import com.dayora.app.ui.home.HomeScreen
import com.dayora.app.ui.theme.DayoraTheme
import com.dayora.app.util.DateDisplayFormatter
import kotlinx.coroutines.launch
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val dao = DayoraDatabase.getInstance(applicationContext).dailyEntryDao()

        setContent {
            DayoraTheme {
                val scope = rememberCoroutineScope()
                val todayIso = remember { DateDisplayFormatter.toStorageIso(LocalDate.now()) }
                val goodDayCount by dao.observeGoodDayCount().collectAsStateWithLifecycle(initialValue = 0)
                val firstDate by dao.observeFirstEntryIsoDate().collectAsStateWithLifecycle(initialValue = null)
                val todayEntry by dao.observeByDate(todayIso).collectAsStateWithLifecycle(initialValue = null)

                HomeScreen(
                    goodDayCount = goodDayCount,
                    firstEntryIsoDate = firstDate,
                    todayEntry = todayEntry,
                    onSaveEntry = { dayState, isGood, isRainRoom, reason, sparks ->
                        scope.launch {
                            dao.saveEntryWithSparks(
                                entry = DailyEntryEntity(
                                    localDate = todayIso,
                                    timezoneId = DateDisplayFormatter.currentTimezoneId(),
                                    dayState = dayState,
                                    isGoodDay = isGood,
                                    isRainRoom = isRainRoom,
                                    reasonText = reason.ifBlank { null }
                                ),
                                sparks = sparks
                            )
                        }
                    }
                )
            }
        }
    }
}
