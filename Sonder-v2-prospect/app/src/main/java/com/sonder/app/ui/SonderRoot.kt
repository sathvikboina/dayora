package com.sonder.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.sonder.app.data.prefs.ThemeMode
import com.sonder.app.ui.calendar.CalendarScreen
import com.sonder.app.ui.capture.CaptureSheet
import com.sonder.app.ui.components.LivingAuraBackground
import com.sonder.app.ui.home.HomeScreen
import com.sonder.app.ui.lock.LockScreen
import com.sonder.app.ui.rainroom.RainRoomScreen
import com.sonder.app.ui.settings.SettingsScreen
import com.sonder.app.ui.theme.SonderTheme
import com.sonder.app.ui.theme.TimeOfDayMood
import java.time.LocalDate

@Composable
fun SonderRoot(viewModel: SonderViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                viewModel.lockNow()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    SonderTheme(mode = state.settings.themeMode) {
        val darkTheme = when (state.settings.themeMode) {
            ThemeMode.AUTO -> TimeOfDayMood.current().isDark
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
        val baseMood = TimeOfDayMood.current().forDarkTheme(darkTheme)

        if (!state.isUnlocked) {
            LockScreen(
                mood = baseMood,
                onUnlockPin = viewModel::unlockWithPin,
                onUnlockBiometric = viewModel::unlockWithBiometric
            )
            return@SonderTheme
        }

        val navController = rememberNavController()
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = backStackEntry?.destination?.route ?: Screen.HOME.route
        val isRainRoute = currentRoute == Screen.RAIN_ROOM.route
        val activeMood = if (isRainRoute) TimeOfDayMood.RAIN_ROOM else baseMood

        val snackbarHostState = remember { SnackbarHostState() }
        var threadFilterNorm by remember { mutableStateOf<String?>(null) }

        LaunchedEffect(state.statusMessage) {
            val msg = state.statusMessage ?: return@LaunchedEffect
            snackbarHostState.showSnackbar(msg)
            viewModel.consumeStatusMessage()
        }

        val navItems = listOf(
            Triple(Screen.HOME, Icons.Outlined.Home, Screen.HOME.label),
            Triple(Screen.CALENDAR, Icons.Outlined.CalendarMonth, Screen.CALENDAR.label),
            Triple(Screen.THREADS, Icons.Outlined.AutoAwesome, Screen.THREADS.label),
            Triple(Screen.RAIN_ROOM, Icons.Outlined.WaterDrop, Screen.RAIN_ROOM.label),
            Triple(Screen.SETTINGS, Icons.Outlined.Settings, Screen.SETTINGS.label)
        )

        LivingAuraBackground(mood = activeMood) {
            Scaffold(
                containerColor = Color.Transparent,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                bottomBar = {
                    NavigationBar(
                        containerColor = if (isRainRoute) {
                            Color(0xFF1A2130).copy(alpha = 0.94f)
                        } else {
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
                        },
                        tonalElevation = 0.dp
                    ) {
                        navItems.forEach { (screen, icon: ImageVector, label) ->
                            val selected = currentRoute == screen.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    if (screen != Screen.THREADS) threadFilterNorm = null
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(imageVector = icon, contentDescription = label) },
                                label = { Text(label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = if (isRainRoute) Color(0xFF9BB4D8) else MaterialTheme.colorScheme.primary,
                                    selectedTextColor = if (isRainRoute) Color(0xFFE6ECF5) else MaterialTheme.colorScheme.onSurface,
                                    unselectedIconColor = if (isRainRoute) Color(0xFF9FB0C9) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = if (isRainRoute) Color(0xFF9FB0C9) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    indicatorColor = if (isRainRoute) Color(0xFF283347) else MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                )
                            )
                        }
                    }
                }
            ) { innerPadding ->
                NavHost(
                    navController = navController,
                    startDestination = Screen.HOME.route,
                    modifier = Modifier.padding(innerPadding)
                ) {
                    composable(Screen.HOME.route) {
                        HomeScreen(
                            goodDayCount = state.goodDayCount,
                            totalMomentCount = state.totalMomentCount,
                            firstMomentDate = state.firstMomentDate,
                            todayMoments = state.todayMoments,
                            yesterdayHasEntry = state.yesterdayHasEntry,
                            allTags = state.allTags,
                            unlockedEnvelopes = state.unlockedEnvelopes,
                            showWelcomeBack = state.showWelcomeBack,
                            darkTheme = darkTheme,
                            onDismissWelcomeBack = viewModel::dismissWelcomeBack,
                            onOpenEnvelope = viewModel::openEnvelope,
                            onCaptureToday = { viewModel.openCapture(LocalDate.now()) },
                            onCaptureYesterday = { viewModel.openCapture(LocalDate.now().minusDays(1)) },
                            onEditMoment = { m -> viewModel.openCapture(LocalDate.parse(m.localDate), existing = m) },
                            onConfirmTag = viewModel::confirmTag,
                            onDismissTag = viewModel::dismissTag,
                            onOpenThread = { norm ->
                                threadFilterNorm = norm
                                navController.navigate(Screen.THREADS.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                }
                            }
                        )
                    }

                    composable(Screen.CALENDAR.route) {
                        CalendarScreen(
                            selectedDate = state.selectedDate,
                            allMoments = state.allMoments,
                            selectedDayMoments = state.selectedDayMoments,
                            chapters = state.chapters,
                            darkTheme = darkTheme,
                            onSelectDate = viewModel::selectDate,
                            onCaptureForDate = { d, existing -> viewModel.openCapture(d, existing = existing) },
                            onCreateChapter = viewModel::createChapter,
                            onCloseChapter = viewModel::closeChapter
                        )
                    }

                    composable(Screen.THREADS.route) {
                        ThreadsScreen(
                            threads = state.threads,
                            initialFilterNormalized = threadFilterNorm,
                            semanticMatches = state.semanticMatches,
                            darkTheme = darkTheme,
                            onSearchSemantic = viewModel::searchSemantic,
                            onRenameTagGlobally = viewModel::renameTagGlobally
                        )
                    }

                    composable(Screen.RAIN_ROOM.route) {
                        RainRoomScreen(
                            rainMoments = state.rainRoomMoments,
                            unsentMoments = state.unsentMoments,
                            onStepIntoRainRoom = {
                                viewModel.openCapture(LocalDate.now(), forceRainRoom = true)
                            },
                            onEditMoment = { m ->
                                viewModel.openCapture(LocalDate.parse(m.localDate), existing = m)
                            }
                        )
                    }

                    composable(Screen.SETTINGS.route) {
                        SettingsScreen(
                            settings = state.settings,
                            darkTheme = darkTheme,
                            onThemeModeChange = viewModel::setThemeMode,
                            onSetupPin = viewModel::setupPin,
                            onAppLockToggle = viewModel::setAppLockEnabled,
                            onReminderToggle = viewModel::setReminder,
                            onExportCsv = viewModel::exportCsv,
                            onExportEncrypted = viewModel::exportEncrypted,
                            onRestoreEncrypted = viewModel::restoreEncrypted,
                            onDeleteAll = viewModel::deleteAllData
                        )
                    }
                }
            }
        }

        val capDate = state.captureDate
        if (capDate != null) {
            val existing = state.captureExistingMoment
            val existingSparks = remember(existing, state.allTags) {
                if (existing == null) emptySet()
                else state.allTags.filter { it.momentId == existing.id && it.userConfirmed }
                    .map { it.tagName }.toSet()
            }
            CaptureSheet(
                date = capDate,
                existing = existing,
                existingSparks = existingSparks,
                forceRainRoom = state.captureForceRainRoom,
                showSwipeHint = !state.settings.swipeHintSeen,
                onSwipeHintConsumed = viewModel::markSwipeHintSeen,
                onDismiss = viewModel::closeCapture,
                onSave = viewModel::saveMoment,
                onDelete = viewModel::deleteMoment
            )
        }
    }
}
