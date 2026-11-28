package com.sonder.app.ui.threads

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sonder.app.data.local.entity.MomentEntity
import com.sonder.app.ui.ThreadSummary
import com.sonder.app.ui.components.EmptyWhisper
import com.sonder.app.ui.components.GlassCard
import com.sonder.app.ui.components.SectionEyebrow
import com.sonder.app.ui.theme.readableAccent
import com.sonder.app.ui.theme.readableSecondaryAccent
import com.sonder.app.util.DateDisplayFormatter

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThreadsScreen(
    threads: List<ThreadSummary>,
    initialFilterNormalized: String? = null,
    semanticMatches: List<MomentEntity>,
    darkTheme: Boolean,
    onSearchSemantic: (String) -> Unit,
    onRenameTagGlobally: (String, String) -> Unit
) {
    var selectedType by remember { mutableStateOf<String?>(null) }
    var selectedThreadNorm by remember(initialFilterNormalized) { mutableStateOf(initialFilterNormalized) }
    var searchQuery by remember { mutableStateOf("") }
    var renamingThread by remember { mutableStateOf<ThreadSummary?>(null) }
    var renameDraft by remember { mutableStateOf("") }

    val accent = readableAccent(darkTheme)
    val secondaryAccent = readableSecondaryAccent(darkTheme)

    val filteredThreads = remember(threads, selectedType, selectedThreadNorm) {
        threads.filter { t ->
            (selectedType == null || t.tagType == selectedType) &&
                (selectedThreadNorm == null || t.normalizedName == selectedThreadNorm)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Threads",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // Offline Semantic Search ("When did I feel...")
        item {
            GlassCard {
                SectionEyebrow(text = "Memory search", color = accent)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        onSearchSemantic(it)
                    },
                    label = { Text("When did I feel… or remember…") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Outlined.Search, contentDescription = null)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp)
                )

                if (semanticMatches.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        semanticMatches.forEach { m ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = DateDisplayFormatter.isoToDisplayString(m.localDate),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = accent
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = m.bodyText.orEmpty(),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Category Filter Chips
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    null to "All",
                    "PERSON" to "People",
                    "PLACE" to "Places",
                    "THING" to "Things",
                    "SPARK" to "Sparks"
                ).forEach { (type, label) ->
                    FilterChip(
                        selected = selectedType == type && selectedThreadNorm == null,
                        onClick = {
                            selectedType = type
                            selectedThreadNorm = null
                        },
                        label = { Text(label) }
                    )
                }
                if (selectedThreadNorm != null) {
                    FilterChip(
                        selected = true,
                        onClick = { selectedThreadNorm = null },
                        label = { Text("Clear filter") }
                    )
                }
            }
        }

        if (filteredThreads.isEmpty()) {
            item {
                GlassCard {
                    EmptyWhisper(
                        title = "No threads yet",
                        subtitle = "Sparks, people, and places appear here as you capture moments."
                    )
                }
            }
        } else {
            items(filteredThreads, key = { "\${it.tagType}:\${it.normalizedName}" }) { thread ->
                val icon: ImageVector = when (thread.tagType) {
                    "PERSON" -> Icons.Outlined.Person
                    "PLACE" -> Icons.Outlined.LocationOn
                    "THING" -> Icons.Outlined.AutoAwesome
                    else -> Icons.Outlined.Bookmark
                }
                GlassCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    selectedThreadNorm =
                                        if (selectedThreadNorm == thread.normalizedName) null
                                        else thread.normalizedName
                                }
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = secondaryAccent,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = thread.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "\${thread.count} \${if (thread.count == 1) "moment" else "moments"} · last \${DateDisplayFormatter.isoToDisplayString(thread.lastSeenIso)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                renamingThread = thread
                                renameDraft = thread.name
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = "Rename thread",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    thread.recentExcerpts.forEach { (dateStr, excerpt) ->
                        Text(
                            text = "\$dateStr — \$excerpt",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }

    val targetRename = renamingThread
    if (targetRename != null) {
        AlertDialog(
            onDismissRequest = { renamingThread = null },
            title = { Text("Rename "\${targetRename.name}"") },
            text = {
                OutlinedTextField(
                    value = renameDraft,
                    onValueChange = { renameDraft = it },
                    label = { Text("New name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRenameTagGlobally(targetRename.normalizedName, renameDraft)
                        renamingThread = null
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renamingThread = null }) { Text("Cancel") }
            }
        )
    }
}
