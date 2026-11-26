package com.sonder.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sonder.app.data.local.entity.DayState
import com.sonder.app.ui.theme.auraColor

/**
 * The non-color geometric marker system. Color is never the only signifier:
 * Bright = filled sun spark, Warm = soft filled dot, Ordinary = hollow ring,
 * Heavy = quiet crescent.
 */
@Composable
fun DayStateMarker(
    state: DayState?,
    counted: Boolean = true,
    modifier: Modifier = Modifier,
    size: Dp = 34.dp
) {
    val base = state?.auraColor() ?: MaterialTheme.colorScheme.outline
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

        val ink = when (state) {
            DayState.ORDINARY -> Color(0xFF4A453F)
            DayState.HEAVY -> Color(0xFFE3E9F3)
            else -> Color.White.copy(alpha = 0.92f)
        }

        when (state) {
            DayState.BRIGHT -> {
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

            DayState.WARM -> {
                drawCircle(ink, radius * 0.34f, center)
            }

            DayState.ORDINARY -> {
                drawCircle(ink, radius * 0.42f, center, style = Stroke(width = radius * 0.13f))
            }

            DayState.HEAVY -> {
                val path = Path().apply {
                    val r = radius * 0.52f
                    addArc(
                        Rect(
                            center.x - r,
                            center.y - r,
                            center.x + r,
                            center.y + r
                        ),
                        35f,
                        245f
                    )
                }
                drawPath(path, ink, style = Stroke(width = radius * 0.14f))
            }

            null -> {
                drawCircle(ink.copy(alpha = 0.6f), radius * 0.30f, center, style = Stroke(width = radius * 0.10f))
            }
        }
    }
}

private fun Rect(left: Float, top: Float, right: Float, bottom: Float) =
    androidx.compose.ui.geometry.Rect(left, top, right, bottom)

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

@Composable
fun StateLegend(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DayState.entries.forEach { state ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                DayStateMarker(state = state, counted = true, size = 16.dp)
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = state.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
                )
            }
        }
    }
}
