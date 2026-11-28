package com.sonder.app.ui.home

import android.media.MediaPlayer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sonder.app.data.local.entity.MomentEntity
import com.sonder.app.data.local.entity.MomentTagEntity
import com.sonder.app.data.local.entity.OpenWhenEnvelopeEntity
import com.sonder.app.ui.components.EmptyWhisper
import com.sonder.app.ui.components.GlassCard
import com.sonder.app.ui.components.SectionEyebrow
import com.sonder.app.ui.components.TagPill
import com.sonder.app.ui.theme.readableAccent
import com.sonder.app.ui.theme.readableSecondaryAccent
import com.sonder.app.ui.theme.weatherScoreColor
import com.sonder.app.util.AudioNoteManager
import com.sonder.app.util.DateDisplayFormatter
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    goodDayCount: Int,
    totalMomentCount: Int,
    firstMomentDate: LocalDate?,
    todayMoments: List<MomentEntity>,
    yesterdayHasEntry: Boolean,
    allTags: List<MomentTagEntity>,
    unlockedEnvelopes: List<OpenWhenEnvelopeEntity>,
    showWelcomeBack: Boolean,
    darkTheme: Boolean,
    onDismissWelcomeBack: () -> Unit,
    onOpenEnvelope: (OpenWhenEnvelopeEntity) -> Unit,
    onCaptureToday: () -> Unit,
    onCaptureYesterday: () -> Unit,
    onEditMoment: (MomentEntity) -> Unit,
    onConfirmTag: (Long) -> Unit,
    onDismissTag: (Long) -> Unit,
    onOpenThread: (String) -> Unit
) {
    val context = LocalContext.current
    val audioManager = remember { AudioNoteManager(context) }
    var playingId by remember { mutableStateOf<Long?>(null) }
    var activePlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var readingEnvelope by remember { mutableStateOf<OpenWhenEnvelopeEntity?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            activePlayer?.release()
        }
    }

    val today = LocalDate.now()
    val nowTime = LocalTime.now()
    val offerMidnightGrace = DateDisplayFormatter.shouldOfferMidnightGrace(nowTime, yesterdayHasEntry)
    val accent = readableAccent(darkTheme)
    val secondaryAccent = readableSecondaryAccent(darkTheme)

    val daysTogether = firstMomentDate?.let {
        (ChronoUnit.DAYS.between(it, today) + 1).coerceAtLeast(1)
    } ?: 0

    val todayMomentIds = todayMoments.map { it.id }.toSet()
    val todayConfirmedTags = remember(allTags, todayMomentIds) {
        allTags.filter { it.momentId in todayMomentIds && it.userConfirmed }
            .distinctBy { it.normalizedName }
    }
    val noticedSuggestions = remember(allTags, todayMomentIds) {
        allTags.filter { it.momentId in todayMomentIds && !it.userConfirmed && it.source == "ML_NOTICED" }
            .distinctBy { it.normalizedName }
            .take(3)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text(
                    text = DateDisplayFormatter.toDisplayString(today),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Sonder",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        if (showWelcomeBack) {
            item {
                GlassCard(borderColor = accent.copy(alpha = 0.45f)) {
                    Text(
                        text = "Welcome back",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = accent
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Your quiet space has been waiting. No catch-up needed—just today.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    TextButton(onClick = onDismissWelcomeBack) {
                        Text("Got it")
                    }
                }
            }
        }

        if (unlockedEnvelopes.isNotEmpty()) {
            item {
                val env = unlockedEnvelopes.first()
                GlassCard(borderColor = accent.copy(alpha = 0.5f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.MailOutline,
                            contentDescription = null,
                            tint = accent
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "An envelope opened today",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = env.titleHint ?: "Sealed on ${DateDisplayFormatter.isoToDisplayString(env.unlockDate)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            onOpenEnvelope(env)
                            readingEnvelope = env
                        },
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Open envelope")
                    }
                }
            }
        }

        // Good Days Hero Counter
        item {
            GlassCard {
                SectionEyebrow(text = "Good days so far", color = accent)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = goodDayCount.toString(),
                        style = MaterialTheme.typography.displayLarge,
                        color = accent
                    )
                    Text(
                        text = if (goodDayCount == 1) "good day" else "good days",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (daysTogether > 0) {
                        "$totalMomentCount ${if (totalMomentCount == 1) "moment" else "moments"} across $daysTogether ${if (daysTogether == 1) "day" else "days"} · your pace, your terms"
                    } else {
                        "Every good day you notice counts. Heavy or quiet days never reset anything."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Today's Moments
        item {
            GlassCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SectionEyebrow(text = "Today's moments")
                    TextButton(onClick = onCaptureToday) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add moment")
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                if (todayMoments.isEmpty()) {
                    EmptyWhisper(
                        title = "Nothing noted yet today",
                        subtitle = "Capture a moment in a few words or a short voice note."
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Button(
                        onClick = onCaptureToday,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text("Capture a moment")
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        todayMoments.forEach { m ->
                            val tint = weatherScoreColor(m.weatherScore, darkTheme)
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onEditMoment(m) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = tint.copy(alpha = 0.22f)
                                    ) {
                                        Text(
                                            text = m.weatherScore?.let {
                                                when {
                                                    it <= -0.25f -> "Rain"
                                                    it >= 0.25f -> "Sun"
                                                    else -> "Mist"
                                                }
                                            } ?: "Note",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = tint,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = m.bodyText?.takeIf { it.isNotBlank() }
                                                ?: if (m.audioFileName != null) "Voice note" else "Quiet moment",
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    if (m.audioFileName != null) {
                                        val isThisPlaying = playingId == m.id
                                        IconButton(
                                            onClick = {
                                                if (isThisPlaying) {
                                                    activePlayer?.stop()
                                                    activePlayer?.release()
                                                    activePlayer = null
                                                    playingId = null
                                                } else {
                                                    val file = audioManager.fileFor(m.audioFileName)
                                                    if (file.exists()) {
                                                        activePlayer?.release()
                                                        val mp = MediaPlayer().apply {
                                                            setDataSource(file.absolutePath)
                                                            prepare()
                                                            setOnCompletionListener {
                                                                playingId = null
                                                                release()
                                                                activePlayer = null
                                                            }
                                                            start()
                                                        }
                                                        activePlayer = mp
                                                        playingId = m.id
                                                    }
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = if (isThisPlaying) Icons.Outlined.Stop else Icons.Outlined.PlayArrow,
                                                contentDescription = "Play voice note",
                                                tint = accent
                                            )
                                        }
                                    } else {
                                        Icon(
                                            imageVector = Icons.Outlined.Edit,
                                            contentDescription = "Edit moment",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (offerMidnightGrace) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onCaptureYesterday,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Still up? Log yesterday (${DateDisplayFormatter.toDisplayString(today.minusDays(1))})")
                    }
                }
            }
        }

        // Sparks & Noticed Threads
        if (todayConfirmedTags.isNotEmpty() || noticedSuggestions.isNotEmpty()) {
            item {
                GlassCard {
                    if (todayConfirmedTags.isNotEmpty()) {
                        SectionEyebrow(text = "Sparks today", color = secondaryAccent)
                        Spacer(modifier = Modifier.height(10.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            todayConfirmedTags.forEach { tag ->
                                TagPill(
                                    label = tag.tagName,
                                    icon = Icons.Outlined.Bookmark,
                                    accent = secondaryAccent,
                                    onClick = { onOpenThread(tag.normalizedName) }
                                )
                            }
                        }
                    }

                    if (noticedSuggestions.isNotEmpty()) {
                        if (todayConfirmedTags.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(14.dp))
                        }
                        SectionEyebrow(text = "Noticed in your words", color = accent)
                        Spacer(modifier = Modifier.height(8.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            noticedSuggestions.forEach { suggestion ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.AutoAwesome,
                                            contentDescription = null,
                                            tint = accent,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Keep "\${suggestion.tagName}" as a \${suggestion.tagType.lowercase()}?",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Row {
                                        IconButton(onClick = { onConfirmTag(suggestion.id) }) {
                                            Icon(
                                                imageVector = Icons.Outlined.Check,
                                                contentDescription = "Confirm",
                                                tint = secondaryAccent
                                            )
                                        }
                                        IconButton(onClick = { onDismissTag(suggestion.id) }) {
                                            Icon(
                                                imageVector = Icons.Outlined.Close,
                                                contentDescription = "Dismiss",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
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

    val envToRead = readingEnvelope
    if (envToRead != null) {
        AlertDialog(
            onDismissRequest = { readingEnvelope = null },
            title = { Text(envToRead.titleHint ?: "Sealed envelope") },
            text = {
                Text(
                    text = envToRead.bodyText ?: "A quiet note from the past.",
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            confirmButton = {
                TextButton(onClick = { readingEnvelope = null }) {
                    Text("Close")
                }
            }
        )
    }
}
