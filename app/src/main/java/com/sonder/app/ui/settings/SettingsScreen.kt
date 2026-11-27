package com.sonder.app.ui.settings

import android.app.TimePickerDialog
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sonder.app.data.prefs.SonderSettings
import com.sonder.app.data.prefs.ThemeMode
import com.sonder.app.ui.RestoreReport
import com.sonder.app.util.DateDisplayFormatter
import kotlinx.coroutines.launch
import java.time.LocalDate

private enum class PasswordAction { BACKUP, RESTORE }

@Composable
fun SettingsScreen(
    settings: SonderSettings,
    entryCount: Int,
    onBack: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onThemeMode: (ThemeMode) -> Unit,
    onEnableLock: (String) -> Unit,
    onDisableLock: () -> Unit,
    onUpdatePin: (String) -> Unit,
    onSetReminder: (Boolean, Int, Int) -> Unit,
    onExportCsv: suspend (Uri) -> Unit,
    onExportBackup: suspend (Uri, String) -> Unit,
    onRestore: suspend (Uri, String) -> RestoreReport,
    onDeleteAll: suspend () -> Unit,
    onNotify: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showPinDialog by remember { mutableStateOf(false) }
    var pinDialogIsChange by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var passwordAction by remember { mutableStateOf(PasswordAction.BACKUP) }
    var pendingPassword by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching { onExportCsv(uri) }
                    .onSuccess { onNotify("CSV exported with DD-MM-YYYY dates.") }
                    .onFailure { onNotify("Could not export CSV: ${it.message}") }
            }
        }
    }

    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val password = pendingPassword
        pendingPassword = null
        if (uri != null && password != null) {
            scope.launch {
                runCatching { onExportBackup(uri, password) }
                    .onSuccess { onNotify("Encrypted backup saved.") }
                    .onFailure { onNotify("Backup failed: ${it.message}") }
            }
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        val password = pendingPassword
        pendingPassword = null
        if (uri != null && password != null) {
            scope.launch {
                runCatching { onRestore(uri, password) }
                    .onSuccess { report: RestoreReport ->
                        onNotify(
                            if (report.imported == 0) "Nothing newer to restore."
                            else "Restored ${report.imported} ${if (report.imported == 1) "memory" else "memories"}."
                        )
                    }
                    .onFailure { onNotify("Restore failed — wrong password or damaged file.") }
            }
        }
    }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onSetReminder(true, settings.reminderHour, settings.reminderMinute)
            onNotify("A gentle evening reminder is set.")
        } else {
            onNotify("Notifications are off. You can still enable the reminder later.")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Back"
                )
            }
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
        SectionTitle("APPEARANCE")
        Text(
            text = "Auto Aura follows the light of the day.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeMode.entries.forEach { mode ->
                FilterChip(
                    selected = settings.themeMode == mode,
                    onClick = { onThemeMode(mode) },
                    label = {
                        Text(
                            when (mode) {
                                ThemeMode.AUTO -> "Auto Aura"
                                ThemeMode.LIGHT -> "Light"
                                ThemeMode.DARK -> "Dark"
                            }
                        )
                    }
                )
            }
        }

        SettingsDivider()
        SectionTitle("PRIVACY")
        ToggleRow(
            title = "App lock",
            subtitle = if (settings.pinHash == null) {
                "Set a PIN first; biometrics unlock when available."
            } else {
                "Biometric or PIN unlock when Sonder opens."
            },
            checked = settings.appLockEnabled,
            onCheckedChange = { enabled ->
                if (enabled) {
                    pinDialogIsChange = false
                    showPinDialog = true
                } else {
                    onDisableLock()
                }
            }
        )
        if (settings.pinHash != null) {
            TextButton(onClick = {
                pinDialogIsChange = true
                showPinDialog = true
            }) { Text("Change PIN") }
        }
        Text(
            text = "No account, no cloud, no analytics. Your vault lives only on this phone.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
        )

        SettingsDivider()
        SectionTitle("REMINDER")
        ToggleRow(
            title = "One quiet evening nudge",
            subtitle = "Around ${formatTime(settings.reminderHour, settings.reminderMinute)}. Skipped automatically if today is already logged.",
            checked = settings.reminderEnabled,
            onCheckedChange = { enabled ->
                if (!enabled) {
                    onSetReminder(false, settings.reminderHour, settings.reminderMinute)
                } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    onSetReminder(true, settings.reminderHour, settings.reminderMinute)
                    onNotify("A gentle evening reminder is set.")
                }
            }
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = {
                TimePickerDialog(
                    context,
                    { _, hour, minute ->
                        onSetReminder(settings.reminderEnabled, hour, minute)
                    },
                    settings.reminderHour,
                    settings.reminderMinute,
                    true
                ).show()
            }) {
                Text("Change time · ${formatTime(settings.reminderHour, settings.reminderMinute)}")
            }
        }
        TextButton(onClick = onOpenBatterySettings) {
            Text("Reminders not arriving? OEM battery help")
        }

        SettingsDivider()
        SectionTitle("YOUR DATA")
        Text(
            text = "$entryCount ${if (entryCount == 1) "memory" else "memories"} on this device. Embeddings are recomputed on restore.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { csvLauncher.launch("sonder_export_${DateDisplayFormatter.toDisplayString(LocalDate.now())}.csv") },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Export CSV (plain text)") }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                passwordAction = PasswordAction.BACKUP
                showPasswordDialog = true
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Encrypted backup (AES-256-GCM)") }
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(
            onClick = {
                passwordAction = PasswordAction.RESTORE
                showPasswordDialog = true
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Restore from encrypted backup") }
        Text(
            text = "A lost password cannot be recovered. Keep it somewhere safe.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(6.dp))
        TextButton(
            onClick = { showDeleteConfirm = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Delete all data", color = MaterialTheme.colorScheme.error)
        }

        SettingsDivider()
        SectionTitle("ABOUT")
        Text(
            text = "Sonder is a living memory vault — no streaks, no scores, no guilt. " +
                "It runs completely offline: tone routing, entity threads and semantic rescue " +
                "all happen on your phone's silicon.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Version 1.0 · No data collected. No ads. No trackers.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
            textAlign = TextAlign.Start
        )

        Spacer(modifier = Modifier.height(40.dp))
    }

    if (showPinDialog) {
        PinDialog(
            title = if (pinDialogIsChange) "Change your PIN" else "Create a PIN",
            onDismiss = { showPinDialog = false },
            onConfirm = { pin ->
                if (pinDialogIsChange) onUpdatePin(pin) else onEnableLock(pin)
                showPinDialog = false
                onNotify(if (pinDialogIsChange) "PIN updated." else "App lock is on.")
            }
        )
    }

    if (showPasswordDialog) {
        PasswordDialog(
            title = if (passwordAction == PasswordAction.BACKUP) {
                "Protect your backup"
            } else {
                "Restore a backup"
            },
            confirmLabel = if (passwordAction == PasswordAction.BACKUP) "Choose where to save" else "Choose backup file",
            requireConfirmation = passwordAction == PasswordAction.BACKUP,
            onDismiss = { showPasswordDialog = false },
            onConfirm = { password ->
                showPasswordDialog = false
                pendingPassword = password
                if (passwordAction == PasswordAction.BACKUP) {
                    backupLauncher.launch(DateDisplayFormatter.backupFilename())
                } else {
                    restoreLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete everything?") },
            text = {
                Text(
                    "Every memory, tag and vector on this phone will be erased. " +
                        "This cannot be undone unless you have a backup."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    scope.launch {
                        runCatching { onDeleteAll() }
                            .onSuccess { onNotify("All local data erased.") }
                            .onFailure { onNotify("Could not erase data: ${it.message}") }
                    }
                }) { Text("Erase everything", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Keep my vault") }
            }
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
    )
    Spacer(modifier = Modifier.height(6.dp))
}

@Composable
private fun SettingsDivider() {
    Spacer(modifier = Modifier.height(20.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
    Spacer(modifier = Modifier.height(18.dp))
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun PinDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val valid = pin.length in 4..8 && pin == confirm
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    "4–8 digits. If you forget it, the only way back in is erasing local data.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { value -> pin = value.filter { it.isDigit() }.take(8) },
                    label = { Text("PIN") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { value -> confirm = value.filter { it.isDigit() }.take(8) },
                    label = { Text("Confirm PIN") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(pin) }, enabled = valid) { Text("Save PIN") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun PasswordDialog(
    title: String,
    confirmLabel: String,
    requireConfirmation: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val valid = password.length >= 8 && (!requireConfirmation || password == confirm)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    "Use at least 8 characters. This password is never stored anywhere.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
                if (requireConfirmation) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirm,
                        onValueChange = { confirm = it },
                        label = { Text("Confirm password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(password) }, enabled = valid) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun formatTime(hour: Int, minute: Int): String {
    val suffix = if (hour < 12) "AM" else "PM"
    val displayHour = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    return "%d:%02d %s".format(displayHour, minute, suffix)
}
