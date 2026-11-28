package com.sonder.app.ui.calendar

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sonder.app.data.local.entity.ChapterEntity
import com.sonder.app.data.local.entity.EmergentDayWeather
import com.sonder.app.data.local.entity.MomentEntity
import com.sonder.app.data.local.entity.MomentPhotoEntity
import com.sonder.app.ui.components.DayWeatherMarker
import com.sonder.app.ui.components.MomentPhotoStrip
import com.sonder.app.ui.components.PhotoThumbnail
import com.sonder.app.ui.components.ScreenHeader
import com.sonder.app.ui.components.auraColor
import com.sonder.app.util.DateDisplayFormatter
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    selectedDate: LocalDate,
    allMoments: List<MomentEntity>,
    selectedDayMoments: List<MomentEntity>,
    photosByMoment: Map<Long, List<MomentPhotoEntity>> = emptyMap(),
    chapters: List<ChapterEntity>,
    onSelectDate: (LocalDate) -> Unit,
    onCaptureForDate: (LocalDate, MomentEntity?) -> Unit,
    onEditMoment: (MomentEntity) -> Unit,
    onCreateChapter: (String, String, String, String?) -> Unit,
    onCloseChapter: (ChapterEntity) -> Unit,
    onDeleteChapter: (ChapterEntity) -> Unit,
    onSetChapterCover: (ChapterEntity, Uri) -> Unit = { _, _ -> },
    onShareChapterRecap: (ChapterEntity) -> Unit = {},
    onOpenSettings: () -> Unit
) {
    var displayMonth by remember { mutableStateOf(YearMonth.from(selectedDate)) }
    var showNewChapter by remember { mutableStateOf(false) }

    val byDate = remember(allMoments) { allMoments.groupBy { it.localDate } }
    val firstOfMonth = displayMonth.atDay(1)
    val daysInMonth = displayMonth.lengthOfMonth()
    val leadingBlanks = firstOfMonth.dayOfWeek.value - 1
    val cells = leadingBlanks + daysInMonth
    val rows = (cells + 6) / 7

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(18.dp))
            ScreenHeader(
                title = "Calendar",
                subtitle = "Days, not scores.",
                onSettings = onOpenSettings
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { displayMonth = displayMonth.minusMonths(1) }) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "Previous month")
                }
                Text(
                    text = "${displayMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${displayMonth.year}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                IconButton(onClick = { displayMonth = displayMonth.plusMonths(1) }) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "Next month")
                }
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth()) {
                listOf("M", "T", "W", "T", "F", "S", "S").forEach { label ->
                    Text(
                        text = label,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(rows) { row ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        repeat(7) { column ->
                            val index = row * 7 + column
                            val dayNumber = index - leadingBlanks + 1
                            if (dayNumber in 1..daysInMonth) {
                                val date = displayMonth.atDay(dayNumber)
                                val iso = DateDisplayFormatter.toStorageIso(date)
                                val dayMoments = byDate[iso].orEmpty()
                                DayCell(
                                    date = date,
                                    moments = dayMoments,
                                    chapters = chapters.filter { it.covers(iso) },
                                    selected = date == selectedDate,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        onSelectDate(date)
                                        displayMonth = YearMonth.from(date)
                                    }
                                )
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        DateDisplayFormatter.toDisplayString(selectedDate),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    val dayWeather = EmergentDayWeather.derive(selectedDayMoments)
                    Text(
                        dayWeather?.label ?: "No weather yet",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
                    )
                }
                Button(onClick = { onCaptureForDate(selectedDate, null) }) {
                    Text("Add moment")
                }
            }
        }

        if (selectedDayMoments.isEmpty()) {
            item {
                Text(
                    "This day is still blank — that's allowed.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
        } else {
            items(selectedDayMoments.size) { index ->
                val moment = selectedDayMoments[index]
                val weather = EmergentDayWeather.derive(listOf(moment))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                            RoundedCornerShape(14.dp)
                        )
                        .clickable { onEditMoment(moment) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DayWeatherMarker(weather = weather, size = 24.dp)
                    Spacer(modifier = Modifier.size(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            moment.bodyText?.takeIf { it.isNotBlank() }
                                ?: if (moment.audioFileName != null) "Voice note" else "A quiet moment",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2
                        )
                        Text(
                            listOfNotNull(
                                dateTimeLabel(moment.createdAt),
                                "Voice".takeIf { moment.audioFileName != null },
                                "Rain".takeIf { moment.isRainRoom },
                                "Unsent".takeIf { moment.isUnsent }
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        if (photosByMoment[moment.id].orEmpty().isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            MomentPhotoStrip(
                                photos = photosByMoment[moment.id].orEmpty(),
                                thumbSize = 42
                            )
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "CHAPTERS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { showNewChapter = true }) { Text("New chapter") }
            }
        }

        if (chapters.isEmpty()) {
            item {
                Text(
                    "Frame a stretch of life — exams, a trip, an internship.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
        } else {
            items(chapters.size) { index ->
                val chapter = chapters[index]
                ChapterRow(
                    chapter = chapter,
                    onClose = { onCloseChapter(chapter) },
                    onDelete = { onDeleteChapter(chapter) },
                    onSetCover = { uri -> onSetChapterCover(chapter, uri) },
                    onShareRecap = { onShareChapterRecap(chapter) }
                )
            }
        }

        item {
            OutlinedButton(
                onClick = { onCaptureForDate(LocalDate.now(), null) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Capture today") }
        }

        item { Spacer(modifier = Modifier.height(28.dp)) }
    }

    if (showNewChapter) {
        NewChapterDialog(
            onDismiss = { showNewChapter = false },
            onCreate = { title, color, pattern, plannedEnd ->
                showNewChapter = false
                onCreateChapter(title, color, pattern, plannedEnd)
            }
        )
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    moments: List<MomentEntity>,
    chapters: List<ChapterEntity>,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val weather = EmergentDayWeather.derive(moments)
    Column(
        modifier = modifier
            .aspectRatio(0.78f)
            .padding(2.dp)
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                else MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                RoundedCornerShape(10.dp)
            )
            .then(
                if (selected) Modifier.border(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                    RoundedCornerShape(10.dp)
                ) else Modifier
            )
            .clickable(onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
        )
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            if (weather != null) {
                DayWeatherMarker(weather = weather, size = 16.dp)
            }
        }
        if (chapters.isNotEmpty()) {
            ChapterBandRow(chapters = chapters.take(2))
        }
    }
}

@Composable
private fun ChapterBandRow(chapters: List<ChapterEntity>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        chapters.forEach { chapter ->
            Canvas(modifier = Modifier.fillMaxWidth().height(2.dp)) {
                val effect = when (chapter.patternStyle) {
                    ChapterEntity.PATTERN_DASHED -> PathEffect.dashPathEffect(
                        floatArrayOf(6f, 4f)
                    )
                    ChapterEntity.PATTERN_DOTTED -> PathEffect.dashPathEffect(
                        floatArrayOf(2f, 3f)
                    )
                    else -> null
                }
                drawLine(
                    color = runCatching { Color(android.graphics.Color.parseColor("#${chapter.colorHex}")) }
                        .getOrDefault(Color(0xFF8A7A66)),
                    start = Offset(0f, 1f),
                    end = Offset(size.width, 1f),
                    strokeWidth = size.height,
                    pathEffect = effect
                )
            }
        }
    }
}

@Composable
private fun ChapterRow(
    chapter: ChapterEntity,
    onClose: () -> Unit,
    onDelete: () -> Unit,
    onSetCover: (Uri) -> Unit,
    onShareRecap: () -> Unit
) {
    val pickCover = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) onSetCover(uri) }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(
                            runCatching { Color(android.graphics.Color.parseColor("#${chapter.colorHex}")) }
                                .getOrDefault(Color(0xFF8A7A66)),
                            RoundedCornerShape(5.dp)
                        )
                )
                Spacer(modifier = Modifier.size(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(chapter.title, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${DateDisplayFormatter.isoToDisplayString(chapter.startDate)} → " +
                            (chapter.endDate?.let { DateDisplayFormatter.isoToDisplayString(it) } ?: "ongoing"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                    )
                }
                if (chapter.endDate == null) {
                    TextButton(onClick = onClose) { Text("End") }
                }
                TextButton(onClick = onDelete) { Text("Delete") }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PhotoThumbnail(
                    fileName = chapter.coverPhotoUri,
                    modifier = Modifier.size(44.dp),
                    maxDimension = 160
                )
                Spacer(modifier = Modifier.size(8.dp))
                TextButton(onClick = {
                    pickCover.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }) { Text(if (chapter.coverPhotoUri == null) "Add cover" else "Change cover") }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onShareRecap) { Text("Recap") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewChapterDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String, String, String?) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var color by remember { mutableStateOf("8A7A66") }
    var pattern by remember { mutableStateOf(ChapterEntity.PATTERN_SOLID) }
    var plannedEnd by remember { mutableStateOf<LocalDate?>(null) }
    var showPicker by remember { mutableStateOf(false) }
    val pickerState = rememberDatePickerState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("A new chapter") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    placeholder = { Text("End-Sem Exams") },
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text("Accent", style = MaterialTheme.typography.labelSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("8A7A66", "5E7D69", "B4702A", "566784", "A45A6E").forEach { hex ->
                        AssistChip(
                            onClick = { color = hex },
                            label = {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .background(
                                            Color(android.graphics.Color.parseColor("#$hex")),
                                            RoundedCornerShape(8.dp)
                                        )
                                )
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Underline pattern (non-colour marker)", style = MaterialTheme.typography.labelSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        ChapterEntity.PATTERN_SOLID to "Solid",
                        ChapterEntity.PATTERN_DASHED to "Dashed",
                        ChapterEntity.PATTERN_DOTTED to "Dotted"
                    ).forEach { (value, label) ->
                        FilterChip(
                            selected = pattern == value,
                            onClick = { pattern = value },
                            label = { Text(label) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = { showPicker = true }) {
                    Text(plannedEnd?.let { "Planned end: ${DateDisplayFormatter.toDisplayString(it)}" } ?: "Planned end (optional)")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (title.isNotBlank()) {
                        onCreate(title.trim(), color, pattern, plannedEnd?.let {
                            DateDisplayFormatter.toStorageIso(it)
                        })
                    }
                }
            ) { Text("Start") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    if (showPicker) {
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        plannedEnd = Instant.ofEpochMilli(millis)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                    }
                    showPicker = false
                }) { Text("Use date") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel") } }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

private fun dateTimeLabel(epochMs: Long): String {
    val time = java.time.Instant.ofEpochMilli(epochMs)
        .atZone(ZoneId.systemDefault())
        .toLocalTime()
    return "%02d:%02d".format(time.hour, time.minute)
}
