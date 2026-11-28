package com.sonder.app.ui.theme

import android.app.Activity
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
import com.sonder.app.data.prefs.ThemeMode
import java.time.LocalTime

object SonderPalette {
    val Obsidian = Color(0xFF141820)
    val WarmCream = Color(0xFFFAF7F2)
    val AmberGold = Color(0xFFE5A645)
    val DeepAmberInk = Color(0xFF9C5D14)
    val SageHoney = Color(0xFF7AA488)
    val DeepSageInk = Color(0xFF3E634C)
    val LinenStone = Color(0xFFCEC5BA)
    val DuskIndigo = Color(0xFF1B2230)
    val DuskSurface = Color(0xFF283347)
    val DuskText = Color(0xFFE6ECF5)
    val RainText = Color(0xFFC4D0E2)
}

enum class TimeOfDayMood(
    val topColor: Color,
    val bottomColor: Color,
    val auraGlow: Color,
    val isDark: Boolean
) {
    DAWN(Color(0xFFFDF3E7), Color(0xFFF6E3CE), Color(0x44E5A645), false),
    DAY(Color(0xFFFAF7F2), Color(0xFFEFE9DF), Color(0x33E5A645), false),
    EVENING(Color(0xFFF7E7D8), Color(0xFFE9C9B6), Color(0x44C87D32), false),
    NIGHT(Color(0xFF141820), Color(0xFF1E2532), Color(0x38E5A645), true),
    RAIN_ROOM(Color(0xFF1B2230), Color(0xFF283347), Color(0x33566784), true);

    fun forDarkTheme(darkTheme: Boolean): TimeOfDayMood = when {
        this == RAIN_ROOM -> RAIN_ROOM
        darkTheme && !isDark -> NIGHT
        !darkTheme && isDark -> DAY
        else -> this
    }

    companion object {
        fun current(now: LocalTime = LocalTime.now()): TimeOfDayMood = when (now.hour) {
            in 5..9 -> DAWN
            in 10..16 -> DAY
            in 17..20 -> EVENING
            else -> NIGHT
        }
    }
}

fun weatherScoreColor(score: Float?, darkTheme: Boolean): Color {
    if (score == null) return if (darkTheme) SonderPalette.LinenStone else Color(0xFF8E8579)
    return when {
        score <= -0.25f -> if (darkTheme) Color(0xFF7C96C2) else Color(0xFF476088)
        score >= 0.25f -> if (darkTheme) SonderPalette.AmberGold else SonderPalette.DeepAmberInk
        else -> if (darkTheme) SonderPalette.SageHoney else SonderPalette.DeepSageInk
    }
}

fun readableAccent(darkTheme: Boolean): Color =
    if (darkTheme) SonderPalette.AmberGold else SonderPalette.DeepAmberInk

fun readableSecondaryAccent(darkTheme: Boolean): Color =
    if (darkTheme) SonderPalette.SageHoney else SonderPalette.DeepSageInk

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
            onSecondary = SonderPalette.Obsidian,
            background = SonderPalette.Obsidian,
            surface = Color(0xFF1F2633),
            surfaceVariant = Color(0xFF2A3344),
            onBackground = SonderPalette.WarmCream,
            onSurface = SonderPalette.WarmCream,
            onSurfaceVariant = Color(0xFFD5DCE8),
            outline = Color(0xFF7B869B)
        )
    } else {
        lightColorScheme(
            primary = SonderPalette.DeepAmberInk,
            onPrimary = Color.White,
            secondary = SonderPalette.DeepSageInk,
            onSecondary = Color.White,
            background = SonderPalette.WarmCream,
            surface = Color(0xFFFFFCF8),
            surfaceVariant = Color(0xFFF0EAE0),
            onBackground = SonderPalette.Obsidian,
            onSurface = SonderPalette.Obsidian,
            onSurfaceVariant = Color(0xFF4A4742),
            outline = Color(0xFF8C857B)
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SonderTypography,
        content = content
    )
}
