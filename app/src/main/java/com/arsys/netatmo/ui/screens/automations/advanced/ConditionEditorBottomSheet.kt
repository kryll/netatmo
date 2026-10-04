package com.arsys.netatmo.ui.screens.automations.advanced

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Accent = Color(0xFF0284C7)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConditionEditorBottomSheet(
    onDismiss: () -> Unit,
    onAdd: (Map<String, Any>) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedType by remember { mutableStateOf("time_range") }

    // time_range state
    var fromTime by remember { mutableStateOf("06:00") }
    var toTime by remember { mutableStateOf("22:00") }

    // numeric_state state
    var selectedEntity by remember { mutableStateOf("outdoor_temp") }
    var selectedCondition by remember { mutableStateOf("below") }
    var threshold by remember { mutableFloatStateOf(20f) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Añadir condición",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )

            // Condition type selector
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(
                    "time_range" to "Rango horario",
                    "numeric_state" to "Estado numérico",
                    "presence" to "Presencia"
                ).forEach { (type, label) ->
                    FilterChip(
                        selected = selectedType == type,
                        onClick = { selectedType = type },
                        label = { Text(text = label, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Accent.copy(alpha = 0.15f),
                            selectedLabelColor = Accent
                        )
                    )
                }
            }

            when (selectedType) {
                "time_range" -> {
                    OutlinedTextField(
                        value = fromTime,
                        onValueChange = { fromTime = it },
                        label = { Text("Desde") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = toTime,
                        onValueChange = { toTime = it },
                        label = { Text("Hasta") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                "numeric_state" -> {
                    // Entity chips
                    Text(text = "Entidad", fontWeight = FontWeight.Medium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            "outdoor_temp" to "outdoor_temp",
                            "indoor_temp" to "indoor_temp"
                        ).forEach { (entity, label) ->
                            FilterChip(
                                selected = selectedEntity == entity,
                                onClick = { selectedEntity = entity },
                                label = { Text(text = label, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Accent.copy(alpha = 0.15f),
                                    selectedLabelColor = Accent
                                )
                            )
                        }
                    }

                    // Condition chips
                    Text(text = "Condición", fontWeight = FontWeight.Medium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            "below" to "below",
                            "above" to "above"
                        ).forEach { (condition, label) ->
                            FilterChip(
                                selected = selectedCondition == condition,
                                onClick = { selectedCondition = condition },
                                label = { Text(text = label, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Accent.copy(alpha = 0.15f),
                                    selectedLabelColor = Accent
                                )
                            )
                        }
                    }

                    // Slider for threshold
                    Text(
                        text = "Umbral: ${threshold.toInt()}°C",
                        fontWeight = FontWeight.Medium
                    )
                    Slider(
                        value = threshold,
                        onValueChange = { threshold = it },
                        valueRange = -20f..50f,
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(
                            thumbColor = Accent,
                            activeTrackColor = Accent
                        )
                    )
                }

                "presence" -> {
                    Text(
                        text = "Se cumple si alguien está en casa",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Buttons
            Button(
                onClick = {
                    val map: Map<String, Any> = when (selectedType) {
                        "time_range" -> mapOf(
                            "type" to "time_range",
                            "after" to fromTime,
                            "before" to toTime
                        )
                        "numeric_state" -> buildMap {
                            put("type", "numeric_state")
                            put("entity", selectedEntity)
                            put(selectedCondition, threshold.toDouble())
                        }
                        "presence" -> mapOf(
                            "type" to "presence",
                            "state" to "someone_home"
                        )
                        else -> emptyMap()
                    }
                    onAdd(map)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Accent)
            ) {
                Text(text = "Añadir", color = Color.White)
            }

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Cancelar", color = Accent)
            }
        }
    }
}
