package com.sonder.app.ui.home

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonder.app.data.local.entity.DailyEntryEntity
import com.sonder.app.ui.components.DayStateMarker
import com.sonder.app.ui.components.LivingAuraBackground
import com.sonder.app.ui.components.ScreenHeader
import com.sonder.app.ui.theme.SonderPalette
import com.sonder.app.ui.theme.TimeOfDayMood
import com.sonder.app.util.DateDisplayFormatter
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Composable
fun HomeScreen(
    goodDayCount: Int,
    firstEntryIsoDate: String?,
    todayEntry: DailyEntryEntity?,
    todayIso: String,
    whisper: DailyEntryEntity?,
    onCapture: (LocalDate) -> Unit,
    onOpenSettings: () -> Unit
) {
    val mood = remember { TimeOfDayMood.current() }
    val haptics = LocalHapticFeedback.current
    val animatedCount by animateIntAsState(
        targetValue = goodDayCount,
        animationSpec = tween(durationMillis = 900),
        label = "good_day_counter"
    )
    var previousCount by remember { mutableIntStateOf(goodDayCount) }

    LaunchedEffect(goodDayCount) {
        if (goodDayCount > previousCount) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        previousCount = goodDayCount
    }

    LivingAuraBackground(mood = mood) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 26.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ScreenHeader(
                title = "",
                subtitle = null,
                onSettings = onOpenSettings
            )
            Spacer(modifier = Modifier.height(36.dp))

            Text(
                text = "SONDER",
                style = MaterialTheme.typography.labelLarge,
                letterSpacing = 5.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = animatedCount.toString(),
                fontSize = 104.sp,
                fontWeight = FontWeight.ExtraLight,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (firstEntryIsoDate != null) {
                    val days = runCatching {
                        ChronoUnit.DAYS.between(
                            DateDisplayFormatter.fromStorageIso(firstEntryIsoDate),
                            LocalDate.now()
                        ) + 1
                    }.getOrDefault(1L)
                    "Collecting since ${DateDisplayFormatter.isoToDisplayString(firstEntryIsoDate)} · $days days"
                } else {
                    "A living vault for the days that felt good"
                },
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
            )

            Spacer(modifier = Modifier.height(40.dp))
            Button(
                onClick = { onCapture(LocalDate.now()) },
                modifier = Modifier.fillMaxWidth(0.78f),
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    todayEntry?.dayState?.let {
                        DayStateMarker(state = it, counted = todayEntry.isGoodDay, size = 18.dp)
                        Spacer(modifier = Modifier.padding(horizontal = 5.dp))
                    }
                    Text(
                        text = todayEntry?.dayState?.let { "Today: ${it.label} · Edit" }
                            ?: "Capture today"
                    )
                }
            }

            if (todayEntry == null) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "No streak to keep. Missing a day is just quiet space.",
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f)
                )
            }

            if (whisper != null && whisper.localDate != todayIso) {
                Spacer(modifier = Modifier.height(34.dp))
                WhisperCard(whisper)
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
private fun WhisperCard(whisper: DailyEntryEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DayStateMarker(state = whisper.dayState, counted = true, size = 16.dp)
                Spacer(modifier = Modifier.padding(horizontal = 5.dp))
                Text(
                    text = "A MEMORY · ${DateDisplayFormatter.isoToDisplayString(whisper.localDate)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = SonderPalette.AmberGold
                )
            }
            Text(
                text = "\u201C${whisper.reasonText}\u201D",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
