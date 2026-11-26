package com.sonder.app.ui.capture

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.sonder.app.data.local.entity.DailyEntryEntity
import com.sonder.app.data.local.entity.DayState
import com.sonder.app.data.local.entity.EntryTagEntity
import com.sonder.app.ml.EntityExtractor
import com.sonder.app.ml.LightweightLexiconToneRouter
import com.sonder.app.ui.components.DayStateMarker
import com.sonder.app.util.DateDisplayFormatter
import java.time.LocalDate

data class CaptureDraft(
    val date: LocalDate,
    val state: DayState,
    val countAsGood: Boolean,
    val reason: String,
    val sparks: Set<String>,
    val suggestedState: DayState?
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CaptureSheet(
    date: LocalDate,
    existing: DailyEntryEntity?,
    existingSparks: Set<String> = emptySet(),
    allowMidnightGrace: Boolean = false,
    forceRainRoom: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (CaptureDraft) -> Unit,
    onDelete: ((DailyEntryEntity) -> Unit)? = null,
    onLetGo: ((DailyEntryEntity) -> Unit)? = null
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val toneRouter = remember { LightweightLexiconToneRouter() }

    var useYesterday by remember { mutableStateOf(false) }
    var selectedState by remember {
        mutableStateOf(existing?.dayState ?: if (forceRainRoom) DayState.HEAVY else null)
    }
    var countAsGood by remember {
        mutableStateOf(
            existing?.isGoodDay
                ?: (selectedState ?: if (forceRainRoom) DayState.HEAVY else DayState.ORDINARY)
                    .defaultCountsAsGood
        )
    }
    var reasonText by remember { mutableStateOf(existing?.reasonText.orEmpty()) }
    var selectedSparks by remember { mutableStateOf(existingSparks) }
    var userPickedState by remember { mutableStateOf(existing != null) }
    var suggestedState by remember { mutableStateOf<DayState?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showLetGoConfirm by remember { mutableStateOf(false) }

    val effectiveDate = if (useYesterday) date.minusDays(1) else date
    val isHeavy = selectedState == DayState.HEAVY

    val suggestions = remember(reasonText, selectedSparks) {
        EntityExtractor.extract(reasonText, selectedSparks + DayState.DEFAULT_SPARKS.toSet())
            .map { it.tag }
            .distinct()
            .take(3)
    }

    val voiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val spoken = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
        if (!spoken.isNullOrBlank()) {
            val joiner = if (reasonText.isBlank()) "" else " "
            reasonText = reasonText + joiner + spoken
        }
    }

    fun launchVoice() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak a memory…")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        runCatching { voiceLauncher.launch(intent) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) launchVoice()
    }

    fun onMicClick() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) launchVoice() else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (existing != null) "Edit this day" else "How did today feel?",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = DateDisplayFormatter.toDisplayString(effectiveDate),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            if (allowMidnightGrace && existing == null) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("This was yesterday", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Midnight grace — log before 4 AM",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                        )
                    }
                    Switch(checked = useYesterday, onCheckedChange = { useYesterday = it })
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DayState.entries.forEach { state ->
                    FilterChip(
                        selected = selectedState == state,
                        onClick = {
                            selectedState = state
                            userPickedState = true
                            countAsGood = state.defaultCountsAsGood
                        },
                        leadingIcon = {
                            DayStateMarker(state = state, counted = true, size = 18.dp)
                        },
                        label = { Text(state.label) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = reasonText,
                onValueChange = { text ->
                    reasonText = text
                    val suggestion = toneRouter.suggestDayState(text)
                    if (suggestion != null) {
                        suggestedState = suggestion
                        if (!userPickedState) {
                            selectedState = suggestion
                            countAsGood = suggestion.defaultCountsAsGood
                        }
                    }
                },
                label = {
                    Text(
                        if (isHeavy) "Want to put something down? (optional)"
                        else "What made today good? (optional)"
                    )
                },
                trailingIcon = {
                    IconButton(onClick = { onMicClick() }) {
                        Icon(Icons.Outlined.Mic, contentDescription = "Speak instead")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 6
            )

            if (suggestedState != null && !userPickedState) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Quietly reading this as ${suggestedState?.label} — tap a state to change.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "SPARKS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                DayState.DEFAULT_SPARKS.forEach { spark ->
                    val isSelected = spark in selectedSparks
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            selectedSparks = if (isSelected) selectedSparks - spark
                            else selectedSparks + spark
                        },
                        label = { Text(spark) }
                    )
                }
            }

            if (suggestions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "SUGGESTED",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    suggestions.forEach { tag ->
                        AssistChip(
                            onClick = { selectedSparks = selectedSparks + tag },
                            label = { Text(tag) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Count this as a good day",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (isHeavy && !countAsGood) {
                        Text(
                            text = "Heavy days rest quietly in the Rain Room.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                        )
                    }
                }
                Switch(checked = countAsGood, onCheckedChange = { countAsGood = it })
            }

            Spacer(modifier = Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (existing != null && onDelete != null) {
                    TextButton(onClick = { showDeleteConfirm = true }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
                if (existing != null && existing.isRainRoom && onLetGo != null &&
                    !existing.reasonText.isNullOrBlank()
                ) {
                    TextButton(onClick = { showLetGoConfirm = true }) {
                        Text("Let it go")
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                Button(
                    onClick = {
                        val state = selectedState ?: DayState.ORDINARY
                        onSave(
                            CaptureDraft(
                                date = effectiveDate,
                                state = state,
                                countAsGood = countAsGood,
                                reason = reasonText.trim(),
                                sparks = selectedSparks,
                                suggestedState = suggestedState
                            )
                        )
                    }
                ) {
                    Text(if (existing != null) "Update" else "Save")
                }
            }
        }
    }

    if (showDeleteConfirm && existing != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete this day?") },
            text = { Text("This memory will be removed from your vault.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete?.invoke(existing)
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Keep") }
            }
        )
    }

    if (showLetGoConfirm && existing != null) {
        AlertDialog(
            onDismissRequest = { showLetGoConfirm = false },
            title = { Text("Let this go?") },
            text = {
                Text(
                    "The words will be cleared, like burning a piece of paper. " +
                        "Only the date and the quiet mark remain."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showLetGoConfirm = false
                    onLetGo?.invoke(existing)
                }) { Text("Let it go") }
            },
            dismissButton = {
                TextButton(onClick = { showLetGoConfirm = false }) { Text("Keep it") }
            }
        )
    }
}
