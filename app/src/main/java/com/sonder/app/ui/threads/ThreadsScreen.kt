package com.sonder.app.ui.threads

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sonder.app.data.local.entity.DailyEntryEntity
import com.sonder.app.data.local.entity.EntryTagEntity
import com.sonder.app.ui.components.DayStateMarker
import com.sonder.app.ui.components.ScreenHeader
import com.sonder.app.ui.components.StateLegend
import com.sonder.app.ui.theme.SonderPalette
import com.sonder.app.ui.theme.TimeOfDayMood
import com.sonder.app.ui.components.LivingAuraBackground
import com.sonder.app.util.DateDisplayFormatter
import java.time.LocalDate

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThreadsScreen(
    entries: List<DailyEntryEntity>,
    tags: List<EntryTagEntity>,
    onEntryClick: (DailyEntryEntity) -> Unit,
    onOpenSettings: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var selectedThread by remember { mutableStateOf<String?>(null) }

    val tagsByEntry = remember(tags) { tags.groupBy { it.entryId } }
    val threadCounts = remember(tags) {
        tags.filter { it.userConfirmed }
            .groupingBy { it.tag }
            .eachCount()
            .toList()
            .sortedByDescending { it.second }
    }

    val filteredEntries = remember(entries, tagsByEntry, query, selectedThread) {
        entries.filter { entry ->
            val entryTags = tagsByEntry[entry.id].orEmpty()
            val threadMatch = selectedThread == null || entryTags.any { it.tag == selectedThread }
            val queryMatch = query.isBlank() ||
                entry.reasonText?.contains(query, ignoreCase = true) == true ||
                entryTags.any { it.tag.contains(query, ignoreCase = true) }
            threadMatch && queryMatch
        }
    }

    val allEntryTags = remember(tags) { tags.filter { it.userConfirmed } }
    val insight = remember(entries, allEntryTags) { buildInsight(entries, allEntryTags) }
    val children = entries.isNotEmpty()
    val mood = remember { TimeOfDayMood.current() }

    LivingAuraBackground(mood = mood) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 16.dp)
        ) {
            ScreenHeader(
                title = "Threads",
                subtitle = "The people, places and small rituals that keep returning.",
                onSettings = onOpenSettings
            )

            Spacer(modifier = Modifier.height(14.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search memories and threads") },
                leadingIcon = {
                    Icon(Icons.Outlined.Search, contentDescription = null)
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp)
            )

            if (insight != null) {
                Spacer(modifier = Modifier.height(14.dp))
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "A QUIET PATTERN",
                            style = MaterialTheme.typography.labelSmall,
                            color = SonderPalette.AmberGold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = insight, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            if (threadCounts.isNotEmpty()) {
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "THREADS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    threadCounts.forEach { (tag, count) ->
                        FilterChip(
                            selected = selectedThread == tag,
                            onClick = {
                                selectedThread = if (selectedThread == tag) null else tag
                            },
                            label = { Text("$tag · $count") }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(22.dp))
            Text(
                text = if (selectedThread == null) "MEMORIES" else "MEMORIES IN $selectedThread",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (!children) {
                Text(
                    text = "Nothing to thread together yet. Capture a day and Sonder will quietly connect the people, places and rituals that recur.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
                )
            } else if (filteredEntries.isEmpty()) {
                Text(
                    text = "No memories match that search.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
                )
            } else {
                filteredEntries.forEach { entry ->
                    EntryCard(
                        entry = entry,
                        entryTags = tagsByEntry[entry.id].orEmpty(),
                        onClick = { onEntryClick(entry) }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            StateLegend()
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
private fun EntryCard(
    entry: DailyEntryEntity,
    entryTags: List<EntryTagEntity>,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.66f)
        )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            DayStateMarker(state = entry.dayState, counted = entry.isGoodDay, size = 24.dp)
            Spacer(modifier = Modifier.padding(horizontal = 5.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = DateDisplayFormatter.isoToDisplayString(entry.localDate),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                )
                if (!entry.reasonText.isNullOrBlank()) {
                    Text(
                        text = entry.reasonText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Normal,
                        maxLines = 3
                    )
                }
                val threadLabels = entryTags
                    .filter { it.userConfirmed && it.tagType != "SPARK" }
                    .joinToString(" · ") { it.tag }
                if (threadLabels.isNotBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = threadLabels,
                        style = MaterialTheme.typography.labelSmall,
                        color = SonderPalette.SageHoney
                    )
                }
            }
        }
    }
}

private fun buildInsight(
    entries: List<DailyEntryEntity>,
    tags: List<EntryTagEntity>
): String? {
    if (entries.size < 8) return null
    val entryById = entries.associateBy { it.id }
    val baseline = entries.count { it.isGoodDay }.toFloat() / entries.size
    val sparkTags = tags.filter { it.tagType == "SPARK" }
    val best = sparkTags
        .groupBy { it.tag }
        .mapValues { (_, list) ->
            val matching = list.mapNotNull { entryById[it.entryId] }
            matching to (if (matching.isEmpty()) 0f else matching.count { it.isGoodDay }.toFloat() / matching.size)
        }
        .filter { it.value.first.size >= 4 }
        .maxByOrNull { it.value.second - baseline }

    if (best == null) {
        val goodDays = entries.count { it.isGoodDay }
        return "You've collected $goodDays good days across ${entries.size} memories. No strong pattern yet — that's okay."
    }

    val (spark, data) = best
    val (matching, rate) = data
    val lift = rate - baseline
    if (lift < 0.15f) {
        return "Your days with $spark feel good ${(rate * 100).toInt()}% of the time — close to your baseline. Nothing forced."
    }
    val early = if (matching.size < 8) " (early pattern)" else ""
    return "Days with $spark are bright or warm ${(rate * 100).toInt()}% of the time " +
        "across ${matching.size} memories$early — your baseline is ${(baseline * 100).toInt()}%."
}
