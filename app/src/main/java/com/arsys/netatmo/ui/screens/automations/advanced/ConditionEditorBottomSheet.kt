package com.arsys.netatmo.ui.screens.automations.advanced

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConditionEditorBottomSheet(
    onDismiss: () -> Unit,
    onAdd: (Map<String, Any>) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedType by remember { mutableStateOf("time_range") }
    var fromTime by remember { mutableStateOf("06:00") }
    var toTime by remember { mutableStateOf("22:00") }
    var selectedEntity by remember { mutableStateOf("outdoor_temp") }
    var selectedCondition by remember { mutableStateOf("below") }
    var threshold by remember { mutableFloatStateOf(20f) }

    val conditionTypes = listOf(
        "time_range" to "Rango horario",
        "numeric_state" to "Temperatura",
        "presence" to "Presencia"
    )

    val tertiary = MaterialTheme.colorScheme.tertiary

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
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Añadir condición",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            SheetLabel("Tipo de condición")
            WrappingFilterChips(
                items = conditionTypes,
                selected = selectedType,
                accentColor = tertiary,
                onSelect = { selectedType = it }
            )

            when (selectedType) {
                "time_range" -> {
                    SheetLabel("Hora de inicio")
                    SheetTextField(
                        value = fromTime,
                        onValueChange = { fromTime = it },
                        label = "Desde (HH:MM)"
                    )
                    SheetLabel("Hora de fin")
                    SheetTextField(
                        value = toTime,
                        onValueChange = { toTime = it },
                        label = "Hasta (HH:MM)"
                    )
                }

                "numeric_state" -> {
                    SheetLabel("Entidad")
                    WrappingFilterChips(
                        items = listOf(
                            "outdoor_temp" to "Temp. exterior",
                            "indoor_temp" to "Temp. interior"
                        ),
                        selected = selectedEntity,
                        accentColor = tertiary,
                        onSelect = { selectedEntity = it }
                    )
                    SheetLabel("Condición")
                    WrappingFilterChips(
                        items = listOf("below" to "Por debajo de", "above" to "Por encima de"),
                        selected = selectedCondition,
                        accentColor = tertiary,
                        onSelect = { selectedCondition = it }
                    )
                    SheetLabel("Umbral: ${threshold.toInt()}°C")
                    Slider(
                        value = threshold,
                        onValueChange = { threshold = it },
                        valueRange = -20f..50f,
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(
                            thumbColor = tertiary,
                            activeTrackColor = tertiary,
                            inactiveTrackColor = OutlineVariant
                        )
                    )
                }

                "presence" -> {
                    Text(
                        text = "Se cumple si hay alguien en casa en el momento de la ejecución.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

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
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Text("Añadir condición", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
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
