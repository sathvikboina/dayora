package com.sonder.app.ui.rainroom

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sonder.app.data.local.entity.DailyEntryEntity
import com.sonder.app.ui.components.DayStateMarker
import com.sonder.app.ui.theme.SonderPalette
import com.sonder.app.util.DateDisplayFormatter

@Composable
fun RainRoomScreen(
    entries: List<DailyEntryEntity>,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (DailyEntryEntity) -> Unit,
    onLetGo: (DailyEntryEntity) -> Unit,
    onRescue: (DailyEntryEntity) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(SonderPalette.DuskIndigo, SonderPalette.DuskSurface)
                )
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val lineColor = Color(0xFF8FA6C8).copy(alpha = 0.08f)
            var x = -size.height
            var index = 0
            while (x < size.width + size.height) {
                val startY = (index * 97 % 240).toFloat()
                drawLine(
                    color = lineColor,
                    start = Offset(x, startY),
                    end = Offset(x + 40f, startY + 90f),
                    strokeWidth = 2f
                )
                x += 74f
                index++
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back",
                        tint = SonderPalette.RainText
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                DayStateMarker(state = null, counted = true, size = 18.dp)
                Spacer(modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.padding(horizontal = 24.dp))
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "The Rain Room",
                style = MaterialTheme.typography.headlineMedium,
                color = SonderPalette.DuskText
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "A private place to set heavy days down.",
                style = MaterialTheme.typography.bodyMedium,
                color = SonderPalette.RainText.copy(alpha = 0.75f)
            )

            Spacer(modifier = Modifier.height(22.dp))
            Button(
                onClick = onAdd,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                Text("Put down a heavy day", modifier = Modifier.padding(vertical = 4.dp))
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (entries.isEmpty()) {
                Text(
                    text = "Nothing here right now.\nHeavy days can rest here whenever they need to.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = SonderPalette.RainText.copy(alpha = 0.6f),
                    modifier = Modifier.padding(vertical = 28.dp)
                )
            } else {
                entries.forEach { entry ->
                    RainCard(
                        entry = entry,
                        onEdit = { onEdit(entry) },
                        onLetGo = { onLetGo(entry) },
                        onRescue = { onRescue(entry) }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
private fun RainCard(
    entry: DailyEntryEntity,
    onEdit: () -> Unit,
    onLetGo: () -> Unit,
    onRescue: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2E3A4F).copy(alpha = 0.82f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DayStateMarker(state = entry.dayState, counted = entry.isGoodDay, size = 18.dp)
                Spacer(modifier = Modifier.padding(horizontal = 5.dp))
                Text(
                    text = DateDisplayFormatter.isoToDisplayString(entry.localDate),
                    style = MaterialTheme.typography.labelLarge,
                    color = SonderPalette.RainText
                )
            }
            if (!entry.reasonText.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = entry.reasonText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = SonderPalette.DuskText
                )
            } else {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Let go — only the quiet date remains.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SonderPalette.RainText.copy(alpha = 0.6f)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                TextButton(onClick = onEdit) { Text("Edit") }
                if (!entry.reasonText.isNullOrBlank()) {
                    TextButton(onClick = onRescue) {
                        Icon(
                            Icons.Outlined.LightMode,
                            contentDescription = null,
                            tint = SonderPalette.AmberGold
                        )
                        Spacer(modifier = Modifier.padding(horizontal = 3.dp))
                        Text("A memory from before")
                    }
                    TextButton(onClick = onLetGo) {
                        Text("Let it go", color = SonderPalette.RainText.copy(alpha = 0.7f))
                    }
                }
            }
        }
    }
}
