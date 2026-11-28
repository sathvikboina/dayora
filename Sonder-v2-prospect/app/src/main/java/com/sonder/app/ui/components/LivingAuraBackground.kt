package com.sonder.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.sonder.app.ui.theme.TimeOfDayMood
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun LivingAuraBackground(
    mood: TimeOfDayMood,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val topColor by animateColorAsState(
        targetValue = mood.topColor,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "aura_top"
    )
    val bottomColor by animateColorAsState(
        targetValue = mood.bottomColor,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "aura_bottom"
    )
    val glowColor by animateColorAsState(
        targetValue = mood.auraGlow,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "aura_glow"
    )

    val transition = rememberInfiniteTransition(label = "aura_breath")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12_000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "aura_phase"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(topColor, bottomColor)))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(
                x = size.width * (0.5f + 0.14f * cos(phase)),
                y = size.height * (0.30f + 0.08f * sin(phase))
            )
            val radius = size.maxDimension * (0.42f + 0.05f * sin(phase * 0.5f))
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(glowColor, Color.Transparent),
                    center = center,
                    radius = radius.coerceAtLeast(1f)
                ),
                radius = radius.coerceAtLeast(1f),
                center = center
            )
        }
        content()
    }
}
