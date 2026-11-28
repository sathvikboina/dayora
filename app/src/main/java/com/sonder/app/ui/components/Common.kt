package com.sonder.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sonder.app.data.local.entity.EmergentDayWeather

/**
 * Non-colour geometric marker for a day's emergent weather. Colour is never the
 * only signifier: Golden Sun = filled sun spark, Part-Sun = half sun / half
 * crescent, Dusk Rain = quiet crescent, Quiet Mist = hollow ring.
 */
@Composable
fun DayWeatherMarker(
    weather: EmergentDayWeather?,
    counted: Boolean = true,
    modifier: Modifier = Modifier,
    size: Dp = 34.dp
) {
    val base = weather?.auraColor() ?: MaterialTheme.colorScheme.outline
    Canvas(modifier = modifier.size(size)) {
        val radius = this.size.minDimension / 2f
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        drawCircle(
            color = base.copy(alpha = if (counted) 1f else 0.28f),
            radius = radius
        )
        if (!counted) {
            drawCircle(
                color = base.copy(alpha = 0.75f),
                radius = radius,
                style = Stroke(width = radius * 0.10f)
            )
        }

        val ink = when (weather) {
            EmergentDayWeather.QUIET_MIST -> Color(0xFF4A453F)
            EmergentDayWeather.DUSK_RAIN -> Color(0xFFE3E9F3)
            else -> Color.White.copy(alpha = 0.92f)
        }

        when (weather) {
            EmergentDayWeather.GOLDEN_SUN -> {
                val r = radius * 0.52f
                val w = radius * 0.13f
                drawLine(ink, center + Offset(0f, -r), center + Offset(0f, r), w)
                drawLine(ink, center + Offset(-r, 0f), center + Offset(r, 0f), w)
                drawLine(
                    ink,
                    center + Offset(-r * 0.55f, -r * 0.55f),
                    center + Offset(r * 0.55f, r * 0.55f),
                    w * 0.7f
                )
                drawLine(
                    ink,
                    center + Offset(-r * 0.55f, r * 0.55f),
                    center + Offset(r * 0.55f, -r * 0.55f),
                    w * 0.7f
                )
            }

            EmergentDayWeather.PART_SUN_PART_RAIN -> {
                drawCircle(ink, radius * 0.28f, center + Offset(-radius * 0.18f, 0f))
                val path = Path().apply {
                    val r = radius * 0.30f
                    addArc(
                        Rect(
                            center.x + radius * 0.18f - r,
                            center.y - r,
                            center.x + radius * 0.18f + r,
                            center.y + r
                        ),
                        35f,
                        245f
                    )
                }
                drawPath(path, ink, style = Stroke(width = radius * 0.12f))
            }

            EmergentDayWeather.DUSK_RAIN -> {
                val path = Path().apply {
                    val r = radius * 0.52f
                    addArc(
                        Rect(center.x - r, center.y - r, center.x + r, center.y + r),
                        35f,
                        245f
                    )
                }
                drawPath(path, ink, style = Stroke(width = radius * 0.14f))
            }

            EmergentDayWeather.QUIET_MIST -> {
                drawCircle(
                    ink,
                    radius * 0.42f,
                    center,
                    style = Stroke(width = radius * 0.13f)
                )
            }

            null -> {
                drawCircle(
                    ink.copy(alpha = 0.6f),
                    radius * 0.30f,
                    center,
                    style = Stroke(width = radius * 0.10f)
                )
            }
        }
    }
}

fun EmergentDayWeather.auraColor(): Color = when (this) {
    EmergentDayWeather.GOLDEN_SUN -> Color(0xFFE5A645)
    EmergentDayWeather.PART_SUN_PART_RAIN -> Color(0xFFB98A5E)
    EmergentDayWeather.DUSK_RAIN -> Color(0xFF566784)
    EmergentDayWeather.QUIET_MIST -> Color(0xFFCEC5BA)
}

@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    onSettings: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.size(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
        }
        if (trailing != null) {
            trailing()
            Spacer(modifier = Modifier.width(4.dp))
        }
        if (onSettings != null) {
            IconButton(onClick = onSettings) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
            }
        }
    }
}
