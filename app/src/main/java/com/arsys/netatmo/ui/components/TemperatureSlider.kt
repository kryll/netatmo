package com.arsys.netatmo.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.arsys.netatmo.ui.theme.OutlineVariant
import com.arsys.netatmo.ui.theme.SurfaceContainerHigh

@Composable
fun TemperatureSlider(
    value: Double,
    onValueChange: (Double) -> Unit,
    minTemp: Float = 7f,
    maxTemp: Float = 30f,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val thumbColor = Color(0xFFF1F5F9)
    val thumbBorderColor = Color(0xFF0A0E13)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Central temperature display: value in displayMedium Bold + unit in titleLarge
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = "%.1f".format(value),
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = primary
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "°C",
                style = MaterialTheme.typography.titleLarge,
                color = onSurfaceVariant,
                modifier = Modifier.padding(bottom = 10.dp)
            )
        }

        // Slider with custom thumb (24dp, #F1F5F9, dark border) and 6dp track
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Slider(
                value = value.toFloat(),
                onValueChange = { onValueChange(Math.round(it * 2).toDouble() / 2) },
                valueRange = minTemp..maxTemp,
                steps = ((maxTemp - minTemp) * 2).toInt() - 1,
                modifier = Modifier.fillMaxWidth(),
                thumb = { _ ->
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(thumbColor)
                            .border(1.5.dp, thumbBorderColor, CircleShape)
                    )
                },
                track = { sliderState ->
                    SliderDefaults.Track(
                        sliderState = sliderState,
                        modifier = Modifier.height(6.dp),
                        colors = SliderDefaults.colors(
                            activeTrackColor = primary,
                            inactiveTrackColor = OutlineVariant,
                            activeTickColor = Color.Transparent,
                            inactiveTickColor = Color.Transparent,
                            thumbColor = thumbColor
                        )
                    )
                }
            )

            // Min / max labels below the slider
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${minTemp.toInt()}°C",
                    style = MaterialTheme.typography.bodySmall,
                    color = onSurfaceVariant
                )
                Text(
                    text = "${maxTemp.toInt()}°C",
                    style = MaterialTheme.typography.bodySmall,
                    color = onSurfaceVariant
                )
            }
        }

        // Quick preset chips: pill-shaped, surfaceContainerHigh background,
        // selected state uses primaryContainer fill + primary border
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(16.0, 18.0, 20.0, 22.0, 24.0).forEach { preset ->
                val isSelected = value == preset
                Surface(
                    onClick = { onValueChange(preset) },
                    shape = RoundedCornerShape(50),
                    color = if (isSelected) primaryContainer else SurfaceContainerHigh,
                    border = if (isSelected) {
                        BorderStroke(1.dp, primary)
                    } else {
                        BorderStroke(1.dp, OutlineVariant)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                    ) {
                        Text(
                            text = "${preset.toInt()}°",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isSelected) onPrimaryContainer else onSurfaceVariant,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}
