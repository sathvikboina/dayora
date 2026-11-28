package com.sonder.app.ui.threads

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sonder.app.data.local.entity.EmergentDayWeather
import com.sonder.app.data.local.entity.MomentEntity
import com.sonder.app.ui.ThreadSummary
import com.sonder.app.ui.components.DayWeatherMarker
import com.sonder.app.ui.components.ScreenHeader
import com.sonder.app.util.DateDisplayFormatter

@Composable
fun ThreadsScreen(
    threads: List<ThreadSummary>,
    allMoments: List<MomentEntity>,
    initialFilterNormalized: String?,
    semanticMatches: List<MomentEntity>,
    onSearchSemantic: (String) -> Unit,
    onRenameTagGlobally: (String, String) -> Unit,
    onMergeTag: (String, String) -> Unit,
    onOpenMoment: (MomentEntity) -> Unit,
    onOpenSettings: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var filter by remember(initialFilterNormalized) { mutableStateOf(initialFilterNormalized) }
    var renameTarget by remember { mutableStateOf<ThreadSummary?>(null) }
    var mergeTarget by remember { mutableStateOf<ThreadSummary?>(null) }
    var expanded by remember { mutableStateOf<String?>(null) }

    val momentsById = remember(allMoments) { allMoments.associateBy { it.id } }
    val visible = threads.filter { filter == null || it.normalizedName == filter }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(18.dp))
            ScreenHeader(
                title = "Threads",
                subtitle = "The people and things that keep returning.",
                onSettings = onOpenSettings
            )
        }

        item {
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    onSearchSemantic(it)
                },
                label = { Text("Search meaning, not just words") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        if (query.isNotBlank() && semanticMatches.isNotEmpty()) {
            item {
                Text(
                    "MEANING MATCHES",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
            }
            items(semanticMatches.size) { index ->
                val moment = semanticMatches[index]
                MomentCard(
                    moment = moment,
                    onClick = { onOpenMoment(moment) }
                )
            }
        }

        if (filter != null) {
            item {
                AssistChip(onClick = { filter = null }, label = { Text("Show all threads") })
            }
        }

        if (visible.isEmpty() && query.isBlank()) {
            item {
                Text(
                    "Tags and people appear here once a few moments share them. " +
                        "You can rename or merge them any time.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
        }

        items(visible.size) { index ->
            val thread = visible[index]
            val isExpanded = expanded == thread.normalizedName
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                expanded = if (isExpanded) null else thread.normalizedName
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                thread.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "${thread.count} ${if (thread.count == 1) "moment" else "moments"} · " +
                                    "last ${DateDisplayFormatter.isoToDisplayString(thread.lastSeenIso)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                            )
                        }
                        Text(
                            thread.tagType.lowercase().replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                        )
                    }

                    if (thread.hasUnconfirmed) {
                        Text(
                            "Includes a suggestion you haven't confirmed yet.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }

                    if (isExpanded) {
                        Spacer(modifier = Modifier.height(8.dp))
                        thread.recentExcerpts.forEach { (date, excerpt) ->
                            Text(
                                "$date — $excerpt",
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (thread.momentIds.isNotEmpty()) {
                                TextButton(onClick = {
                                    momentsById[thread.momentIds.first()]?.let(onOpenMoment)
                                }) { Text("Open latest") }
                            }
                            TextButton(onClick = { renameTarget = thread }) { Text("Rename") }
                            TextButton(onClick = { mergeTarget = thread }) { Text("Merge") }
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(28.dp)) }
    }

    renameTarget?.let { thread ->
        RenameDialog(
            thread = thread,
            onDismiss = { renameTarget = null },
            onRename = { newName ->
                renameTarget = null
                onRenameTagGlobally(thread.normalizedName, newName)
            }
        )
    }

    mergeTarget?.let { thread ->
        MergeDialog(
            thread = thread,
            others = threads.filter { it.normalizedName != thread.normalizedName },
            onDismiss = { mergeTarget = null },
            onMerge = { target ->
                mergeTarget = null
                onMergeTag(thread.normalizedName, target.normalizedName)
            }
        )
    }
}

@Composable
private fun MomentCard(moment: MomentEntity, onClick: () -> Unit) {
    val weather = EmergentDayWeather.derive(listOf(moment))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DayWeatherMarker(weather = weather, size = 22.dp)
        Spacer(modifier = Modifier.size(10.dp))
        Column {
            Text(
                moment.bodyText?.takeIf { it.isNotBlank() } ?: "Voice note",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2
            )
            Text(
                DateDisplayFormatter.isoToDisplayString(moment.localDate),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
            )
        }
    }
}

@Composable
private fun RenameDialog(
    thread: ThreadSummary,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit
) {
    var name by remember { mutableStateOf(thread.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename \"${thread.name}\"") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("New name") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onRename(name.trim()) }
            ) { Text("Rename everywhere") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun MergeDialog(
    thread: ThreadSummary,
    others: List<ThreadSummary>,
    onDismiss: () -> Unit,
    onMerge: (ThreadSummary) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Merge \"${thread.name}\" into…") },
        text = {
            Column {
                if (others.isEmpty()) {
                    Text("No other threads to merge into yet.")
                } else {
                    others.forEach { other ->
                        Text(
                            "${other.name} (${other.count})",
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onMerge(other) }
                                .padding(vertical = 10.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
