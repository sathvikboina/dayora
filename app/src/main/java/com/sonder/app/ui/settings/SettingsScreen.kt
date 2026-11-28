package com.sonder.app.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sonder.app.data.prefs.SonderSettings
import com.sonder.app.data.prefs.ThemeMode
import com.sonder.app.ui.components.ScreenHeader
import com.sonder.app.util.DateDisplayFormatter
import java.time.LocalDate

@Composable
fun SettingsScreen(
    settings: SonderSettings,
    momentCount: Int,
    onBack: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onThemeMode: (ThemeMode) -> Unit,
    onEnableLock: (String) -> Unit,
    onDisableLock: () -> Unit,
    onUpdatePin: (String) -> Unit,
    onSetVaultPin: (String) -> Unit,
    onDisableVaultLock: () -> Unit,
    onSetReminder: (Boolean, Int, Int) -> Unit,
    onExportCsv: (Uri) -> Unit,
    onExportBackup: (Uri, String) -> Unit,
    onRestore: (Uri, String) -> Unit,
    onDeleteAll: () -> Unit,
    onNotify: (String) -> Unit
) {
    var showPinDialog by remember { mutableStateOf(false) }
    var showUpdatePin by remember { mutableStateOf(false) }
    var showVaultDialog by remember { mutableStateOf(false) }
    var showBackupPassword by remember { mutableStateOf(false) }
    var showRestorePassword by remember { mutableStateOf(false) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var backupTarget by remember { mutableStateOf<Uri?>(null) }
    var showDeleteAll by remember { mutableStateOf(false) }
    var reminderHour by remember(settings.reminderHour) { mutableStateOf(settings.reminderHour) }
    var reminderMinute by remember(settings.reminderMinute) { mutableStateOf(settings.reminderMinute) }

    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> uri?.let(onExportCsv) }

    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            pendingRestoreUri = null
            backupTarget = uri
            showBackupPassword = true
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pendingRestoreUri = uri
            showRestorePassword = true
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(18.dp))
            ScreenHeader(
                title = "Settings",
                subtitle = "$momentCount moments on this device",
                trailing = { TextButton(onClick = onBack) { Text("Back") } }
            )
        }

        item { Section("APPEARANCE") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = settings.themeMode == mode,
                        onClick = { onThemeMode(mode) },
                        label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) }
                    )
                }
            }
        }

        item { Section("PRIVACY") }
        item {
            SettingsRow(
                title = "App lock",
                subtitle = if (settings.pinHash == null) "No PIN set" else "PIN + biometrics",
                checked = settings.appLockEnabled,
                onCheckedChange = { enabled ->
                    if (enabled) showPinDialog = true else onDisableLock()
                }
            )
        }
        if (settings.pinHash != null && settings.appLockEnabled) {
            item {
                TextButton(onClick = { showUpdatePin = true }) { Text("Change PIN") }
            }
        }
        item {
            SettingsRow(
                title = "Separate vault lock",
                subtitle = if (settings.vaultPinHash == null) {
                    "Guards the Rain Room and Unsent drawer"
                } else {
                    "Vault PIN is on"
                },
                checked = settings.vaultPinHash != null,
                onCheckedChange = { enabled ->
                    if (enabled) showVaultDialog = true else onDisableVaultLock()
                }
            )
        }

        item { Section("A QUIET REMINDER") }
        item {
            SettingsRow(
                title = "Evening nudge",
                subtitle = "One optional notice. No streaks, no guilt.",
                checked = settings.reminderEnabled,
                onCheckedChange = { enabled ->
                    onSetReminder(enabled, reminderHour, reminderMinute)
                }
            )
        }
        if (settings.reminderEnabled) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = reminderHour.toString().padStart(2, '0'),
                        onValueChange = { text ->
                            val value = text.filter { it.isDigit() }.take(2).toIntOrNull()
                            if (value != null && value in 0..23) {
                                reminderHour = value
                                onSetReminder(true, value, reminderMinute)
                            }
                        },
                        label = { Text("Hour") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = reminderMinute.toString().padStart(2, '0'),
                        onValueChange = { text ->
                            val value = text.filter { it.isDigit() }.take(2).toIntOrNull()
                            if (value != null && value in 0..59) {
                                reminderMinute = value
                                onSetReminder(true, reminderHour, value)
                            }
                        },
                        label = { Text("Minute") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            item {
                TextButton(onClick = onOpenBatterySettings) {
                    Text("Allow reminders through battery optimisation")
                }
            }
        }

        item { Section("BACKUP & PORTABILITY") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = {
                    csvLauncher.launch("sonder_moments_${DateDisplayFormatter.toDisplayString(LocalDate.now())}.csv")
                }) { Text("Export a plain CSV") }
                TextButton(onClick = {
                    backupLauncher.launch("sonder_${DateDisplayFormatter.toDisplayString(LocalDate.now())}.sonder")
                }) { Text("Export an encrypted .sonder archive") }
                TextButton(onClick = {
                    restoreLauncher.launch(arrayOf("application/octet-stream", "application/json", "*/*"))
                }) { Text("Restore from a backup") }
                Text(
                    "Last backup: " + if (settings.lastBackupAt > 0L) {
                        DateDisplayFormatter.isoToDisplayString(
                            java.time.Instant.ofEpochMilli(settings.lastBackupAt)
                                .atZone(java.time.ZoneId.systemDefault())
                                .toLocalDate()
                                .let { DateDisplayFormatter.toStorageIso(it) }
                        )
                    } else {
                        "never"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                )
            }
        }

        item { Section("THIS DEVICE") }
        item {
            Card(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Everything stays on this phone.", fontWeight = FontWeight.SemiBold)
                    Text(
                        "No account, no cloud, no analytics. Airplane mode changes nothing.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                    )
                }
            }
        }
        item {
            TextButton(
                onClick = { showDeleteAll = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Delete all data", color = MaterialTheme.colorScheme.error)
            }
        }

        item {
            Text(
                "Sonder v2 · built for real days, not perfect ones.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
                modifier = Modifier.padding(vertical = 16.dp)
            )
        }
        item { Spacer(modifier = Modifier.height(24.dp)) }
    }

    if (showPinDialog) {
        PinDialog(
            title = "Create an app PIN",
            confirmLabel = "Enable lock",
            onDismiss = { showPinDialog = false },
            onConfirm = { pin ->
                showPinDialog = false
                onEnableLock(pin)
            }
        )
    }
    if (showUpdatePin) {
        PinDialog(
            title = "Change your PIN",
            confirmLabel = "Update",
            onDismiss = { showUpdatePin = false },
            onConfirm = { pin ->
                showUpdatePin = false
                onUpdatePin(pin)
            }
        )
    }
    if (showVaultDialog) {
        PinDialog(
            title = "Create a vault PIN",
            subtitle = "This guards the Rain Room and Unsent notes, even when the app is open.",
            confirmLabel = "Enable vault lock",
            onDismiss = { showVaultDialog = false },
            onConfirm = { pin ->
                showVaultDialog = false
                onSetVaultPin(pin)
            }
        )
    }
    if (showBackupPassword) {
        PasswordDialog(
            title = "Protect this backup",
            confirmLabel = "Export",
            onDismiss = { showBackupPassword = false; backupTarget = null },
            onConfirm = { password ->
                showBackupPassword = false
                val target = backupTarget
                backupTarget = null
                if (target != null) {
                    if (password.length >= 6) onExportBackup(target, password)
                    else onNotify("Use at least 6 characters.")
                }
            }
        )
    }
    if (showRestorePassword) {
        PasswordDialog(
            title = "Restore this backup",
            confirmLabel = "Restore",
            onDismiss = { showRestorePassword = false; pendingRestoreUri = null },
            onConfirm = { password ->
                showRestorePassword = false
                val target = pendingRestoreUri
                pendingRestoreUri = null
                if (target != null) onRestore(target, password)
            }
        )
    }
    if (showDeleteAll) {
        AlertDialog(
            onDismissRequest = { showDeleteAll = false },
            title = { Text("Delete everything?") },
            text = {
                Text(
                    "Every moment, voice note, chapter and envelope on this phone will be " +
                        "removed. This cannot be undone without a backup."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteAll = false
                    onDeleteAll()
                }) { Text("Delete all", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteAll = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun Section(title: String) {
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f))
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
        )
    }
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun PinDialog(
    title: String,
    subtitle: String? = null,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter { ch -> ch.isDigit() }.take(8) },
                    label = { Text("PIN (4–8 digits)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it.filter { ch -> ch.isDigit() }.take(8) },
                    label = { Text("Repeat PIN") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(pin) },
                enabled = pin.length in 4..8 && pin == confirm
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun PasswordDialog(
    title: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    "The password cannot be recovered. Keep it somewhere safe.",
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
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(password) },
                enabled = password.isNotBlank()
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
