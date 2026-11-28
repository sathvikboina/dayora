package com.sonder.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import android.app.Activity
import com.sonder.app.data.local.entity.DayState
import com.sonder.app.data.prefs.ThemeMode
import java.time.LocalTime

object SonderPalette {
    val Obsidian = Color(0xFF161A20)
    val WarmCream = Color(0xFFFAF7F2)
    val AmberGold = Color(0xFFE5A645)
    val SageHoney = Color(0xFF7AA488)
    val LinenStone = Color(0xFFCEC5BA)
    val DuskIndigo = Color(0xFF1B2230)
    val DuskSurface = Color(0xFF283347)
    val DuskText = Color(0xFFDCE3EE)
    val RainText = Color(0xFFB9C6DA)
}

enum class TimeOfDayMood(
    val topColor: Color,
    val bottomColor: Color,
    val auraGlow: Color,
    val isDark: Boolean
) {
    DAWN(Color(0xFFFDF3E7), Color(0xFFF8E0C8), Color(0x55E5A645), false),
    DAY(Color(0xFFFAF7F2), Color(0xFFEFE9DF), Color(0x44E5A645), false),
    EVENING(Color(0xFFF6E4D5), Color(0xFFE4C1B2), Color(0x55C87D32), false),
    NIGHT(Color(0xFF151921), Color(0xFF212836), Color(0x44E5A645), true),

    RAIN_ROOM(Color(0xFF1B2230), Color(0xFF283347), Color(0x33566784), true);

    companion object {
        fun current(now: LocalTime = LocalTime.now()): TimeOfDayMood = when (now.hour) {
            in 5..9 -> DAWN
            in 10..16 -> DAY
            in 17..20 -> EVENING
            else -> NIGHT
        }
    }
}

fun DayState.auraColor(): Color = when (this) {
    DayState.BRIGHT -> Color(0xFFE5A645)
    DayState.WARM -> Color(0xFF7AA488)
    DayState.ORDINARY -> Color(0xFFCEC5BA)
    DayState.HEAVY -> Color(0xFF566784)
}

/** Keeps the room's atmosphere honest when the user forces light or dark. */
fun TimeOfDayMood.forDarkTheme(dark: Boolean): TimeOfDayMood = when {
    dark && !isDark -> TimeOfDayMood.NIGHT
    !dark && isDark -> TimeOfDayMood.DAY
    else -> this
}

private val SonderTypography = Typography(
    displayLarge = TextStyle(fontSize = 57.sp, lineHeight = 64.sp, fontWeight = FontWeight.Light),
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Normal),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Normal),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
)

@Composable
fun SonderTheme(
    mode: ThemeMode = ThemeMode.AUTO,
    content: @Composable () -> Unit
) {
    val darkTheme = when (mode) {
        ThemeMode.AUTO -> TimeOfDayMood.current().isDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = SonderPalette.AmberGold,
            onPrimary = SonderPalette.Obsidian,
            secondary = SonderPalette.SageHoney,
            background = SonderPalette.Obsidian,
            surface = Color(0xFF212836),
            surfaceVariant = Color(0xFF2B3342),
            onBackground = SonderPalette.WarmCream,
            onSurface = SonderPalette.WarmCream,
            onSurfaceVariant = Color(0xFFC8CFDA),
            outline = Color(0xFF6C7688)
        )
    } else {
        lightColorScheme(
            primary = Color(0xFFB4702A),
            onPrimary = Color.White,
            secondary = Color(0xFF5E7D69),
            background = SonderPalette.WarmCream,
            surface = Color(0xFFFFFCF8),
            surfaceVariant = Color(0xFFF0EAE1),
            onBackground = SonderPalette.Obsidian,
            onSurface = SonderPalette.Obsidian,
            onSurfaceVariant = Color(0xFF5B5A57),
            outline = Color(0xFF9B958C)
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SonderTypography,
        content = content
    )
}
