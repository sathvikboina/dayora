package com.dayora.app.ui.home

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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.sp
import com.dayora.app.data.local.entity.DailyEntryEntity
import com.dayora.app.data.local.entity.DayState
import com.dayora.app.ml.LightweightLexiconToneRouter
import com.dayora.app.ui.components.LivingAuraBackground
import com.dayora.app.ui.theme.TimeOfDayMood
import com.dayora.app.util.DateDisplayFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    goodDayCount: Int,
    firstEntryIsoDate: String?,
    todayEntry: DailyEntryEntity?,
    onSaveEntry: (DayState, Boolean, Boolean, String, List<String>) -> Unit
) {
    val mood = remember { TimeOfDayMood.current() }
    var showCaptureSheet by remember { mutableStateOf(false) }

    LivingAuraBackground(mood = mood) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 28.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "DAYORA",
                style = MaterialTheme.typography.labelLarge,
                letterSpacing = 4.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = goodDayCount.toString(),
                fontSize = 88.sp,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (firstEntryIsoDate != null) {
                    "Collecting since ${DateDisplayFormatter.isoToDisplayString(firstEntryIsoDate)}"
                } else {
                    "Your quiet vault of days that felt good"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(48.dp))
            Button(
                onClick = { showCaptureSheet = true },
                modifier = Modifier.fillMaxWidth(0.75f)
            ) {
                val label = todayEntry?.dayState?.label?.let { "Today: $it · Edit" } ?: "Capture today"
                Text(text = label, modifier = Modifier.padding(vertical = 6.dp))
            }
        }
    }

    if (showCaptureSheet) {
        val toneRouter = remember { LightweightLexiconToneRouter() }
        var selectedState by remember { mutableStateOf(todayEntry?.dayState ?: DayState.WARM) }
        var countAsGood by remember { mutableStateOf(todayEntry?.isGoodDay ?: selectedState.defaultCountsAsGood) }
        var reasonText by remember { mutableStateOf(todayEntry?.reasonText ?: "") }
        var selectedSparks by remember { mutableStateOf(emptySet<String>()) }

        ModalBottomSheet(onDismissRequest = { showCaptureSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "How did today feel?",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DayState.entries.forEach { state ->
                        FilterChip(
                            selected = selectedState == state,
                            onClick = {
                                selectedState = state
                                countAsGood = state.defaultCountsAsGood
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
                        toneRouter.suggestDayState(text)?.let { suggested ->
                            selectedState = suggested
                            countAsGood = suggested.defaultCountsAsGood
                        }
                    },
                    label = {
                        Text(
                            if (selectedState == DayState.HEAVY) "Want to put something down? (optional)"
                            else "What made today good? (optional)"
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )

                Spacer(modifier = Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    DayState.DEFAULT_SPARKS.forEach { spark ->
                        val isSelected = spark in selectedSparks
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedSparks = if (isSelected) selectedSparks - spark else selectedSparks + spark
                            },
                            label = { Text(spark) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (selectedState == DayState.HEAVY) "Save quietly to Rain Room"
                        else "Count this as a good day",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(
                        checked = countAsGood,
                        onCheckedChange = { countAsGood = it }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { showCaptureSheet = false }) { Text("Cancel") }
                    Button(
                        onClick = {
                            val isRainRoom = selectedState.defaultRoutesToRainRoom && !countAsGood
                            onSaveEntry(selectedState, countAsGood, isRainRoom, reasonText.trim(), selectedSparks.toList())
                            showCaptureSheet = false
                        }
                    ) { Text("Save") }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
