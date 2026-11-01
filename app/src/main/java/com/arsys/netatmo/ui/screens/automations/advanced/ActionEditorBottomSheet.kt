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

/** A Netatmo heating schedule ("programa") offered by the Programa action picker. */
data class ScheduleOption(val homeId: String, val id: String, val name: String)

/** Another saved automation offered by the Automatización action picker (never the one being edited). */
data class AutomationOption(val id: Long, val name: String)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ActionEditorBottomSheet(
    onDismiss: () -> Unit,
    onAdd: (Map<String, Any>) -> Unit,
    initial: Map<String, Any>? = null,
    scenarios: List<com.arsys.netatmo.data.local.entities.ScenarioEntity> = emptyList(),
    schedules: List<ScheduleOption> = emptyList(),
    automations: List<AutomationOption> = emptyList()
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // When editing an existing action, start from its stored values.
    val prefill = remember { initial?.let { prefillAction(it) } }

    var selectedType by remember { mutableStateOf(prefill?.type?.ifBlank { "set_temperature" } ?: "set_temperature") }

    // set_temperature
    var temperature by remember { mutableFloatStateOf(prefill?.temperature ?: 20f) }
    var homeId by remember { mutableStateOf(prefill?.homeId ?: "") }
    var roomId by remember { mutableStateOf(prefill?.roomId ?: "") }

    // set_mode
    var selectedMode by remember { mutableStateOf(prefill?.mode ?: "schedule") }

    // notify
    var notifyTitle by remember { mutableStateOf(prefill?.notifyTitle ?: "") }
    var notifyMessage by remember { mutableStateOf(prefill?.notifyMessage ?: "") }

    // delay
    var delayMinutes by remember { mutableStateOf(prefill?.delayMinutes ?: "5") }

    // apply_scenario
    var scenarioId by remember { mutableStateOf(prefill?.scenarioId ?: -1L) }

    // set_schedule
    var scheduleId by remember { mutableStateOf(prefill?.scheduleId ?: "") }

    // run_automation
    var targetAutomationId by remember { mutableStateOf(prefill?.automationId ?: -1L) }

    // set_home_mode / set_mode
    var homeMode by remember { mutableStateOf(prefill?.mode ?: "schedule") }
    var modeHours by remember { mutableStateOf(prefill?.durationText ?: "") }
    var modeHoursError by remember { mutableStateOf(false) }

    val actionTypes = listOf(
        "set_temperature" to "Temperatura",
        "set_mode" to "Modo",
        "set_home_mode" to "Modo de la casa",
        "set_schedule" to "Programa",
        "apply_scenario" to "Escenario",
        "run_automation" to "Automatización",
        "notify" to "Notificación",
        "delay" to "Esperar"
    )

    // Netatmo home modes (setthermmode); "heating"/"cooling" are not valid values there.
    val modes = listOf(
        "schedule" to "Programado",
        "away" to "Ausente",
        "hg" to "Anticongelación",
        "off" to "Apagado"
    )

    // A stored action whose type this editor does not know is left untouched:
    // better read-only than silently replacing it with an empty map.
    val unknownType = initial != null && actionTypes.none { it.first == prefill?.type }

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
                text = if (initial == null) "Añadir acción" else "Editar acción",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (unknownType) {
                Text(
                    "Esta acción («${prefill?.type}») no se puede editar aquí. " +
                        "Elimínala y créala de nuevo si necesitas cambiarla.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            } else {

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
                    SheetLabel("Duración en horas desde que salte (vacío = hasta cambiarlo; admite decimales: 3,5)")
                    SheetTextField(
                        value = modeHours,
                        onValueChange = { modeHours = it; modeHoursError = false },
                        label = "Horas"
                    )
                    if (modeHoursError) {
                        Text(
                            "Introduce un número de horas válido (por ejemplo 3 o 3,5) o deja el campo vacío.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    LegacyEndAtNote(prefill)
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
                    Text(
                        text = "Variables en título y mensaje: %automatizacion%, %tem_ext%, " +
                            "%tem_int%, %estancia%, %consigna%, %modo%, %temperaturas%.\n" +
                            "Ejemplo: Exterior %tem_ext% °C. Interior %tem_int% °C.\n" +
                            "Interior, consigna y modo: estancia de la alerta o la más fría " +
                            "si solo hay una casa. Para una estancia concreta: %tem_int:ID%, " +
                            "%consigna:ID%, %modo:ID%, %estancia:ID% (ID de Netatmo).\n" +
                            "Sin dato: no disponible. Variables desconocidas: se conservan.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                "set_home_mode" -> {
                    SheetLabel("Modo de la casa")
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf(
                            "schedule" to "Programado",
                            "away" to "Ausente",
                            "hg" to "Anticongelación",
                            "off" to "Apagado"
                        ).forEach { (mode, label) ->
                            FilterChip(
                                selected = homeMode == mode,
                                onClick = { homeMode = mode },
                                label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = secondaryContainer.copy(alpha = 0.20f),
                                    selectedLabelColor = secondary,
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = homeMode == mode,
                                    selectedBorderColor = secondary.copy(alpha = 0.45f),
                                    borderColor = OutlineVariant
                                )
                            )
                        }
                    }
                    SheetLabel("Duración en horas desde que salte (vacío = hasta cambiarlo; admite decimales: 3,5)")
                    SheetTextField(
                        value = modeHours,
                        onValueChange = { modeHours = it; modeHoursError = false },
                        label = "Horas"
                    )
                    if (modeHoursError) {
                        Text(
                            "Introduce un número de horas válido (por ejemplo 3 o 3,5) o deja el campo vacío.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    LegacyEndAtNote(prefill)
                }

                "delay" -> {
                    SheetLabel("Minutos de espera")
                    SheetTextField(
                        value = delayMinutes,
                        onValueChange = { delayMinutes = it },
                        label = "Minutos"
                    )
                }

                "apply_scenario" -> {
                    SheetLabel("Escenario")
                    if (scenarios.isEmpty()) {
                        Text(
                            "No hay escenarios guardados. Créalos desde la pantalla de escenarios.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            scenarios.forEach { scenario ->
                                FilterChip(
                                    selected = scenarioId == scenario.id,
                                    onClick = { scenarioId = scenario.id },
                                    label = { Text(scenario.name, style = MaterialTheme.typography.labelMedium) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = secondaryContainer.copy(alpha = 0.20f),
                                        selectedLabelColor = secondary,
                                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = scenarioId == scenario.id,
                                        selectedBorderColor = secondary.copy(alpha = 0.45f),
                                        borderColor = OutlineVariant
                                    )
                                )
                            }
                        }
                    }
                }

                "set_schedule" -> {
                    SheetLabel("Programa de calefacción")
                    if (schedules.isEmpty()) {
                        Text(
                            "No se encontraron programas en la instalación (o no se pudieron cargar). " +
                                "Cierra y vuelve a abrir esta ventana para reintentar.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            schedules.forEach { schedule ->
                                FilterChip(
                                    selected = scheduleId == schedule.id,
                                    onClick = { scheduleId = schedule.id },
                                    label = { Text(schedule.name, style = MaterialTheme.typography.labelMedium) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = secondaryContainer.copy(alpha = 0.20f),
                                        selectedLabelColor = secondary,
                                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = scheduleId == schedule.id,
                                        selectedBorderColor = secondary.copy(alpha = 0.45f),
                                        borderColor = OutlineVariant
                                    )
                                )
                            }
                        }
                        Text(
                            "Al ejecutarse se activa el programa elegido y la casa vuelve al modo programado.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                "run_automation" -> {
                    SheetLabel("Automatización a ejecutar")
                    if (automations.isEmpty()) {
                        Text(
                            "No hay otras automatizaciones guardadas. Crea primero la que quieras invocar.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            automations.forEach { automation ->
                                FilterChip(
                                    selected = targetAutomationId == automation.id,
                                    onClick = { targetAutomationId = automation.id },
                                    label = { Text(automation.name, style = MaterialTheme.typography.labelMedium) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = secondaryContainer.copy(alpha = 0.20f),
                                        selectedLabelColor = secondary,
                                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = targetAutomationId == automation.id,
                                        selectedBorderColor = secondary.copy(alpha = 0.45f),
                                        borderColor = OutlineVariant
                                    )
                                )
                            }
                        }
                        Text(
                            "Ejecuta las acciones de la automatización elegida, solo si está activa " +
                                "y sus condiciones se cumplen en ese momento. Sus disparadores no se " +
                                "evalúan: quien manda es esta automatización. Las referencias en bucle " +
                                "se detectan y se omiten (todo queda registrado en el diagnóstico).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            }

            if (!unknownType) {
            Spacer(Modifier.height(4.dp))

            Button(
                enabled = when (selectedType) {
                    "apply_scenario" -> scenarios.any { it.id == scenarioId }
                    "set_schedule" -> schedules.any { it.id == scheduleId }
                    "run_automation" -> automations.any { it.id == targetAutomationId }
                    else -> true
                },
                onClick = {
                    if (selectedType == "set_home_mode" || selectedType == "set_mode") {
                        // Never save an invalid duration silently as "until changed":
                        // reject visibly and keep the sheet open.
                        val hoursText = modeHours.trim()
                        val parsed = hoursText.replace(',', '.').toDoubleOrNull()
                        val invalid = hoursText.isNotEmpty() && (parsed == null || parsed <= 0.0)
                        modeHoursError = invalid
                        if (invalid) return@Button
                    }
                    val map: Map<String, Any> = when (selectedType) {
                        "set_temperature" -> mapOf(
                            "type" to "set_temperature",
                            "temperature" to temperature.toDouble(),
                            "homeId" to homeId,
                            "roomId" to roomId
                        )
                        "set_mode" -> buildMap {
                            put("type", "set_mode")
                            put("mode", selectedMode)
                            // Same semantics as set_home_mode: the engine computes the
                            // end from the duration when the trigger fires.
                            val hours = modeHours.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 }
                            if (hours != null) {
                                put("durationHours", hours)
                            } else {
                                // No duration entered: keep the fixed end it was saved with.
                                prefill?.legacyEndAt?.takeIf { it > 0L }?.let { put("endAt", it) }
                            }
                        }
                        "notify" -> mapOf(
                            "type" to "notify",
                            "title" to notifyTitle,
                            "message" to notifyMessage
                        )
                        "set_home_mode" -> buildMap {
                            put("type", "set_home_mode")
                            put("mode", homeMode)
                            // Store the duration, not a fixed end: the engine computes the
                            // end when the trigger fires, not when the automation is saved.
                            val hours = modeHours.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 }
                            if (hours != null) {
                                put("durationHours", hours)
                            } else {
                                // No duration entered: keep the fixed end it was saved with.
                                prefill?.legacyEndAt?.takeIf { it > 0L }?.let { put("endAt", it) }
                            }
                        }
                        "delay" -> mapOf(
                            "type" to "delay",
                            "minutes" to (delayMinutes.toIntOrNull() ?: 5)
                        )
                        "apply_scenario" -> mapOf(
                            "type" to "apply_scenario",
                            "scenarioId" to scenarioId,
                            "scenarioName" to (scenarios.firstOrNull { it.id == scenarioId }?.name ?: "")
                        )
                        "set_schedule" -> mapOf(
                            "type" to "set_schedule",
                            "scheduleId" to scheduleId,
                            "scheduleName" to (schedules.firstOrNull { it.id == scheduleId }?.name ?: ""),
                            "homeId" to (schedules.firstOrNull { it.id == scheduleId }?.homeId ?: "")
                        )
                        "run_automation" -> mapOf(
                            "type" to "run_automation",
                            "automationId" to targetAutomationId,
                            "automationName" to (automations.firstOrNull { it.id == targetAutomationId }?.name ?: "")
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
                Text(
                    if (initial == null) "Añadir acción" else "Guardar cambios",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
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

/** Note shown while editing an action saved with a fixed end date instead of a duration. */
@Composable
private fun LegacyEndAtNote(prefill: ActionPrefill?) {
    val endAt = prefill?.legacyEndAt ?: return
    if (endAt <= 0L) return
    val c = java.util.Calendar.getInstance().apply { timeInMillis = endAt }
    val whenText = "%02d/%02d/%04d %02d:%02d".format(
        c.get(java.util.Calendar.DAY_OF_MONTH), c.get(java.util.Calendar.MONTH) + 1,
        c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE)
    )
    Text(
        "Guardada con fin fijo: el $whenText. Si escribes una duración, se usará en su lugar.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
