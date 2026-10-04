package com.arsys.netatmo.ui.screens.automations.advanced

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Accent = Color(0xFF0284C7)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ActionEditorBottomSheet(
    onDismiss: () -> Unit,
    onAdd: (Map<String, Any>) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedType by remember { mutableStateOf("set_temperature") }

    // set_temperature state
    var temperature by remember { mutableFloatStateOf(20f) }
    var homeId by remember { mutableStateOf("") }
    var roomId by remember { mutableStateOf("") }

    // set_mode state
    var selectedMode by remember { mutableStateOf("heating") }

    // notify state
    var notifyTitle by remember { mutableStateOf("") }
    var notifyMessage by remember { mutableStateOf("") }

    // delay state
    var delayMinutes by remember { mutableStateOf("5") }

    val actionTypes = listOf(
        "set_temperature" to "Ajustar temperatura",
        "set_mode" to "Cambiar modo",
        "notify" to "Notificar",
        "delay" to "Esperar"
    )

    val modes = listOf(
        "heating" to "Calefacción",
        "cooling" to "Frío",
        "away" to "Ausente",
        "off" to "Apagado"
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
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Nueva acción",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Text(
                text = "Tipo de acción",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                actionTypes.forEach { (type, label) ->
                    FilterChip(
                        selected = selectedType == type,
                        onClick = { selectedType = type },
                        label = { Text(text = label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Accent.copy(alpha = 0.15f),
                            selectedLabelColor = Accent
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            when (selectedType) {
                "set_temperature" -> {
                    Text(
                        text = "Temperatura: ${"%.1f".format(temperature)}°C",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Slider(
                        value = temperature,
                        onValueChange = { raw ->
                            temperature = (Math.round(raw * 2) / 2f)
                        },
                        valueRange = 5f..30f,
                        steps = 49,
                        colors = SliderDefaults.colors(
                            thumbColor = Accent,
                            activeTrackColor = Accent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = homeId,
                        onValueChange = { homeId = it },
                        label = { Text("Home ID") },
                        placeholder = { Text("Dejar vacío para home principal") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = roomId,
                        onValueChange = { roomId = it },
                        label = { Text("Room ID") },
                        placeholder = { Text("Dejar vacío para modo global") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                "set_mode" -> {
                    Text(
                        text = "Modo",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        modes.forEach { (mode, label) ->
                            FilterChip(
                                selected = selectedMode == mode,
                                onClick = { selectedMode = mode },
                                label = { Text(text = label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Accent.copy(alpha = 0.15f),
                                    selectedLabelColor = Accent
                                )
                            )
                        }
                    }
                }

                "notify" -> {
                    OutlinedTextField(
                        value = notifyTitle,
                        onValueChange = { notifyTitle = it },
                        label = { Text("Título") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = notifyMessage,
                        onValueChange = { notifyMessage = it },
                        label = { Text("Mensaje") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )
                }

                "delay" -> {
                    OutlinedTextField(
                        value = delayMinutes,
                        onValueChange = { delayMinutes = it },
                        label = { Text("Minutos") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = "Cancelar", color = Accent)
                }

                Button(
                    onClick = {
                        val actionMap: Map<String, Any> = when (selectedType) {
                            "set_temperature" -> mapOf(
                                "type" to "set_temperature",
                                "temperature" to temperature,
                                "homeId" to homeId,
                                "roomId" to roomId
                            )
                            "set_mode" -> mapOf(
                                "type" to "set_mode",
                                "mode" to selectedMode
                            )
                            "notify" -> mapOf(
                                "type" to "notify",
                                "title" to notifyTitle,
                                "message" to notifyMessage
                            )
                            "delay" -> mapOf(
                                "type" to "delay",
                                "minutes" to (delayMinutes.toIntOrNull() ?: 5)
                            )
                            else -> emptyMap()
                        }
                        onAdd(actionMap)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = "Añadir")
                }
            }
        }
    }
}
