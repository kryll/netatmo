package com.arsys.netatmo.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TemperatureSlider(
    value: Double,
    onValueChange: (Double) -> Unit,
    minTemp: Float = 7f,
    maxTemp: Float = 30f,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${minTemp.toInt()}°C",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "%.1f°C".format(value),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "${maxTemp.toInt()}°C",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(Math.round(it * 2).toDouble() / 2) },
            valueRange = minTemp..maxTemp,
            steps = ((maxTemp - minTemp) * 2).toInt() - 1,
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(16.0, 18.0, 20.0, 22.0, 24.0).forEach { preset ->
                FilterChip(
                    selected = value == preset,
                    onClick = { onValueChange(preset) },
                    label = { Text("${preset.toInt()}°") },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
