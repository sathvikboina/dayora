package com.sonder.app.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonder.app.data.local.entity.DailyEntryEntity
import com.sonder.app.data.local.entity.DayState
import com.sonder.app.ui.components.DayStateMarker
import com.sonder.app.ui.components.LivingAuraBackground
import com.sonder.app.ui.components.StateLegend
import com.sonder.app.ui.theme.TimeOfDayMood
import com.sonder.app.util.DateDisplayFormatter
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

private val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")

@Composable
fun CalendarScreen(
    entries: List<DailyEntryEntity>,
    onDayClick: (LocalDate) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenRainRoom: () -> Unit
) {
    val mood = remember { TimeOfDayMood.current() }
    var month by remember { mutableStateOf(YearMonth.now()) }
    var showHeavy by remember { mutableStateOf(false) }
    val entryByDate = remember(entries) { entries.associateBy { it.localDate } }
    var dragTotal by remember { mutableStateOf(0f) }
    val today = LocalDate.now()

    LivingAuraBackground(mood = mood) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onOpenRainRoom) {
                    Icon(
                        imageVector = Icons.Outlined.WaterDrop,
                        contentDescription = "The Rain Room",
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f)
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "CALENDAR",
                    style = MaterialTheme.typography.labelLarge,
                    letterSpacing = 3.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { month = month.minusMonths(1) }) {
                    Icon(
                        Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                        contentDescription = "Previous month"
                    )
                }
                Text(
                    text = month.format(monthFormatter),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { month = month.plusMonths(1) }) {
                    Icon(
                        Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = "Next month"
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            WeekdayHeader()
            Spacer(modifier = Modifier.height(6.dp))

            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.60f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragStart = { dragTotal = 0f },
                            onDragEnd = {
                                if (dragTotal > 150.dp.toPx()) onOpenRainRoom()
                                dragTotal = 0f
                            }
                        ) { change, amount ->
                            dragTotal += amount
                            change.consume()
                        }
                    }
            ) {
                MonthGrid(
                    month = month,
                    today = today,
                    entryByDate = entryByDate,
                    showHeavy = showHeavy,
                    onDayClick = onDayClick
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            StateLegend()
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Pull down for the Rain Room",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                )
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = { showHeavy = !showHeavy }) {
                    Text(
                        text = if (showHeavy) "Hide heavy days" else "Show heavy days",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
private fun WeekdayHeader() {
    val days = listOf("M", "T", "W", "T", "F", "S", "S")
    Row(modifier = Modifier.fillMaxWidth()) {
        days.forEach { day ->
            Text(
                text = day,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f)
            )
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    today: LocalDate,
    entryByDate: Map<String, DailyEntryEntity>,
    showHeavy: Boolean,
    onDayClick: (LocalDate) -> Unit
) {
    val firstOffset = month.atDay(1).dayOfWeek.value - 1
    val daysInMonth = month.lengthOfMonth()
    val totalCells = ((firstOffset + daysInMonth + 6) / 7) * 7

    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp)) {
        for (row in 0 until totalCells / 7) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (column in 0 until 7) {
                    val cellIndex = row * 7 + column
                    val dayNumber = cellIndex - firstOffset + 1
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (dayNumber in 1..daysInMonth) {
                            val date = month.atDay(dayNumber)
                            val entry = entryByDate[DateDisplayFormatter.toStorageIso(date)]
                            val hiddenHeavy = entry?.dayState == DayState.HEAVY && !showHeavy
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .size(width = 44.dp, height = 48.dp)
                                    .clickable(enabled = !date.isAfter(today)) {
                                        onDayClick(date)
                                    },
                                verticalArrangement = Arrangement.Center
                            ) {
                                if (entry != null && !hiddenHeavy) {
                                    DayStateMarker(
                                        state = entry.dayState,
                                        counted = entry.isGoodDay,
                                        size = 26.dp
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(26.dp)
                                            .background(
                                                color = if (date == today) {
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                                } else {
                                                    Color.Transparent
                                                },
                                                shape = CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (date == today) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .background(
                                                        MaterialTheme.colorScheme.primary,
                                                        CircleShape
                                                    )
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = dayNumber.toString(),
                                    fontSize = 10.sp,
                                    fontWeight = if (date == today) FontWeight.Bold else FontWeight.Normal,
                                    color = MaterialTheme.colorScheme.onSurface.copy(
                                        alpha = if (date.isAfter(today)) 0.25f else 0.72f
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
