package com.sonder.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sonder.app.data.local.entity.ChapterEntity
import com.sonder.app.data.local.entity.EmergentDayWeather
import com.sonder.app.data.local.entity.MomentEntity
import com.sonder.app.data.local.entity.MomentPhotoEntity
import com.sonder.app.data.local.entity.MomentTagEntity
import com.sonder.app.data.local.entity.OpenWhenEnvelopeEntity
import com.sonder.app.ui.Insight
import com.sonder.app.ui.components.DayWeatherMarker
import com.sonder.app.ui.components.MomentPhotoDetails
import com.sonder.app.ui.components.ScreenHeader
import com.sonder.app.ui.components.auraColor
import com.sonder.app.util.DateDisplayFormatter
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Composable
fun HomeScreen(
    goodDayCount: Int,
    totalMomentCount: Int,
    firstMomentDate: LocalDate?,
    todayMoments: List<MomentEntity>,
    yesterdayHasMoment: Boolean,
    allTags: List<MomentTagEntity>,
    photosByMoment: Map<Long, List<MomentPhotoEntity>> = emptyMap(),
    unlockedEnvelopes: List<OpenWhenEnvelopeEntity>,
    chapters: List<ChapterEntity>,
    insights: List<Insight>,
    showWelcomeBack: Boolean,
    weather: EmergentDayWeather?,
    reReadEnabled: Boolean,
    onDismissWelcomeBack: () -> Unit,
    onOpenEnvelope: (OpenWhenEnvelopeEntity) -> Unit,
    onCapture: () -> Unit,
    onCaptureYesterday: () -> Unit,
    onEditMoment: (MomentEntity) -> Unit,
    onConfirmTag: (Long) -> Unit,
    onDismissTag: (Long) -> Unit,
    onOpenRainRoom: () -> Unit,
    onRollReRead: () -> Unit,
    onOpenSettings: () -> Unit,
    onChapterKeepGoing: (ChapterEntity) -> Unit,
    onChapterEnded: (ChapterEntity) -> Unit
) {
    val today = LocalDate.now()
    val todayIso = DateDisplayFormatter.toStorageIso(today)
    val todayTagIds = todayMoments.map { it.id }.toSet()
    val unconfirmed = allTags.filter {
        it.momentId in todayTagIds &&
            !it.userConfirmed &&
            it.source == MomentTagEntity.SOURCE_ML_NOTICED
    }
    val checkInChapter = chapters.firstOrNull { chapter ->
        chapter.endDate == null && chapter.plannedEndDate == null &&
            ChronoUnit.DAYS.between(
                runCatching { DateDisplayFormatter.fromStorageIso(chapter.startDate) }
                    .getOrDefault(today),
                today
            ) >= 7
    }?.let { chapter ->
        val last = chapter.lastCheckinDate?.let {
            runCatching { DateDisplayFormatter.fromStorageIso(it) }.getOrNull()
        }
        if (last == null || ChronoUnit.DAYS.between(last, today) >= 7) chapter else null
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(18.dp))
            ScreenHeader(
                title = "Sonder",
                subtitle = "A living vault for real days.",
                onSettings = onOpenSettings,
                trailing = {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(
                                (weather ?: EmergentDayWeather.QUIET_MIST).auraColor().copy(alpha = 0.18f),
                                RoundedCornerShape(17.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        DayWeatherMarker(weather = weather, size = 20.dp)
                    }
                }
            )
        }

        if (showWelcomeBack) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                    ),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Welcome back.",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "No catching up needed — whenever you're ready.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = onDismissWelcomeBack) { Text("Okay") }
                        }
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard(
                    label = "Good days",
                    value = goodDayCount.toString(),
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    label = "Moments",
                    value = totalMomentCount.toString(),
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    label = "Since",
                    value = firstMomentDate?.let { DateDisplayFormatter.toDisplayString(it) } ?: "—",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (unlockedEnvelopes.isNotEmpty()) {
            item {
                Text(
                    "READY TO OPEN",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
            }
            items(unlockedEnvelopes, key = { "env-${it.id}" }) { envelope ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenEnvelope(envelope) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.MailOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.size(10.dp))
                        Column {
                            Text(
                                envelope.titleHint ?: "An envelope",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                "From ${DateDisplayFormatter.isoToDisplayString(envelope.unlockDate)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        }

        if (checkInChapter != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                    ),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Still in ${checkInChapter.title}?",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "No rush. This is just a quiet check-in.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { onChapterEnded(checkInChapter) }) {
                                Text("It ended")
                            }
                            TextButton(onClick = { onChapterKeepGoing(checkInChapter) }) {
                                Text("Yes, still")
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
                Text(
                    "TODAY · ${DateDisplayFormatter.toDisplayString(today)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.weight(1f))
                if (weather != null) {
                    Text(
                        text = weather.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
                    )
                }
            }
        }

        if (todayMoments.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                            RoundedCornerShape(18.dp)
                        )
                        .padding(18.dp)
                ) {
                    Text(
                        "Nothing recorded yet today.",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "A sentence, a voice note, a single spark — all enough.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }

        items(todayMoments, key = { it.id }) { moment ->
            MomentRow(
                moment = moment,
                photos = photosByMoment[moment.id].orEmpty(),
                onClick = { onEditMoment(moment) }
            )
        }

        item {
            Button(onClick = onCapture, modifier = Modifier.fillMaxWidth()) {
                Text("Capture a moment")
            }
            if (!yesterdayHasMoment) {
                TextButton(onClick = onCaptureYesterday, modifier = Modifier.fillMaxWidth()) {
                    Text("Add yesterday instead")
                }
            }
        }

        if (unconfirmed.isNotEmpty()) {
            item {
                Text(
                    "WE NOTICED",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
            }
            items(unconfirmed, key = { "tag-${it.id}" }) { tag ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AssistChip(
                        onClick = { onConfirmTag(tag.id) },
                        label = { Text("Keep \"${tag.tagName}\"") }
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    TextButton(onClick = { onDismissTag(tag.id) }) { Text("No") }
                }
            }
        }

        if (insights.isNotEmpty()) {
            item {
                Text(
                    "GENTLE PATTERNS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
            }
            items(insights, key = { "insight-${it.name}" }) { insight ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.size(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(insight.detail, style = MaterialTheme.typography.bodyMedium)
                            if (insight.isEarly) {
                                Text(
                                    "Early pattern · still learning",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onOpenRainRoom, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.WaterDrop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.size(6.dp))
                    Text("Rain Room")
                }
                if (reReadEnabled) {
                    OutlinedButton(onClick = onRollReRead, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.Casino, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.size(6.dp))
                        Text("Re-read")
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(28.dp)) }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
            )
        }
    }
}

@Composable
private fun MomentRow(
    moment: MomentEntity,
    photos: List<MomentPhotoEntity>,
    onClick: () -> Unit
) {
    val weather = EmergentDayWeather.derive(listOf(moment))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DayWeatherMarker(weather = weather, size = 28.dp)
        Spacer(modifier = Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = moment.bodyText?.takeIf { it.isNotBlank() }
                    ?: if (moment.audioFileName != null) "Voice note" else "A quiet moment",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3
            )
            val meta = buildList {
                if (moment.audioFileName != null) add("Voice note")
                if (moment.isRainRoom) add("Rain Room")
                if (moment.isUnsent) add("Unsent")
                if (moment.countsAsGood) add("Good")
            }.joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(
                    meta,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
            if (photos.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                MomentPhotoDetails(photos = photos)
            }
        }
    }
}
