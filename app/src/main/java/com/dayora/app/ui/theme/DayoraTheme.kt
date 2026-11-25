package com.dayora.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import java.time.LocalTime

object DayoraPalette {
    val Obsidian = Color(0xFF161A20)
    val WarmCream = Color(0xFFFAF7F2)
    val AmberGold = Color(0xFFE5A645)
    val SageHoney = Color(0xFF7AA488)
    val LinenStone = Color(0xFFCEC5BA)
    val DuskIndigo = Color(0xFF1B2230)
    val DuskSurface = Color(0xFF283347)
    val DuskText = Color(0xFFDCE3EE)
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
    NIGHT(Color(0xFF151921), Color(0xFF212836), Color(0x44E5A645), true);

    companion object {
        fun current(now: LocalTime = LocalTime.now()): TimeOfDayMood = when (now.hour) {
            in 5..9 -> DAWN
            in 10..16 -> DAY
            in 17..20 -> EVENING
            else -> NIGHT
        }
    }
}

@Composable
fun DayoraTheme(
    darkTheme: Boolean = TimeOfDayMood.current().isDark,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = DayoraPalette.AmberGold,
            background = DayoraPalette.Obsidian,
            surface = Color(0xFF212836),
            onBackground = DayoraPalette.WarmCream,
            onSurface = DayoraPalette.WarmCream
        )
    } else {
        lightColorScheme(
            primary = Color(0xFFC87D32),
            background = DayoraPalette.WarmCream,
            surface = Color(0xFFFFFFFF),
            onBackground = DayoraPalette.Obsidian,
            onSurface = DayoraPalette.Obsidian
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
