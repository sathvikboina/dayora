package com.sonder.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.provider.Settings
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sonder.app.data.local.entity.MomentEntity
import com.sonder.app.data.local.entity.OpenWhenEnvelopeEntity
import com.sonder.app.ui.calendar.CalendarScreen
import com.sonder.app.ui.capture.CaptureSheet
import com.sonder.app.ui.components.LivingAuraBackground
import com.sonder.app.ui.home.HomeScreen
import com.sonder.app.ui.lock.LockScreen
import com.sonder.app.ui.rainroom.RainRoomScreen
import com.sonder.app.ui.settings.SettingsScreen
import com.sonder.app.ui.theme.SonderTheme
import com.sonder.app.ui.theme.TimeOfDayMood
import com.sonder.app.ui.theme.forDarkTheme
import com.sonder.app.ui.threads.ThreadsScreen
import com.sonder.app.util.DateDisplayFormatter
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun SonderRoot(viewModel: SonderViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    LaunchedEffect(Unit) { viewModel.rescheduleReminderIfNeeded() }

    var unlocked by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(state.settings.appLockEnabled, state.settings.pinHash) {
        if (unlocked == null) {
            unlocked = !state.settings.appLockEnabled || state.settings.pinHash == null
        }
    }

    SonderTheme(mode = state.settings.themeMode) {
        val darkTheme = when (state.settings.themeMode) {
            com.sonder.app.data.prefs.ThemeMode.AUTO -> TimeOfDayMood.current().isDark
            com.sonder.app.data.prefs.ThemeMode.LIGHT -> false
            com.sonder.app.data.prefs.ThemeMode.DARK -> true
        }
        val baseMood = TimeOfDayMood.current().forDarkTheme(darkTheme)

        val isUnlocked = unlocked
        if (isUnlocked == null) {
            Box(modifier = Modifier.fillMaxSize())
            return@SonderTheme
        }

        if (!isUnlocked) {
            LockScreen(
                onUnlock = { unlocked = true },
                onVerifyPin = { pin -> viewModel.verifyPin(pin) },
                onEraseAll = { viewModel.deleteAllData() }
            )
            return@SonderTheme
        }

        MainVault(
            viewModel = viewModel,
            state = state,
            baseMood = baseMood,
            darkTheme = darkTheme,
            activity = activity
        )
    }
}

@Composable
private fun MainVault(
    viewModel: SonderViewModel,
    state: SonderUiState,
    baseMood: TimeOfDayMood,
    darkTheme: Boolean,
    activity: Activity?
) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var route by remember { mutableStateOf(Screen.HOME) }
    var captureRequest by remember { mutableStateOf<CaptureRequest?>(null) }
    var selectedEnvelope by remember { mutableStateOf<OpenWhenEnvelopeEntity?>(null) }

    val isSanctuary = route == Screen.RAIN_ROOM
    DisposableEffect(isSanctuary, activity) {
        val window = activity?.window
        if (isSanctuary) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        onDispose {
            if (isSanctuary) window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    LaunchedEffect(state.statusMessage) {
        val message = state.statusMessage ?: return@LaunchedEffect
        snackbar.showSnackbar(message)
        viewModel.consumeStatusMessage()
    }

    LaunchedEffect(route) {
        if (route != Screen.RAIN_ROOM) viewModel.lockVault()
        if (route != Screen.THREADS) { /* filter is local to the screen */ }
    }

    val navItems = listOf(
        Triple(Screen.HOME, Icons.Outlined.Home, "Home"),
        Triple(Screen.CALENDAR, Icons.Outlined.CalendarMonth, "Calendar"),
        Triple(Screen.THREADS, Icons.Outlined.AutoAwesome, "Threads"),
        Triple(Screen.RAIN_ROOM, Icons.Outlined.WaterDrop, "Rain Room"),
        Triple(Screen.SETTINGS, Icons.Outlined.Settings, "Settings")
    )

    LivingAuraBackground(mood = if (isSanctuary) TimeOfDayMood.RAIN_ROOM else baseMood) {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                NavigationBar(
                    containerColor = if (isSanctuary) Color(0xE61A2130)
                    else MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                    tonalElevation = 0.dp
                ) {
                    navItems.forEach { (screen, icon: ImageVector, label) ->
                        NavigationBarItem(
                            selected = route == screen,
                            onClick = { route = screen },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = if (isSanctuary) Color(0xFF9BB4D8)
                                else MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                indicatorColor = if (isSanctuary) Color(0xFF283347)
                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                            )
                        )
                    }
                }
            }
        ) { padding ->
            val modifier = Modifier.fillMaxSize().padding(padding)
            Box(modifier = modifier) {
                when (route) {
                    Screen.HOME -> HomeScreen(
                        goodDayCount = state.goodDayCount,
                        totalMomentCount = state.totalMomentCount,
                        firstMomentDate = state.firstMomentDate,
                        todayMoments = state.todayMoments,
                        yesterdayHasMoment = state.yesterdayHasMoment,
                        allTags = state.allTags,
                        photosByMoment = state.photosByMoment,
                        unlockedEnvelopes = state.unlockedEnvelopes,
                        chapters = state.chapters,
                        insights = state.insights,
                        showWelcomeBack = state.showWelcomeBack,
                        weather = viewModel.dayWeather(state.todayMoments),
                        reReadEnabled = state.totalMomentCount >= 50,
                        onDismissWelcomeBack = viewModel::dismissWelcomeBack,
                        onOpenEnvelope = { selectedEnvelope = it },
                        onCapture = { openCapture(viewModel, captureRequest, LocalDate.now()) { captureRequest = it } },
                        onCaptureYesterday = {
                            openCapture(viewModel, captureRequest, LocalDate.now().minusDays(1)) {
                                captureRequest = it
                            }
                        },
                        onEditMoment = { moment ->
                            openCapture(
                                viewModel,
                                captureRequest,
                                runCatching { LocalDate.parse(moment.localDate) }.getOrDefault(LocalDate.now()),
                                existing = moment
                            ) { captureRequest = it }
                        },
                        onConfirmTag = viewModel::confirmTag,
                        onDismissTag = viewModel::dismissTag,
                        onOpenRainRoom = { route = Screen.RAIN_ROOM },
                        onRollReRead = viewModel::rollReRead,
                        onOpenSettings = { route = Screen.SETTINGS },
                        onChapterKeepGoing = viewModel::keepChapterGoing,
                        onChapterEnded = { viewModel.closeChapter(it) }
                    )

                    Screen.CALENDAR -> CalendarScreen(
                        selectedDate = state.selectedDate,
                        allMoments = state.allMoments,
                        selectedDayMoments = state.selectedDayMoments,
                        photosByMoment = state.photosByMoment,
                        chapters = state.chapters,
                        onSelectDate = viewModel::selectDate,
                        onCaptureForDate = { date, existing ->
                            openCapture(viewModel, captureRequest, date, existing) { captureRequest = it }
                        },
                        onEditMoment = { moment ->
                            openCapture(
                                viewModel,
                                captureRequest,
                                runCatching { LocalDate.parse(moment.localDate) }.getOrDefault(LocalDate.now()),
                                existing = moment
                            ) { captureRequest = it }
                        },
                        onCreateChapter = viewModel::createChapter,
                        onCloseChapter = { viewModel.closeChapter(it) },
                        onDeleteChapter = viewModel::deleteChapter,
                        onSetChapterCover = { chapter, uri -> viewModel.setChapterCover(chapter, uri) },
                        onShareChapterRecap = { chapter ->
                            viewModel.chapterRecapText(chapter) { text, card ->
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = if (card != null) "image/png" else "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, text)
                                    if (card != null) {
                                        runCatching {
                                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                                context,
                                                "${context.packageName}.fileprovider",
                                                card
                                            )
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                    }
                                }
                                runCatching {
                                    context.startActivity(
                                        Intent.createChooser(send, "Share chapter recap")
                                    )
                                }
                            }
                        },
                        onOpenSettings = { route = Screen.SETTINGS }
                    )

                    Screen.THREADS -> ThreadsScreen(
                        threads = state.threads,
                        allMoments = state.allMoments,
                        initialFilterNormalized = null,
                        semanticMatches = state.semanticMatches,
                        onSearchSemantic = viewModel::searchSemantic,
                        onRenameTagGlobally = viewModel::renameTagGlobally,
                        onMergeTag = viewModel::mergeTag,
                        onOpenMoment = { moment ->
                            openCapture(
                                viewModel,
                                captureRequest,
                                runCatching { LocalDate.parse(moment.localDate) }.getOrDefault(LocalDate.now()),
                                existing = moment
                            ) { captureRequest = it }
                        },
                        onOpenSettings = { route = Screen.SETTINGS }
                    )

                    Screen.RAIN_ROOM -> RainRoomScreen(
                        rainMoments = state.rainRoomMoments,
                        unsentMoments = state.unsentMoments,
                        vaultPinConfigured = state.vaultPinConfigured,
                        vaultUnlocked = state.vaultUnlocked,
                        onUnlockVault = viewModel::unlockVault,
                        onLockVault = viewModel::lockVault,
                        onAddRain = {
                            openCapture(
                                viewModel,
                                captureRequest,
                                LocalDate.now(),
                                forceRainRoom = true
                            ) { captureRequest = it }
                        },
                        onAddUnsent = {
                            openCapture(
                                viewModel,
                                captureRequest,
                                LocalDate.now(),
                                forceUnsent = true
                            ) { captureRequest = it }
                        },
                        onEditMoment = { moment ->
                            openCapture(
                                viewModel,
                                captureRequest,
                                runCatching { LocalDate.parse(moment.localDate) }.getOrDefault(LocalDate.now()),
                                existing = moment
                            ) { captureRequest = it }
                        },
                        onLetGo = viewModel::letGo,
                        onDelete = viewModel::deleteMoment
                    )

                    Screen.SETTINGS -> SettingsScreen(
                        settings = state.settings,
                        momentCount = state.totalMomentCount,
                        onBack = { route = Screen.HOME },
                        onOpenBatterySettings = {
                            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            runCatching { context.startActivity(intent) }
                        },
                        onThemeMode = viewModel::setThemeMode,
                        onEnableLock = viewModel::setupPin,
                        onDisableLock = { viewModel.setAppLockEnabled(false) },
                        onUpdatePin = viewModel::updatePin,
                        onSetVaultPin = viewModel::setVaultPin,
                        onDisableVaultLock = viewModel::disableVaultLock,
                        onSetReminder = viewModel::setReminder,
                        onExportCsv = viewModel::exportCsv,
                        onExportBackup = viewModel::exportEncrypted,
                        onRestore = viewModel::restoreEncrypted,
                        onDeleteAll = viewModel::deleteAllData,
                        onNotify = { message ->
                            scope.launch { snackbar.showSnackbar(message) }
                        }
                    )
                }
            }
        }
    }

    captureRequest?.let { request ->
        val existingTags = remember(request.existing, state.allTags) {
            if (request.existing == null) emptyList()
            else state.allTags.filter { it.momentId == request.existing.id }
        }
        val existingSparks = remember(existingTags) {
            existingTags.filter { it.userConfirmed }.map { it.tagName }.toSet()
        }
        val existingPhotos = remember(request.existing, state.photosByMoment) {
            request.existing?.let { state.photosByMoment[it.id].orEmpty() }.orEmpty()
        }
        CaptureSheet(
            date = request.date,
            existing = request.existing,
            existingSparks = existingSparks,
            existingTags = existingTags,
            existingPhotos = existingPhotos,
            forceRainRoom = request.forceRainRoom,
            forceUnsent = request.forceUnsent,
            showSwipeHint = !state.settings.swipeHintSeen,
            onSwipeHintConsumed = viewModel::markSwipeHintSeen,
            onDismiss = {
                captureRequest = null
                viewModel.closeCapture()
            },
            onSave = { draft ->
                captureRequest = null
                viewModel.saveMoment(draft)
            },
            onDelete = viewModel::deleteMoment,
            onLetGo = viewModel::letGo
        )
    }

    selectedEnvelope?.let { envelope ->
        AlertDialog(
            onDismissRequest = { selectedEnvelope = null },
            title = { Text(envelope.titleHint ?: "An envelope from before") },
            text = {
                Text(
                    envelope.bodyText?.takeIf { it.isNotBlank() }
                        ?: if (envelope.audioFileName != null) "A voice note is waiting inside." else "A quiet mark."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.openEnvelope(envelope)
                    selectedEnvelope = null
                }) { Text("Keep this close") }
            },
            dismissButton = {
                TextButton(onClick = { selectedEnvelope = null }) { Text("Later") }
            }
        )
    }

    state.reReadMoment?.let { moment ->
        AlertDialog(
            onDismissRequest = viewModel::dismissReRead,
            title = { Text("A memory from before") },
            text = {
                Text(
                    "On ${DateDisplayFormatter.isoToDisplayString(moment.localDate)} you wrote:\n\n" +
                        "\u201C${moment.bodyText.orEmpty()}\u201D"
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissReRead) { Text("Keep this close") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissReRead) { Text("Not now") }
            }
        )
    }
}

private data class CaptureRequest(
    val date: LocalDate,
    val existing: MomentEntity?,
    val forceRainRoom: Boolean,
    val forceUnsent: Boolean
)

private fun openCapture(
    viewModel: SonderViewModel,
    current: CaptureRequest?,
    date: LocalDate,
    existing: MomentEntity? = null,
    forceRainRoom: Boolean = false,
    forceUnsent: Boolean = false,
    update: (CaptureRequest?) -> Unit
) {
    viewModel.openCapture(date, existing = existing, forceRainRoom = forceRainRoom)
    update(
        CaptureRequest(
            date = date,
            existing = existing,
            forceRainRoom = forceRainRoom,
            forceUnsent = forceUnsent
        )
    )
}

private fun Context.findActivity(): Activity? {
    var context: Context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}
