package com.sonder.app.ui.rainroom

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sonder.app.data.local.entity.MomentEntity
import com.sonder.app.ui.components.EmptyWhisper
import com.sonder.app.ui.theme.SonderPalette
import com.sonder.app.util.DateDisplayFormatter
import java.time.LocalDate

@Composable
fun RainRoomScreen(
    rainMoments: List<MomentEntity>,
    unsentMoments: List<MomentEntity>,
    onStepIntoRainRoom: () -> Unit,
    onEditMoment: (MomentEntity) -> Unit
) {
    var showUnsent by remember { mutableStateOf(false) }
    val listToShow = if (showUnsent) unsentMoments else rainMoments

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val streakColor = Color(0xFF9BB4D8).copy(alpha = 0.11f)
            var x = -size.height
            var i = 0
            while (x < size.width + size.height) {
                val y0 = (i * 89 % 260).toFloat()
                drawLine(
                    color = streakColor,
                    start = Offset(x, y0),
                    end = Offset(x + 28f, y0 + 70f),
                    strokeWidth = 1.6f
                )
                x += 72f
                i++
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.WaterDrop,
                            contentDescription = null,
                            tint = Color(0xFF9BB4D8),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Rain Room",
                            style = MaterialTheme.typography.headlineMedium,
                            color = SonderPalette.DuskText
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Heavy, sad, or quiet moments live here safely. They never count against your good days.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SonderPalette.RainText
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = onStepIntoRainRoom,
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF3A4B68),
                                contentColor = SonderPalette.DuskText
                            )
                        ) {
                            Text("Leave a quiet note")
                        }
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFF283347),
                            modifier = Modifier.clickable { showUnsent = !showUnsent }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Lock,
                                    contentDescription = null,
                                    tint = SonderPalette.RainText,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (showUnsent) "Rain notes" else "Unsent (\${unsentMoments.size})",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = SonderPalette.DuskText
                                )
                            }
                        }
                    }
                }
            }

            if (listToShow.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = Color(0xFF242E40).copy(alpha = 0.85f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        EmptyWhisper(
                            title = if (showUnsent) "No unsent notes" else "No rain notes yet",
                            subtitle = "Whenever a day feels heavy, you have a quiet place to set it down."
                        )
                    }
                }
            } else {
                items(listToShow, key = { it.id }) { m ->
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFF242E40).copy(alpha = 0.88f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onEditMoment(m) }
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = DateDisplayFormatter.isoToDisplayString(m.localDate),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF9BB4D8)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = m.bodyText?.takeIf { it.isNotBlank() }
                                    ?: if (m.audioFileName != null) "Quiet voice note" else "Heavy moment",
                                style = MaterialTheme.typography.bodyLarge,
                                color = SonderPalette.DuskText
                            )
                        }
                    }
                }
            }
        }
    }
}
