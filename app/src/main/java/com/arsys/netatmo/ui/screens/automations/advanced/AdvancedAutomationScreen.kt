package com.arsys.netatmo.ui.screens.automations.advanced

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController

private val Accent = Color(0xFF0284C7)
private val TextPrimary = Color(0xFF1E293B)

data class AutomationTrigger(
    val type: String,
    val hour: Int = 0,
    val minute: Int = 0,
    val days: List<Int> = emptyList(),
    val entity: String = "",
    val below: Boolean = true,
    val value: Double = 0.0,
    val event: String = "sunset",
    val offset: Int = 0,
    val action: String = "enter"
)

data class AutomationCondition(
    val type: String,
    val from: String = "",
    val to: String = "",
    val entity: String = "",
    val below: Boolean = true,
    val value: Double = 0.0
)

data class AutomationAction(
    val type: String,
    val temperature: Double = 20.0,
    val mode: String = "",
    val title: String = "",
    val minutes: Int = 0,
    val scenarioId: String = ""
)

private fun AutomationTrigger.toMap(): Map<String, Any> = mapOf(
    "type" to type, "hour" to hour, "minute" to minute,
    "days" to days, "entity" to entity, "below" to below,
    "value" to value, "event" to event, "offset" to offset, "action" to action
)

private fun Map<String, Any>.toAutoTrigger() = AutomationTrigger(
    type = this["type"] as? String ?: "",
    hour = (this["hour"] as? Number)?.toInt() ?: 0,
    minute = (this["minute"] as? Number)?.toInt() ?: 0,
    days = (this["days"] as? List<*>)?.mapNotNull { (it as? Number)?.toInt() } ?: emptyList(),
    entity = this["entity"] as? String ?: "",
    below = this["below"] as? Boolean ?: true,
    value = (this["value"] as? Number)?.toDouble() ?: 0.0,
    event = this["event"] as? String ?: "sunset",
    offset = (this["offset"] as? Number)?.toInt() ?: 0,
    action = this["action"] as? String ?: "enter"
)

private fun AutomationCondition.toMap(): Map<String, Any> = mapOf(
    "type" to type, "from" to from, "to" to to,
    "entity" to entity, "below" to below, "value" to value
)

private fun Map<String, Any>.toAutoCondition() = AutomationCondition(
    type = this["type"] as? String ?: "",
    from = this["from"] as? String ?: "",
    to = this["to"] as? String ?: "",
    entity = this["entity"] as? String ?: "",
    below = this["below"] as? Boolean ?: true,
    value = (this["value"] as? Number)?.toDouble() ?: 0.0
)

private fun AutomationAction.toMap(): Map<String, Any> = mapOf(
    "type" to type, "temperature" to temperature, "mode" to mode,
    "title" to title, "minutes" to minutes, "scenarioId" to scenarioId
)

private fun Map<String, Any>.toAutoAction() = AutomationAction(
    type = this["type"] as? String ?: "",
    temperature = (this["temperature"] as? Number)?.toDouble() ?: 20.0,
    mode = this["mode"] as? String ?: "",
    title = this["title"] as? String ?: "",
    minutes = (this["minutes"] as? Number)?.toInt() ?: 0,
    scenarioId = this["scenarioId"] as? String ?: ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedAutomationScreen(
    automationId: Long,
    navController: NavController,
    viewModel: AdvancedAutomationViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    var name by remember { mutableStateOf(uiState.name) }
    var triggerMode by remember { mutableStateOf(uiState.triggerMode) }
    var enabled by remember { mutableStateOf(uiState.enabled) }

    var showTriggerSheet by remember { mutableStateOf(false) }
    var showConditionSheet by remember { mutableStateOf(false) }
    var showActionSheet by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.name) { name = uiState.name }
    LaunchedEffect(uiState.triggerMode) { triggerMode = uiState.triggerMode }
    LaunchedEffect(uiState.enabled) { enabled = uiState.enabled }

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) navController.popBackStack()
    }
    LaunchedEffect(uiState.isDeleted) {
        if (uiState.isDeleted) navController.popBackStack()
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Eliminar automatización") },
            text = { Text("¿Estás seguro de que quieres eliminar esta automatización? Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.delete()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showTriggerSheet) {
        TriggerEditorBottomSheet(
            onDismiss = { showTriggerSheet = false },
            onAdd = { trigger ->
                viewModel.addTrigger(trigger.toMap())
                showTriggerSheet = false
            }
        )
    }

    if (showConditionSheet) {
        ConditionEditorBottomSheet(
            onDismiss = { showConditionSheet = false },
            onAdd = { condition ->
                viewModel.addCondition(condition.toMap())
                showConditionSheet = false
            }
        )
    }

    if (showActionSheet) {
        ActionEditorBottomSheet(
            onDismiss = { showActionSheet = false },
            onAdd = { action ->
                viewModel.addAction(action.toMap())
                showActionSheet = false
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (automationId == -1L) "Nueva automatización" else "Editar automatización",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver", tint = Accent)
                    }
                },
                actions = {
                    if (automationId != -1L) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Eliminar",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Button(
                        onClick = {
                            viewModel.save(
                                name = name,
                                triggers = uiState.triggers,
                                conditions = uiState.conditions,
                                actions = uiState.actions,
                                triggerMode = triggerMode,
                                enabled = enabled
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Accent)
                    ) {
                        Text("Guardar", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            item {
                SectionHeader(title = "Disparadores", subtitle = "Cuándo se ejecuta")
            }
            itemsIndexed(uiState.triggers) { index, triggerMap ->
                TriggerChipRow(trigger = triggerMap.toAutoTrigger(), onDelete = { viewModel.removeTrigger(index) })
            }
            item {
                OutlinedButton(
                    onClick = { showTriggerSheet = true },
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, Accent)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Accent, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Añadir disparador", color = Accent)
                }
            }

            item {
                SectionHeader(title = "Condiciones", subtitle = "Solo si se cumplen")
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Modo:", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                    FilterChip(
                        selected = triggerMode == "any",
                        onClick = { triggerMode = "any" },
                        label = { Text("Cualquiera") }
                    )
                    FilterChip(
                        selected = triggerMode == "all",
                        onClick = { triggerMode = "all" },
                        label = { Text("Todas") }
                    )
                }
            }
            itemsIndexed(uiState.conditions) { index, conditionMap ->
                ConditionChipRow(condition = conditionMap.toAutoCondition(), onDelete = { viewModel.removeCondition(index) })
            }
            item {
                OutlinedButton(
                    onClick = { showConditionSheet = true },
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, Accent)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Accent, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Añadir condición", color = Accent)
                }
            }

            item {
                SectionHeader(title = "Acciones", subtitle = "Qué hacer")
            }
            itemsIndexed(uiState.actions) { index, actionMap ->
                ActionChipRow(action = actionMap.toAutoAction(), onDelete = { viewModel.removeAction(index) })
            }
            item {
                OutlinedButton(
                    onClick = { showActionSheet = true },
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, Accent)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Accent, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Añadir acción", color = Accent)
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                "Automatización activa",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = TextPrimary
                            )
                            Text(
                                if (enabled) "Se ejecutará cuando se cumplan las condiciones" else "No se ejecutará",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = enabled,
                            onCheckedChange = { enabled = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Accent
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(56.dp)
                    .background(
                        Accent,
                        RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)
                    )
            )
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TriggerChipRow(trigger: AutomationTrigger, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = triggerIcon(trigger.type),
                contentDescription = null,
                tint = Accent,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = triggerSummary(trigger),
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Eliminar",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun ConditionChipRow(condition: AutomationCondition, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = conditionIcon(condition.type),
                contentDescription = null,
                tint = Accent,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = conditionSummary(condition),
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Eliminar",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun ActionChipRow(action: AutomationAction, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = actionIcon(action.type),
                contentDescription = null,
                tint = Accent,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = actionSummary(action),
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Eliminar",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

private fun triggerSummary(trigger: AutomationTrigger): String = when (trigger.type) {
    "time" -> {
        val time = "%02d:%02d".format(trigger.hour, trigger.minute)
        val days = trigger.days.joinToString(",")
        "Hora: $time días $days"
    }
    "numeric_state" -> when (trigger.entity) {
        "outdoor_temp" -> "Temp ext. ${if (trigger.below) "bajo" else "sobre"} ${trigger.value.toInt()}°C"
        "indoor_temp" -> "Temp int. ${if (trigger.below) "bajo" else "sobre"} ${trigger.value.toInt()}°C"
        else -> trigger.entity
    }
    "sun" -> "Sol: ${trigger.event} +${trigger.offset} min"
    "geofence" -> "Geovalla: ${trigger.action}"
    else -> trigger.type
}

private fun conditionSummary(condition: AutomationCondition): String = when (condition.type) {
    "time_range" -> "Hora: ${condition.from} – ${condition.to}"
    "numeric_state" -> "Temp ${condition.entity} ${if (condition.below) "bajo" else "sobre"} ${condition.value.toInt()}"
    "presence" -> "Alguien en casa"
    else -> condition.type
}

private fun actionSummary(action: AutomationAction): String = when (action.type) {
    "set_temperature" -> "Setpoint ${"%.1f".format(action.temperature)}°C"
    "set_mode" -> "Modo: ${action.mode}"
    "notify" -> "Notificar: ${action.title}"
    "delay" -> "Esperar ${action.minutes} min"
    "apply_scenario" -> "Escenario: ${action.scenarioId}"
    else -> action.type
}

private fun triggerIcon(type: String): ImageVector = when (type) {
    "time" -> Icons.Default.Schedule
    "numeric_state" -> Icons.Default.Thermostat
    "sun" -> Icons.Default.WbSunny
    "geofence" -> Icons.Default.LocationOn
    else -> Icons.Default.Bolt
}

private fun conditionIcon(type: String): ImageVector = when (type) {
    "time_range" -> Icons.Default.AccessTime
    "numeric_state" -> Icons.Default.Thermostat
    "presence" -> Icons.Default.Group
    else -> Icons.Default.CheckCircle
}

private fun actionIcon(type: String): ImageVector = when (type) {
    "set_temperature" -> Icons.Default.Thermostat
    "set_mode" -> Icons.Default.Tune
    "notify" -> Icons.Default.Notifications
    "delay" -> Icons.Default.HourglassEmpty
    "apply_scenario" -> Icons.Default.AutoAwesome
    else -> Icons.Default.PlayArrow
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TriggerEditorBottomSheet(
    onDismiss: () -> Unit,
    onAdd: (AutomationTrigger) -> Unit
) {
    var selectedType by remember { mutableStateOf("time") }
    var hour by remember { mutableStateOf(8) }
    var minute by remember { mutableStateOf(0) }
    var selectedDays by remember { mutableStateOf(listOf(1, 2, 3, 4, 5)) }
    var entity by remember { mutableStateOf("outdoor_temp") }
    var below by remember { mutableStateOf(true) }
    var value by remember { mutableStateOf(18.0) }
    var sunEvent by remember { mutableStateOf("sunset") }
    var sunOffset by remember { mutableStateOf(0) }
    var geofenceAction by remember { mutableStateOf("enter") }

    val triggerTypes = listOf("time", "numeric_state", "sun", "geofence")

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Añadir disparador",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Text(
                "Tipo",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                triggerTypes.forEach { type ->
                    FilterChip(
                        selected = selectedType == type,
                        onClick = { selectedType = type },
                        label = {
                            Text(
                                when (type) {
                                    "time" -> "Hora"
                                    "numeric_state" -> "Temp"
                                    "sun" -> "Sol"
                                    "geofence" -> "Geo"
                                    else -> type
                                }
                            )
                        }
                    )
                }
            }

            when (selectedType) {
                "time" -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = hour.toString(),
                            onValueChange = { it.toIntOrNull()?.let { h -> if (h in 0..23) hour = h } },
                            label = { Text("Hora") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Text(":", style = MaterialTheme.typography.headlineMedium)
                        OutlinedTextField(
                            value = minute.toString(),
                            onValueChange = { it.toIntOrNull()?.let { m -> if (m in 0..59) minute = m } },
                            label = { Text("Min") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                    Text(
                        "Días",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val dayLabels = listOf("L", "M", "X", "J", "V", "S", "D")
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        dayLabels.forEachIndexed { index, label ->
                            val day = index + 1
                            FilterChip(
                                selected = day in selectedDays,
                                onClick = {
                                    selectedDays = if (day in selectedDays) {
                                        selectedDays - day
                                    } else {
                                        selectedDays + day
                                    }
                                },
                                label = { Text(label) }
                            )
                        }
                    }
                }
                "numeric_state" -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = entity == "outdoor_temp",
                            onClick = { entity = "outdoor_temp" },
                            label = { Text("Exterior") }
                        )
                        FilterChip(
                            selected = entity == "indoor_temp",
                            onClick = { entity = "indoor_temp" },
                            label = { Text("Interior") }
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = below,
                            onClick = { below = true },
                            label = { Text("Bajo") }
                        )
                        FilterChip(
                            selected = !below,
                            onClick = { below = false },
                            label = { Text("Sobre") }
                        )
                    }
                    OutlinedTextField(
                        value = value.toInt().toString(),
                        onValueChange = { it.toDoubleOrNull()?.let { v -> value = v } },
                        label = { Text("Temperatura (°C)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                "sun" -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = sunEvent == "sunset",
                            onClick = { sunEvent = "sunset" },
                            label = { Text("Atardecer") }
                        )
                        FilterChip(
                            selected = sunEvent == "sunrise",
                            onClick = { sunEvent = "sunrise" },
                            label = { Text("Amanecer") }
                        )
                    }
                    OutlinedTextField(
                        value = sunOffset.toString(),
                        onValueChange = { it.toIntOrNull()?.let { o -> sunOffset = o } },
                        label = { Text("Offset (min)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                "geofence" -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = geofenceAction == "enter",
                            onClick = { geofenceAction = "enter" },
                            label = { Text("Entrar") }
                        )
                        FilterChip(
                            selected = geofenceAction == "exit",
                            onClick = { geofenceAction = "exit" },
                            label = { Text("Salir") }
                        )
                    }
                }
            }

            Button(
                onClick = {
                    onAdd(
                        AutomationTrigger(
                            type = selectedType,
                            hour = hour,
                            minute = minute,
                            days = selectedDays,
                            entity = entity,
                            below = below,
                            value = value,
                            event = sunEvent,
                            offset = sunOffset,
                            action = geofenceAction
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Accent)
            ) {
                Text("Añadir")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConditionEditorBottomSheet(
    onDismiss: () -> Unit,
    onAdd: (AutomationCondition) -> Unit
) {
    var selectedType by remember { mutableStateOf("time_range") }
    var from by remember { mutableStateOf("08:00") }
    var to by remember { mutableStateOf("22:00") }
    var entity by remember { mutableStateOf("outdoor_temp") }
    var below by remember { mutableStateOf(true) }
    var value by remember { mutableStateOf(18.0) }

    val conditionTypes = listOf("time_range", "numeric_state", "presence")

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Añadir condición",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Text(
                "Tipo",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                conditionTypes.forEach { type ->
                    FilterChip(
                        selected = selectedType == type,
                        onClick = { selectedType = type },
                        label = {
                            Text(
                                when (type) {
                                    "time_range" -> "Horario"
                                    "numeric_state" -> "Temp"
                                    "presence" -> "Presencia"
                                    else -> type
                                }
                            )
                        }
                    )
                }
            }

            when (selectedType) {
                "time_range" -> {
                    OutlinedTextField(
                        value = from,
                        onValueChange = { from = it },
                        label = { Text("Desde (HH:MM)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = to,
                        onValueChange = { to = it },
                        label = { Text("Hasta (HH:MM)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                "numeric_state" -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = entity == "outdoor_temp",
                            onClick = { entity = "outdoor_temp" },
                            label = { Text("Exterior") }
                        )
                        FilterChip(
                            selected = entity == "indoor_temp",
                            onClick = { entity = "indoor_temp" },
                            label = { Text("Interior") }
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = below,
                            onClick = { below = true },
                            label = { Text("Bajo") }
                        )
                        FilterChip(
                            selected = !below,
                            onClick = { below = false },
                            label = { Text("Sobre") }
                        )
                    }
                    OutlinedTextField(
                        value = value.toInt().toString(),
                        onValueChange = { it.toDoubleOrNull()?.let { v -> value = v } },
                        label = { Text("Temperatura (°C)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                "presence" -> {
                    Text(
                        "Se ejecutará solo si alguien está en casa.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Button(
                onClick = {
                    onAdd(
                        AutomationCondition(
                            type = selectedType,
                            from = from,
                            to = to,
                            entity = entity,
                            below = below,
                            value = value
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Accent)
            ) {
                Text("Añadir")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionEditorBottomSheet(
    onDismiss: () -> Unit,
    onAdd: (AutomationAction) -> Unit
) {
    var selectedType by remember { mutableStateOf("set_temperature") }
    var temperature by remember { mutableStateOf(20.0) }
    var mode by remember { mutableStateOf("schedule") }
    var title by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf(5) }
    var scenarioId by remember { mutableStateOf("") }

    val actionTypes = listOf("set_temperature", "set_mode", "notify", "delay", "apply_scenario")

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Añadir acción",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Text(
                "Tipo",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                actionTypes.forEach { type ->
                    FilterChip(
                        selected = selectedType == type,
                        onClick = { selectedType = type },
                        label = {
                            Text(
                                when (type) {
                                    "set_temperature" -> "Temp"
                                    "set_mode" -> "Modo"
                                    "notify" -> "Notif."
                                    "delay" -> "Esperar"
                                    "apply_scenario" -> "Escenario"
                                    else -> type
                                },
                                fontSize = 11.sp
                            )
                        }
                    )
                }
            }

            when (selectedType) {
                "set_temperature" -> {
                    OutlinedTextField(
                        value = temperature.toString(),
                        onValueChange = { it.toDoubleOrNull()?.let { t -> temperature = t } },
                        label = { Text("Temperatura (°C)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                "set_mode" -> {
                    val modes = listOf("schedule", "manual", "away", "hg", "off")
                    val modeLabels = mapOf(
                        "schedule" to "Programado",
                        "manual" to "Manual",
                        "away" to "Ausente",
                        "hg" to "Anticongelación",
                        "off" to "Apagado"
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        modes.forEach { m ->
                            FilterChip(
                                selected = mode == m,
                                onClick = { mode = m },
                                label = { Text(modeLabels[m] ?: m) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
                "notify" -> {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Título de notificación") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                "delay" -> {
                    OutlinedTextField(
                        value = minutes.toString(),
                        onValueChange = { it.toIntOrNull()?.let { m -> minutes = m } },
                        label = { Text("Minutos de espera") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                "apply_scenario" -> {
                    OutlinedTextField(
                        value = scenarioId,
                        onValueChange = { scenarioId = it },
                        label = { Text("ID del escenario") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }

            Button(
                onClick = {
                    onAdd(
                        AutomationAction(
                            type = selectedType,
                            temperature = temperature,
                            mode = mode,
                            title = title,
                            minutes = minutes,
                            scenarioId = scenarioId
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Accent)
            ) {
                Text("Añadir")
            }
        }
    }
}
