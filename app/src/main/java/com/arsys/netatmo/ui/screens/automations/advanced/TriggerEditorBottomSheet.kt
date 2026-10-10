package com.arsys.netatmo.ui.screens.automations.advanced

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.arsys.netatmo.ui.theme.OutlineVariant
import com.arsys.netatmo.ui.theme.SurfaceContainerLow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TriggerEditorBottomSheet(
    onDismiss: () -> Unit,
    onAdd: (Map<String, Any>) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedType by remember { mutableStateOf("time") }

    // Time
    var time by remember { mutableStateOf("07:00") }
    val days = remember { mutableStateListOf(1, 2, 3, 4, 5) }

    // Numeric state
    var numericEntity by remember { mutableStateOf("outdoor_temp") }
    var numericCondition by remember { mutableStateOf("below") }
    var threshold by remember { mutableFloatStateOf(5f) }

    // Sun
    var sunEvent by remember { mutableStateOf("sunset") }
    var sunOffset by remember { mutableStateOf("0") }

    // Geofence
    var geofenceEvent by remember { mutableStateOf("enter") }
    var geofenceZone by remember { mutableStateOf("home") }

    val triggerTypes = listOf(
        "time" to "Hora",
        "numeric_state" to "Temperatura",
        "sun" to "Sol",
        "geofence" to "Geovalla"
    )

    val dayLabels = listOf(1 to "L", 2 to "M", 3 to "X", 4 to "J", 5 to "V", 6 to "S", 7 to "D")

    val primary = MaterialTheme.colorScheme.primary

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
            // Title
            Text(
                text = "Añadir disparador",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Type selector
            SheetLabel("Tipo de disparador")
            WrappingFilterChips(
                items = triggerTypes,
                selected = selectedType,
                accentColor = primary,
                onSelect = { selectedType = it }
            )

            // Type-specific fields
            when (selectedType) {
                "time" -> {
                    SheetLabel("Hora (HH:MM)")
                    SheetTextField(
                        value = time,
                        onValueChange = { time = it },
                        label = "Hora"
                    )
                    SheetLabel("Días de la semana")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        dayLabels.forEach { (dayNum, dayLabel) ->
                            FilterChip(
                                selected = days.contains(dayNum),
                                onClick = {
                                    if (days.contains(dayNum)) days.remove(dayNum)
                                    else days.add(dayNum)
                                },
                                label = { Text(dayLabel, style = MaterialTheme.typography.labelMedium) },
                                modifier = Modifier.weight(1f),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = primary.copy(alpha = 0.15f),
                                    selectedLabelColor = primary,
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = days.contains(dayNum),
                                    selectedBorderColor = primary.copy(alpha = 0.5f),
                                    borderColor = OutlineVariant
                                )
                            )
                        }
                    }
                }

                "numeric_state" -> {
                    SheetLabel("Entidad")
                    WrappingFilterChips(
                        items = listOf("outdoor_temp" to "Temp. exterior", "indoor_temp" to "Temp. interior"),
                        selected = numericEntity,
                        accentColor = primary,
                        onSelect = { numericEntity = it; threshold = if (it == "outdoor_temp") 5f else 20f }
                    )
                    SheetLabel("Condición")
                    WrappingFilterChips(
                        items = listOf("below" to "Por debajo de", "above" to "Por encima de"),
                        selected = numericCondition,
                        accentColor = primary,
                        onSelect = { numericCondition = it }
                    )
                    val (rangeMin, rangeMax) = if (numericEntity == "outdoor_temp") -15f to 40f else 5f to 35f
                    SheetLabel("Umbral: ${threshold.toInt()}°C")
                    Slider(
                        value = threshold.coerceIn(rangeMin, rangeMax),
                        onValueChange = { threshold = it },
                        valueRange = rangeMin..rangeMax,
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(
                            thumbColor = primary,
                            activeTrackColor = primary,
                            inactiveTrackColor = OutlineVariant
                        )
                    )
                }

                "sun" -> {
                    SheetLabel("Evento solar")
                    WrappingFilterChips(
                        items = listOf("sunset" to "Puesta de sol", "sunrise" to "Salida del sol"),
                        selected = sunEvent,
                        accentColor = primary,
                        onSelect = { sunEvent = it }
                    )
                    SheetLabel("Desfase en minutos (negativo = antes)")
                    SheetTextField(
                        value = sunOffset,
                        onValueChange = { sunOffset = it },
                        label = "Desfase (min)"
                    )
                }

                "geofence" -> {
                    SheetLabel("Evento de geovalla")
                    WrappingFilterChips(
                        items = listOf("enter" to "Al llegar", "exit" to "Al salir"),
                        selected = geofenceEvent,
                        accentColor = primary,
                        onSelect = { geofenceEvent = it }
                    )
                    SheetLabel("Zona")
                    SheetTextField(
                        value = geofenceZone,
                        onValueChange = { geofenceZone = it },
                        label = "Nombre de zona"
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            // Add button
            Button(
                onClick = {
                    val map: Map<String, Any> = when (selectedType) {
                        "time" -> mapOf(
                            "type" to "time",
                            "platform" to "time",
                            "time" to time,
                            "days" to days.sorted()
                        )
                        "numeric_state" -> buildMap {
                            put("type", "numeric_state")
                            put("platform", "numeric_state")
                            put("entity", numericEntity)
                            put(numericCondition, threshold.toDouble())
                        }
                        "sun" -> mapOf(
                            "type" to "sun",
                            "platform" to "sun",
                            "event" to sunEvent,
                            "offset" to (sunOffset.toIntOrNull() ?: 0)
                        )
                        "geofence" -> mapOf(
                            "type" to "geofence",
                            "platform" to "geofence",
                            "event" to geofenceEvent,
                            "zone" to geofenceZone
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
                Text("Añadir disparador", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
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

// ── Shared sheet helpers ──────────────────────────────────────────────────────

@Composable
internal fun SheetLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Medium
    )
}

@Composable
internal fun SheetTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = OutlineVariant,
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            cursorColor = MaterialTheme.colorScheme.primary,
            unfocusedContainerColor = SurfaceContainerLow,
            focusedContainerColor = SurfaceContainerLow
        )
    )
}

@Composable
internal fun WrappingFilterChips(
    items: List<Pair<String, String>>,
    selected: String,
    accentColor: androidx.compose.ui.graphics.Color,
    onSelect: (String) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items.forEach { (value, label) ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = accentColor.copy(alpha = 0.15f),
                    selectedLabelColor = accentColor,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selected == value,
                    selectedBorderColor = accentColor.copy(alpha = 0.5f),
                    borderColor = OutlineVariant
                )
            )
        }
    }
}
