package com.arsys.netatmo.ui.screens.automations.advanced

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Accent = Color(0xFF0284C7)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TriggerEditorBottomSheet(
    onDismiss: () -> Unit,
    onAdd: (Map<String, Any>) -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

    var selectedType by remember { mutableStateOf("time") }

    // Time fields
    var time by remember { mutableStateOf("07:00") }
    val days = remember { mutableStateListOf(1, 2, 3, 4, 5, 6, 7) }

    // Numeric state fields
    var numericEntity by remember { mutableStateOf("outdoor_temp") }
    var numericCondition by remember { mutableStateOf("below") }
    var threshold by remember { mutableFloatStateOf(5f) }

    // Sun fields
    var sunEvent by remember { mutableStateOf("sunset") }
    var sunOffset by remember { mutableStateOf("0") }

    // Geofence fields
    var geofenceEvent by remember { mutableStateOf("enter") }
    var geofenceZone by remember { mutableStateOf("home") }

    val triggerTypes = listOf(
        "time" to "Hora",
        "numeric_state" to "Estado numérico",
        "sun" to "Sol",
        "geofence" to "Geovalla"
    )

    val dayLabels = listOf(
        1 to "L",
        2 to "M",
        3 to "X",
        4 to "J",
        5 to "V",
        6 to "S",
        7 to "D"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Añadir disparador",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(triggerTypes) { (type, label) ->
                    FilterChip(
                        selected = selectedType == type,
                        onClick = { selectedType = type },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Accent,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            when (selectedType) {
                "time" -> {
                    OutlinedTextField(
                        value = time,
                        onValueChange = { time = it },
                        label = { Text("Hora") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                    )

                    Text("Días de la semana", fontWeight = FontWeight.Medium)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        dayLabels.forEach { (dayNum, dayLabel) ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(dayLabel, fontSize = 12.sp)
                                Checkbox(
                                    checked = days.contains(dayNum),
                                    onCheckedChange = { checked ->
                                        if (checked) {
                                            if (!days.contains(dayNum)) days.add(dayNum)
                                        } else {
                                            days.remove(dayNum)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                "numeric_state" -> {
                    Text("Entidad", fontWeight = FontWeight.Medium)

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            "outdoor_temp" to "Temp. exterior",
                            "indoor_temp" to "Temp. interior"
                        ).forEach { (entity, label) ->
                            FilterChip(
                                selected = numericEntity == entity,
                                onClick = {
                                    numericEntity = entity
                                    threshold = 5f
                                },
                                label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Accent,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }

                    Text("Condición", fontWeight = FontWeight.Medium)

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("below" to "Por debajo", "above" to "Por encima").forEach { (condition, label) ->
                            FilterChip(
                                selected = numericCondition == condition,
                                onClick = { numericCondition = condition },
                                label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Accent,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }

                    val (rangeMin, rangeMax) = if (numericEntity == "outdoor_temp") -15f to 40f else 5f to 35f

                    Text("Umbral: ${threshold.toInt()}°C", fontWeight = FontWeight.Medium)

                    Slider(
                        value = threshold.coerceIn(rangeMin, rangeMax),
                        onValueChange = { threshold = it },
                        valueRange = rangeMin..rangeMax,
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(
                            thumbColor = Accent,
                            activeTrackColor = Accent
                        )
                    )
                }

                "sun" -> {
                    Text("Evento solar", fontWeight = FontWeight.Medium)

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("sunset" to "Puesta de sol", "sunrise" to "Salida del sol").forEach { (event, label) ->
                            FilterChip(
                                selected = sunEvent == event,
                                onClick = { sunEvent = event },
                                label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Accent,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }

                    OutlinedTextField(
                        value = sunOffset,
                        onValueChange = { sunOffset = it },
                        label = { Text("Desfase (minutos)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }

                "geofence" -> {
                    Text("Evento de geovalla", fontWeight = FontWeight.Medium)

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("enter" to "Entrar", "exit" to "Salir").forEach { (event, label) ->
                            FilterChip(
                                selected = geofenceEvent == event,
                                onClick = { geofenceEvent = event },
                                label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Accent,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }

                    OutlinedTextField(
                        value = geofenceZone,
                        onValueChange = { geofenceZone = it },
                        label = { Text("Nombre de zona") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    val map: Map<String, Any> = when (selectedType) {
                        "time" -> mapOf(
                            "platform" to "time",
                            "time" to time,
                            "days" to days.sorted()
                        )
                        "numeric_state" -> mapOf(
                            "platform" to "numeric_state",
                            "entity" to numericEntity,
                            numericCondition to threshold.toDouble()
                        )
                        "sun" -> mapOf(
                            "platform" to "sun",
                            "event" to sunEvent,
                            "offset" to (sunOffset.toIntOrNull() ?: 0)
                        )
                        "geofence" -> mapOf(
                            "platform" to "geofence",
                            "event" to geofenceEvent,
                            "zone" to geofenceZone
                        )
                        else -> emptyMap()
                    }
                    onAdd(map)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Accent)
            ) {
                Text("Añadir", color = Color.White)
            }

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancelar", color = Accent)
            }
        }
    }
}
