package com.sonder.app.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sonder.app.data.local.entity.ChapterEntity
import com.sonder.app.data.local.entity.EmergentDayWeather
import com.sonder.app.data.local.entity.MomentEntity
import com.sonder.app.ui.components.EmptyWhisper
import com.sonder.app.ui.components.GlassCard
import com.sonder.app.ui.components.SectionEyebrow
import com.sonder.app.ui.theme.readableAccent
import com.sonder.app.ui.theme.weatherScoreColor
import com.sonder.app.util.DateDisplayFormatter
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private val CHAPTER_SWATCHES = listOf(
    "#D98A3C", "#6E9A7E", "#6B8CAE", "#B57996", "#A68B5B"
)

@Composable
fun CalendarScreen(
    selectedDate: LocalDate,
    allMoments: List<MomentEntity>,
    selectedDayMoments: List<MomentEntity>,
    chapters: List<ChapterEntity>,
    darkTheme: Boolean,
    onSelectDate: (LocalDate) -> Unit,
    onCaptureForDate: (LocalDate, MomentEntity?) -> Unit,
    onCreateChapter: (String, String, String, String?) -> Unit,
    onCloseChapter: (ChapterEntity) -> Unit
) {
    var displayMonth by remember(selectedDate) { mutableStateOf(YearMonth.from(selectedDate)) }
    var showNewChapterDialog by remember { mutableStateOf(false) }
    var newChapterTitle by remember { mutableStateOf("") }
    var newChapterColor by remember { mutableStateOf(CHAPTER_SWATCHES.first()) }
    var newChapterPattern by remember { mutableStateOf("SOLID") }

    val accent = readableAccent(darkTheme)
    val momentsByDate = remember(allMoments) { allMoments.groupBy { it.localDate } }

    val firstOfMonth = displayMonth.atDay(1)
    val daysInMonth = displayMonth.lengthOfMonth()
    val leadingBlanks = (firstOfMonth.dayOfWeek.value - 1).coerceAtLeast(0)
    val totalCells = ((leadingBlanks + daysInMonth + 6) / 7) * 7

    val activeChapters = chapters.filter { it.endDate == null }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Calendar",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                TextButton(onClick = { showNewChapterDialog = true }) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Chapter")
                }
            }
        }

        // Chapters Strip
        item {
            GlassCard {
                SectionEyebrow(text = "Chapters", color = accent)
                Spacer(modifier = Modifier.height(8.dp))
                if (chapters.isEmpty()) {
                    Text(
                        text = "Mark life seasons or trips with a chapter band.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(chapters, key = { it.id }) { ch ->
                            val chColor = runCatching {
                                Color(android.graphics.Color.parseColor(ch.colorHex))
                            }.getOrDefault(accent)
                            val isActive = ch.endDate == null
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = chColor.copy(alpha = 0.16f),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    chColor.copy(alpha = 0.55f)
                                ),
                                modifier = Modifier.clickable {
                                    if (isActive) onCloseChapter(ch)
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(chColor)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (isActive) ch.title else "\${ch.title} (closed)",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Month Grid
        item {
            GlassCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = { displayMonth = displayMonth.minusMonths(1) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                            contentDescription = "Previous month"
                        )
                    }
                    Text(
                        text = "\${displayMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} \${displayMonth.year}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = { displayMonth = displayMonth.plusMonths(1) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                            contentDescription = "Next month"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                val weekdays = listOf("M", "T", "W", "T", "F", "S", "S")
                Row(modifier = Modifier.fillMaxWidth()) {
                    weekdays.forEach { d ->
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = d,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (row in 0 until (totalCells / 7)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            for (col in 0 until 7) {
                                val index = row * 7 + col
                                val dayNum = index - leadingBlanks + 1
                                if (dayNum !in 1..daysInMonth) {
                                    Box(modifier = Modifier.weight(1f).aspectRatio(0.95f))
                                } else {
                                    val date = displayMonth.atDay(dayNum)
                                    val iso = DateDisplayFormatter.toStorageIso(date)
                                    val dayMoments = momentsByDate[iso].orEmpty()
                                    val isSelected = date == selectedDate
                                    val dayChapter = chapters.firstOrNull { ch ->
                                        iso >= ch.startDate && (ch.endDate == null || iso <= ch.endDate)
                                    }
                                    val chColor = dayChapter?.let {
                                        runCatching { Color(android.graphics.Color.parseColor(it.colorHex)) }.getOrNull()
                                    }

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(0.95f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(
                                                when {
                                                    isSelected -> accent.copy(alpha = 0.22f)
                                                    chColor != null -> chColor.copy(alpha = 0.12f)
                                                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                                }
                                            )
                                            .border(
                                                width = if (isSelected) 1.5.dp else 0.dp,
                                                color = if (isSelected) accent else Color.Transparent,
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .clickable { onSelectDate(date) }
                                            .padding(4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Text(
                                                text = dayNum.toString(),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            if (dayMoments.isNotEmpty()) {
                                                Spacer(modifier = Modifier.height(3.dp))
                                                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                                    dayMoments.take(4).forEach { m ->
                                                        Box(
                                                            modifier = Modifier
                                                                .size(6.dp)
                                                                .clip(CircleShape)
                                                                .background(weatherScoreColor(m.weatherScore, darkTheme))
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Selected Day Inspector
        item {
            val emergent = EmergentDayWeather.derive(selectedDayMoments)
            GlassCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = DateDisplayFormatter.toDisplayString(selectedDate),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (emergent != null) {
                            Text(
                                text = emergent.label,
                                style = MaterialTheme.typography.labelLarge,
                                color = accent
                            )
                        }
                    }
                    Button(
                        onClick = { onCaptureForDate(selectedDate, null) },
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Add moment")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (selectedDayMoments.isEmpty()) {
                    EmptyWhisper(
                        title = "Quiet day",
                        subtitle = "No moments recorded on this date."
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        selectedDayMoments.forEach { m ->
                            val c = weatherScoreColor(m.weatherScore, darkTheme)
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onCaptureForDate(selectedDate, m) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(c)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = m.bodyText?.takeIf { it.isNotBlank() } ?: "Audio / weather moment",
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNewChapterDialog) {
        AlertDialog(
            onDismissRequest = { showNewChapterDialog = false },
            title = { Text("Start a chapter") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newChapterTitle,
                        onValueChange = { newChapterTitle = it },
                        label = { Text("Chapter name (e.g., Finals, Roadtrip)") },
                        singleLine = true
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CHAPTER_SWATCHES.forEach { hex ->
                            val swColor = Color(android.graphics.Color.parseColor(hex))
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(swColor)
                                    .border(
                                        width = if (newChapterColor == hex) 2.5.dp else 0.dp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        shape = CircleShape
                                    )
                                    .clickable { newChapterColor = hex }
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("SOLID", "DASHED", "DOTTED").forEach { pat ->
                            FilterChip(
                                selected = newChapterPattern == pat,
                                onClick = { newChapterPattern = pat },
                                label = { Text(pat.lowercase().replaceFirstChar { it.uppercase() }) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onCreateChapter(newChapterTitle, newChapterColor, newChapterPattern, null)
                        newChapterTitle = ""
                        showNewChapterDialog = false
                    }
                ) { Text("Start") }
            },
            dismissButton = {
                TextButton(onClick = { showNewChapterDialog = false }) { Text("Cancel") }
            }
        )
    }
}
