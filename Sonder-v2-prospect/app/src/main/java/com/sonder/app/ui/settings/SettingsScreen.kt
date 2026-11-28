package com.sonder.app.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.sonder.app.data.prefs.SonderSettings
import com.sonder.app.data.prefs.ThemeMode
import com.sonder.app.ui.components.GlassCard
import com.sonder.app.ui.components.SectionEyebrow
import com.sonder.app.ui.theme.readableAccent
import com.sonder.app.util.DateDisplayFormatter
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: SonderSettings,
    darkTheme: Boolean,
    onThemeModeChange: (ThemeMode) -> Unit,
    onSetupPin: (String) -> Unit,
    onAppLockToggle: (Boolean) -> Unit,
    onReminderToggle: (Boolean, Int, Int) -> Unit,
    onExportCsv: (Uri) -> Unit,
    onExportEncrypted: (Uri, String) -> Unit,
    onRestoreEncrypted: (Uri, String) -> Unit,
    onDeleteAll: () -> Unit
) {
    val accent = readableAccent(darkTheme)
    var showPinDialog by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var showPasswordDialogForExport by remember { mutableStateOf(false) }
    var backupPassword by remember { mutableStateOf("") }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var restorePassword by remember { mutableStateOf("") }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    var reminderHour by remember(settings.reminderHour) { mutableIntStateOf(settings.reminderHour) }
    var reminderMinute by remember(settings.reminderMinute) { mutableIntStateOf(settings.reminderMinute) }

    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> if (uri != null) onExportCsv(uri) }

    val encryptedExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> if (uri != null) onExportEncrypted(uri, backupPassword) }

    val encryptedRestoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) pendingRestoreUri = uri }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // Theme & Readability
        item {
            GlassCard {
                SectionEyebrow(text = "Appearance & Readability", color = accent)
                Spacer(modifier = Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = settings.themeMode == mode,
                            onClick = { onThemeModeChange(mode) },
                            label = {
                                Text(
                                    when (mode) {
                                        ThemeMode.AUTO -> "Auto (Time of day)"
                                        ThemeMode.LIGHT -> "Light"
                                        ThemeMode.DARK -> "Dark"
                                    }
                                )
                            }
                        )
                    }
                }
            }
        }

        // Privacy Lock
        item {
            GlassCard {
                SectionEyebrow(text = "Privacy", color = accent)
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "App lock",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (settings.pinHash != null) "PIN & biometric ready" else "Set a 4–8 digit PIN first",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.appLockEnabled && settings.pinHash != null,
                        onCheckedChange = { enabled ->
                            if (enabled && settings.pinHash == null) showPinDialog = true
                            else onAppLockToggle(enabled)
                        }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        pinInput = ""
                        showPinDialog = true
                    },
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(if (settings.pinHash == null) "Set PIN" else "Change PIN")
                }
            }
        }

        // Gentle Reminder
        item {
            GlassCard {
                SectionEyebrow(text = "Gentle reminder", color = accent)
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Evening nudge",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "At most once a day, only if nothing is noted yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.reminderEnabled,
                        onCheckedChange = { onReminderToggle(it, reminderHour, reminderMinute) }
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(20 to "20:00", 21 to "21:00", 22 to "22:00", 23 to "23:00").forEach { (h, label) ->
                        FilterChip(
                            selected = reminderHour == h,
                            onClick = {
                                reminderHour = h
                                reminderMinute = 0
                                if (settings.reminderEnabled) {
                                    onReminderToggle(true, h, 0)
                                }
                            },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }

        // Backup & Export
        item {
            GlassCard {
                SectionEyebrow(text = "Backup & Export", color = accent)
                Spacer(modifier = Modifier.height(8.dp))
                if (settings.lastBackupAt > 0L) {
                    val dateStr = DateDisplayFormatter.toDisplayString(
                        Instant.ofEpochMilli(settings.lastBackupAt).atZone(ZoneId.systemDefault()).toLocalDate()
                    )
                    Text(
                        text = "Last backup: \$dateStr",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { csvLauncher.launch("sonder_moments.csv") },
                        shape = RoundedCornerShape(14.dp)
                    ) { Text("Export CSV") }

                    Button(
                        onClick = {
                            backupPassword = ""
                            showPasswordDialogForExport = true
                        },
                        shape = RoundedCornerShape(14.dp)
                    ) { Text("Encrypted backup") }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { encryptedRestoreLauncher.launch(arrayOf("application/json", "*/*")) },
                    shape = RoundedCornerShape(14.dp)
                ) { Text("Restore encrypted backup") }
            }
        }

        // Danger Zone
        item {
            GlassCard {
                SectionEyebrow(text = "Your data", color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Everything stays on your device. You can wipe all moments whenever you want.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = { showDeleteConfirm = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Delete all moments")
                }
            }
        }
    }

    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = { Text("Set PIN") },
            text = {
                OutlinedTextField(
                    value = pinInput,
                    onValueChange = { if (it.length <= 8) pinInput = it },
                    label = { Text("4–8 digits") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (pinInput.length >= 4) {
                            onSetupPin(pinInput)
                            showPinDialog = false
                        }
                    }
                ) { Text("Save PIN") }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showPasswordDialogForExport) {
        AlertDialog(
            onDismissRequest = { showPasswordDialogForExport = false },
            title = { Text("Encrypt backup") },
            text = {
                OutlinedTextField(
                    value = backupPassword,
                    onValueChange = { backupPassword = it },
                    label = { Text("Backup passphrase") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (backupPassword.isNotBlank()) {
                            showPasswordDialogForExport = false
                            encryptedExportLauncher.launch(DateDisplayFormatter.backupFilename())
                        }
                    }
                ) { Text("Choose location") }
            },
            dismissButton = {
                TextButton(onClick = { showPasswordDialogForExport = false }) { Text("Cancel") }
            }
        )
    }

    val uriToRestore = pendingRestoreUri
    if (uriToRestore != null) {
        AlertDialog(
            onDismissRequest = { pendingRestoreUri = null },
            title = { Text("Restore backup") },
            text = {
                OutlinedTextField(
                    value = restorePassword,
                    onValueChange = { restorePassword = it },
                    label = { Text("Backup passphrase") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRestoreEncrypted(uriToRestore, restorePassword)
                        pendingRestoreUri = null
                        restorePassword = ""
                    }
                ) { Text("Restore") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestoreUri = null }) { Text("Cancel") }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete all moments?") },
            text = { Text("This permanently removes every moment and voice note from this device.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteAll()
                    }
                ) { Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}
