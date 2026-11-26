package com.sonder.app.ui

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sonder.app.data.local.entity.DailyEntryEntity
import com.sonder.app.data.local.entity.DayState
import com.sonder.app.ui.calendar.CalendarScreen
import com.sonder.app.ui.capture.CaptureDraft
import com.sonder.app.ui.capture.CaptureSheet
import com.sonder.app.ui.home.HomeScreen
import com.sonder.app.ui.lock.LockScreen
import com.sonder.app.ui.rainroom.RainRoomScreen
import com.sonder.app.ui.settings.SettingsScreen
import com.sonder.app.ui.theme.SonderTheme
import com.sonder.app.ui.threads.ThreadsScreen
import com.sonder.app.util.DateDisplayFormatter
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

private enum class Route { HOME, CALENDAR, THREADS, SETTINGS, RAIN_ROOM }

private data class CaptureRequest(
    val date: LocalDate,
    val entry: DailyEntryEntity?,
    val sparks: Set<String>,
    val forceRainRoom: Boolean,
    val allowMidnightGrace: Boolean
)

@Composable
fun SonderRoot(viewModel: SonderViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle(initialValue = null)
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val rainRoomEntries by viewModel.rainRoomEntries.collectAsStateWithLifecycle()
    val goodDayCount by viewModel.goodDayCount.collectAsStateWithLifecycle()
    val firstEntryIso by viewModel.firstEntryIso.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.rescheduleReminderIfNeeded() }

    val loaded = settings
    if (loaded == null) {
        SonderTheme() {
            Box(modifier = Modifier.fillMaxSize())
        }
        return
    }

    val lockInitially = remember { loaded.appLockEnabled }
    var unlocked by remember { mutableStateOf(!lockInitially) }

    SonderTheme(mode = loaded.themeMode) {
        if (!unlocked) {
            LockScreen(
                onUnlock = { unlocked = true },
                onVerifyPin = { pin -> viewModel.verifyPin(pin) },
                onEraseAll = { viewModel.deleteAllData() }
            )
        } else {
            MainVault(
                viewModel = viewModel,
                settings = loaded,
                entries = entries,
                tags = tags,
                rainRoomEntries = rainRoomEntries,
                goodDayCount = goodDayCount,
                firstEntryIso = firstEntryIso
            )
        }
    }
}

@Composable
private fun MainVault(
    viewModel: SonderViewModel,
    settings: com.sonder.app.data.prefs.SonderSettings,
    entries: List<DailyEntryEntity>,
    tags: List<com.sonder.app.data.local.entity.EntryTagEntity>,
    rainRoomEntries: List<DailyEntryEntity>,
    goodDayCount: Int,
    firstEntryIso: String?
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val today = LocalDate.now()
    val todayIso = DateDisplayFormatter.toStorageIso(today)

    var route by remember { mutableStateOf(Route.HOME) }
    var captureRequest by remember { mutableStateOf<CaptureRequest?>(null) }
    var rescueMemory by remember { mutableStateOf<RescueMemory?>(null) }
    var whisper by remember { mutableStateOf<DailyEntryEntity?>(null) }

    val todayEntry = entries.firstOrNull { it.localDate == todayIso }

    LaunchedEffect(goodDayCount, entries.size) {
        if (goodDayCount >= 15 && whisper == null) {
            whisper = viewModel.findWhisper()
        }
    }

    fun notify(message: String) {
        scope.launch { snackbar.showSnackbar(message) }
    }

    fun openCapture(date: LocalDate, forceRainRoom: Boolean = false) {
        val entry = entries.firstOrNull { it.localDate == DateDisplayFormatter.toStorageIso(date) }
        val entrySparks = tags
            .filter { it.entryId == entry?.id && it.tagType == "SPARK" && it.userConfirmed }
            .map { it.tag }
            .toSet()
        val grace = entry == null &&
            DateDisplayFormatter.shouldOfferMidnightGrace(
                LocalTime.now(),
                entries.any { it.localDate == DateDisplayFormatter.toStorageIso(date.minusDays(1)) }
            )
        captureRequest = CaptureRequest(
            date = date,
            entry = entry,
            sparks = entrySparks,
            forceRainRoom = forceRainRoom,
            allowMidnightGrace = grace
        )
    }

    fun handleSave(draft: CaptureDraft) {
        val existing = captureRequest?.entry
        captureRequest = null
        scope.launch {
            viewModel.saveEntry(
                date = draft.date,
                state = draft.state,
                countAsGood = draft.countAsGood,
                reason = draft.reason,
                sparks = draft.sparks,
                existing = existing,
                suggestedState = draft.suggestedState
            )
            if (draft.state == DayState.HEAVY && !draft.countAsGood) {
                snackbar.showSnackbar("Set down in the Rain Room.")
                val probe = DailyEntryEntity(
                    localDate = DateDisplayFormatter.toStorageIso(draft.date),
                    timezoneId = DateDisplayFormatter.currentTimezoneId(),
                    dayState = DayState.HEAVY,
                    isGoodDay = false,
                    isRainRoom = true,
                    reasonText = draft.reason.ifBlank { null }
                )
                rescueMemory = viewModel.findRescue(probe)
            } else {
                snackbar.showSnackbar("Saved to your vault.")
            }
        }
    }

    BackHandler(enabled = route != Route.HOME) {
        route = when (route) {
            Route.RAIN_ROOM -> Route.CALENDAR
            else -> Route.HOME
        }
    }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (route == Route.HOME || route == Route.CALENDAR || route == Route.THREADS) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
                ) {
                    NavigationBarItem(
                        selected = route == Route.HOME,
                        onClick = { route = Route.HOME },
                        icon = { Icon(Icons.Outlined.Home, contentDescription = "Home") },
                        label = { Text("Home") }
                    )
                    NavigationBarItem(
                        selected = route == Route.CALENDAR,
                        onClick = { route = Route.CALENDAR },
                        icon = { Icon(Icons.Outlined.DateRange, contentDescription = "Calendar") },
                        label = { Text("Calendar") }
                    )
                    NavigationBarItem(
                        selected = route == Route.THREADS,
                        onClick = { route = Route.THREADS },
                        icon = { Icon(Icons.AutoMirrored.Outlined.List, contentDescription = "Threads") },
                        label = { Text("Threads") }
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (route) {
                Route.HOME -> HomeScreen(
                    goodDayCount = goodDayCount,
                    firstEntryIsoDate = firstEntryIso,
                    todayEntry = todayEntry,
                    todayIso = todayIso,
                    whisper = whisper,
                    onCapture = { openCapture(it) },
                    onOpenSettings = { route = Route.SETTINGS }
                )

                Route.CALENDAR -> CalendarScreen(
                    entries = entries,
                    onDayClick = { openCapture(it) },
                    onOpenSettings = { route = Route.SETTINGS },
                    onOpenRainRoom = { route = Route.RAIN_ROOM }
                )

                Route.THREADS -> ThreadsScreen(
                    entries = entries,
                    tags = tags,
                    onEntryClick = { entry ->
                        runCatching {
                            DateDisplayFormatter.fromStorageIso(entry.localDate)
                        }.getOrNull()?.let { openCapture(it) }
                    },
                    onOpenSettings = { route = Route.SETTINGS }
                )

                Route.RAIN_ROOM -> RainRoomScreen(
                    entries = rainRoomEntries,
                    onBack = { route = Route.CALENDAR },
                    onAdd = { openCapture(today, forceRainRoom = true) },
                    onEdit = { entry ->
                        runCatching {
                            DateDisplayFormatter.fromStorageIso(entry.localDate)
                        }.getOrNull()?.let { openCapture(it) }
                    },
                    onLetGo = { entry ->
                        scope.launch {
                            viewModel.letGo(entry)
                            snackbar.showSnackbar("Let go. Only the quiet date remains.")
                        }
                    },
                    onRescue = { entry ->
                        scope.launch {
                            rescueMemory = viewModel.findRescue(entry)
                            if (rescueMemory == null) {
                                snackbar.showSnackbar("No matching memory yet — that's okay.")
                            }
                        }
                    }
                )

                Route.SETTINGS -> SettingsScreen(
                    settings = settings,
                    entryCount = entries.size,
                    onBack = { route = Route.HOME },
                    onOpenBatterySettings = {
                        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { context.startActivity(intent) }
                            .onFailure { notify("Could not open battery settings.") }
                    },
                    onThemeMode = { viewModel.setThemeMode(it) },
                    onEnableLock = { viewModel.enableLock(it) },
                    onDisableLock = { viewModel.disableLock() },
                    onUpdatePin = { viewModel.updatePin(it) },
                    onSetReminder = { enabled, hour, minute ->
                        viewModel.setReminder(enabled, hour, minute)
                    },
                    onExportCsv = { uri -> viewModel.exportCsv(uri) },
                    onExportBackup = { uri, password -> viewModel.exportEncrypted(uri, password) },
                    onRestore = { uri, password -> viewModel.restoreEncrypted(uri, password) },
                    onDeleteAll = {
                        viewModel.deleteAllData()
                        whisper = null
                    },
                    onNotify = { notify(it) }
                )
            }
        }
    }

    captureRequest?.let { request ->
        CaptureSheet(
            date = request.date,
            existing = request.entry,
            existingSparks = request.sparks,
            allowMidnightGrace = request.allowMidnightGrace,
            forceRainRoom = request.forceRainRoom,
            onDismiss = { captureRequest = null },
            onSave = { handleSave(it) },
            onDelete = { entry ->
                captureRequest = null
                scope.launch {
                    val entryTags = viewModel.tagsForEntry(entry.id)
                    viewModel.deleteEntry(entry)
                    val result = snackbar.showSnackbar(
                        message = "Day deleted.",
                        actionLabel = "Undo"
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.restoreDeleted(entry, entryTags)
                    }
                }
            },
            onLetGo = { entry ->
                captureRequest = null
                scope.launch {
                    viewModel.letGo(entry)
                    snackbar.showSnackbar("Let go. Only the quiet date remains.")
                }
            }
        )
    }

    rescueMemory?.let { memory ->
        AlertDialog(
            onDismissRequest = { rescueMemory = null },
            title = { Text("A memory from before") },
            text = {
                Text(
                    "On ${DateDisplayFormatter.isoToDisplayString(memory.entry.localDate)} you wrote:\n\n" +
                        "\u201C${memory.entry.reasonText}\u201D"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { rescueMemory = null }
                ) { Text("Keep this close", fontWeight = FontWeight.Medium) }
            },
            dismissButton = {
                TextButton(onClick = { rescueMemory = null }) { Text("Not now") }
            }
        )
    }
}
