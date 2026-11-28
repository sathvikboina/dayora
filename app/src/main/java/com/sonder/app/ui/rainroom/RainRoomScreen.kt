package com.sonder.app.ui.rainroom

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sonder.app.data.local.entity.MomentEntity
import com.sonder.app.ui.components.ScreenHeader
import com.sonder.app.util.DateDisplayFormatter

private val RainInk = Color(0xFFE6ECF5)
private val RainMuted = Color(0xFF9FB0C9)
private val RainSurface = Color(0xCC1A2130)

@Composable
fun RainRoomScreen(
    rainMoments: List<MomentEntity>,
    unsentMoments: List<MomentEntity>,
    vaultPinConfigured: Boolean,
    vaultUnlocked: Boolean,
    onUnlockVault: (String) -> Boolean,
    onLockVault: () -> Unit,
    onAddRain: () -> Unit,
    onAddUnsent: () -> Unit,
    onEditMoment: (MomentEntity) -> Unit,
    onLetGo: (MomentEntity) -> Unit,
    onDelete: (MomentEntity) -> Unit
) {
    if (vaultPinConfigured && !vaultUnlocked) {
        VaultGate(onUnlockVault = onUnlockVault)
        return
    }

    var showUnsent by remember { mutableStateOf(false) }
    var letGoTarget by remember { mutableStateOf<MomentEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<MomentEntity?>(null) }
    val moments = if (showUnsent) unsentMoments else rainMoments

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(18.dp))
            ScreenHeader(
                title = "The Rain Room",
                subtitle = if (showUnsent) {
                    "Words you never have to send."
                } else {
                    "A private place to set heavy moments down."
                },
                trailing = {
                    if (vaultPinConfigured) {
                        TextButton(onClick = onLockVault) {
                            Icon(Icons.Outlined.Lock, contentDescription = null, tint = RainMuted)
                        }
                    }
                }
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !showUnsent,
                    onClick = { showUnsent = false },
                    label = { Text("Rain notes (${rainMoments.size})") }
                )
                FilterChip(
                    selected = showUnsent,
                    onClick = { showUnsent = true },
                    label = { Text("Unsent (${unsentMoments.size})") }
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onAddRain, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.WaterDrop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.size(6.dp))
                    Text("Something heavy")
                }
                OutlinedButton(onClick = onAddUnsent, modifier = Modifier.weight(1f)) {
                    Text("Keep unsent")
                }
            }
        }

        if (moments.isEmpty()) {
            item {
                Text(
                    text = if (showUnsent) {
                        "Nothing unsent. This drawer stays empty until you need it."
                    } else {
                        "The room is quiet. Heavy moments will rest here when they come."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = RainMuted
                )
            }
        }

        items(moments.size) { index ->
            val moment = moments[index]
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(RainSurface, RoundedCornerShape(16.dp))
                    .clickable { onEditMoment(moment) }
                    .padding(14.dp)
            ) {
                Text(
                    DateDisplayFormatter.isoToDisplayString(moment.localDate),
                    style = MaterialTheme.typography.labelSmall,
                    color = RainMuted
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = moment.bodyText?.takeIf { it.isNotBlank() }
                        ?: if (moment.audioFileName != null) "A voice note" else "A quiet mark",
                    style = MaterialTheme.typography.bodyLarge,
                    color = RainInk
                )
                if (moment.audioFileName != null) {
                    Text("Voice note · ${(moment.audioDurationMs ?: 0L) / 1000L}s", style = MaterialTheme.typography.labelSmall, color = RainMuted)
                }
                if (!moment.bodyText.isNullOrBlank() || moment.audioFileName != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { letGoTarget = moment }) {
                            Text("Let it go", color = RainMuted)
                        }
                        if (showUnsent) {
                            TextButton(onClick = { deleteTarget = moment }) {
                                Text("Delete", color = Color(0xFFD08A8A))
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(28.dp)) }
    }

    letGoTarget?.let { moment ->
        AlertDialog(
            onDismissRequest = { letGoTarget = null },
            title = { Text("Let this go?") },
            text = {
                Text(
                    "The words and voice note will be cleared. Only the date and " +
                        "its quiet mark remain."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    letGoTarget = null
                    onLetGo(moment)
                }) { Text("Let it go") }
            },
            dismissButton = { TextButton(onClick = { letGoTarget = null }) { Text("Keep it") } }
        )
    }

    deleteTarget?.let { moment ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete this note?") },
            text = { Text("It will be removed from your vault for good.") },
            confirmButton = {
                TextButton(onClick = {
                    deleteTarget = null
                    onDelete(moment)
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun VaultGate(onUnlockVault: (String) -> Boolean) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 34.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Outlined.Lock,
            contentDescription = null,
            tint = RainMuted,
            modifier = Modifier.size(40.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "The sanctuaries are locked",
            style = MaterialTheme.typography.headlineMedium,
            color = RainInk
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            "Enter the vault PIN to step into the Rain Room or Unsent drawer.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = RainMuted
        )
        Spacer(modifier = Modifier.height(20.dp))
        OutlinedTextField(
            value = pin,
            onValueChange = {
                pin = it.filter { ch -> ch.isDigit() }.take(8)
                error = null
            },
            label = { Text("Vault PIN") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        if (error != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(error!!, style = MaterialTheme.typography.labelSmall, color = Color(0xFFE59A9A))
        }
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = {
                if (!onUnlockVault(pin)) error = "That PIN didn't match."
            },
            enabled = pin.length >= 4,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Unlock") }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Screenshots are blocked while this room is open.",
            style = MaterialTheme.typography.labelSmall,
            color = RainMuted.copy(alpha = 0.7f)
        )
    }
}
