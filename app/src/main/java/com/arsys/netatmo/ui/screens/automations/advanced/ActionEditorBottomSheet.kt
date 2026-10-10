package com.arsys.netatmo.ui.screens.automations.advanced

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.arsys.netatmo.ui.theme.OutlineVariant
import com.arsys.netatmo.ui.theme.SurfaceContainerLow

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ActionEditorBottomSheet(
    onDismiss: () -> Unit,
    onAdd: (Map<String, Any>) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedType by remember { mutableStateOf("set_temperature") }

    // set_temperature
    var temperature by remember { mutableFloatStateOf(20f) }
    var homeId by remember { mutableStateOf("") }
    var roomId by remember { mutableStateOf("") }

    // set_mode
    var selectedMode by remember { mutableStateOf("heating") }

    // notify
    var notifyTitle by remember { mutableStateOf("") }
    var notifyMessage by remember { mutableStateOf("") }

    // delay
    var delayMinutes by remember { mutableStateOf("5") }

    val actionTypes = listOf(
        "set_temperature" to "Temperatura",
        "set_mode" to "Modo",
        "notify" to "Notificación",
        "delay" to "Esperar"
    )

    val modes = listOf(
        "heating" to "Calefacción",
        "cooling" to "Frío",
        "away" to "Ausente",
        "hg" to "Anticongelación",
        "off" to "Apagado"
    )

    val secondary = MaterialTheme.colorScheme.secondary
    val secondaryContainer = MaterialTheme.colorScheme.secondaryContainer

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceContainerLow,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        properties = ModalBottomSheetDefaults.properties
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Añadir acción",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            SheetLabel("Tipo de acción")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                actionTypes.forEach { (type, label) ->
                    FilterChip(
                        selected = selectedType == type,
                        onClick = { selectedType = type },
                        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = secondaryContainer.copy(alpha = 0.20f),
                            selectedLabelColor = secondary,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selectedType == type,
                            selectedBorderColor = secondary.copy(alpha = 0.45f),
                            borderColor = OutlineVariant
                        )
                    )
                }
            }

            when (selectedType) {
                "set_temperature" -> {
                    SheetLabel("Temperatura: ${"%.1f".format(temperature)}°C")
                    Slider(
                        value = temperature,
                        onValueChange = { temperature = (Math.round(it * 2) / 2f) },
                        valueRange = 5f..30f,
                        steps = 49,
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(
                            thumbColor = secondary,
                            activeTrackColor = secondary,
                            inactiveTrackColor = OutlineVariant
                        )
                    )
                    SheetLabel("Home ID (opcional)")
                    SheetTextField(
                        value = homeId,
                        onValueChange = { homeId = it },
                        label = "Home ID"
                    )
                    SheetLabel("Room ID (opcional — vacío = global)")
                    SheetTextField(
                        value = roomId,
                        onValueChange = { roomId = it },
                        label = "Room ID"
                    )
                }

                "set_mode" -> {
                    SheetLabel("Modo de funcionamiento")
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        modes.forEach { (mode, label) ->
                            FilterChip(
                                selected = selectedMode == mode,
                                onClick = { selectedMode = mode },
                                label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = secondaryContainer.copy(alpha = 0.20f),
                                    selectedLabelColor = secondary,
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = selectedMode == mode,
                                    selectedBorderColor = secondary.copy(alpha = 0.45f),
                                    borderColor = OutlineVariant
                                )
                            )
                        }
                    }
                }

                "notify" -> {
                    SheetLabel("Título")
                    SheetTextField(
                        value = notifyTitle,
                        onValueChange = { notifyTitle = it },
                        label = "Título de la notificación"
                    )
                    SheetLabel("Mensaje")
                    SheetTextField(
                        value = notifyMessage,
                        onValueChange = { notifyMessage = it },
                        label = "Cuerpo del mensaje"
                    )
                }

                "delay" -> {
                    SheetLabel("Minutos de espera")
                    SheetTextField(
                        value = delayMinutes,
                        onValueChange = { delayMinutes = it },
                        label = "Minutos"
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            Button(
                onClick = {
                    val map: Map<String, Any> = when (selectedType) {
                        "set_temperature" -> mapOf(
                            "type" to "set_temperature",
                            "temperature" to temperature.toDouble(),
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
                    onAdd(map)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Text("Añadir acción", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            }

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
            ) {
                Text("Cancelar")
            }
        }
    }
}
