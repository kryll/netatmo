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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.arsys.netatmo.ui.theme.OutlineVariant
import com.arsys.netatmo.ui.theme.SurfaceContainer
import com.arsys.netatmo.ui.theme.SurfaceContainerHigh
import com.arsys.netatmo.ui.theme.SurfaceContainerLow
import com.arsys.netatmo.ui.theme.SurfaceContainerLowest

// ── Data models ──────────────────────────────────────────────────────────────

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

// ── Map converters ────────────────────────────────────────────────────────────

private fun Map<String, Any>.toAutoTrigger() = AutomationTrigger(
    type = this["type"] as? String ?: this["platform"] as? String ?: "",
    hour = (this["hour"] as? Number)?.toInt() ?: 0,
    minute = (this["minute"] as? Number)?.toInt() ?: 0,
    days = (this["days"] as? List<*>)?.mapNotNull { (it as? Number)?.toInt() } ?: emptyList(),
    entity = this["entity"] as? String ?: "",
    below = this["below"] as? Boolean ?: (this["below"] != null),
    value = (this["below"] as? Number)?.toDouble() ?: (this["above"] as? Number)?.toDouble()
        ?: (this["value"] as? Number)?.toDouble() ?: 0.0,
    event = this["event"] as? String ?: "sunset",
    offset = (this["offset"] as? Number)?.toInt() ?: 0,
    action = this["action"] as? String ?: "enter"
)

private fun Map<String, Any>.toAutoCondition() = AutomationCondition(
    type = this["type"] as? String ?: "",
    from = this["from"] as? String ?: this["after"] as? String ?: "",
    to = this["to"] as? String ?: this["before"] as? String ?: "",
    entity = this["entity"] as? String ?: "",
    below = this["below"] != null,
    value = (this["below"] as? Number)?.toDouble() ?: (this["above"] as? Number)?.toDouble()
        ?: (this["value"] as? Number)?.toDouble() ?: 0.0
)

private fun Map<String, Any>.toAutoAction() = AutomationAction(
    type = this["type"] as? String ?: "",
    temperature = (this["temperature"] as? Number)?.toDouble() ?: 20.0,
    mode = this["mode"] as? String ?: "",
    title = this["title"] as? String ?: "",
    minutes = (this["minutes"] as? Number)?.toInt() ?: 0,
    scenarioId = this["scenarioId"] as? String ?: ""
)

// ── Screen ────────────────────────────────────────────────────────────────────

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
    LaunchedEffect(uiState.isSaved) { if (uiState.isSaved) navController.popBackStack() }
    LaunchedEffect(uiState.isDeleted) { if (uiState.isDeleted) navController.popBackStack() }

    // ── Delete dialog ─────────────────────────────────────────────────────────
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = SurfaceContainer,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = { Text("Eliminar automatización") },
            text = {
                Text("¿Estás seguro? Esta acción no se puede deshacer.")
            },
            confirmButton = {
                TextButton(
                    onClick = { showDeleteDialog = false; viewModel.delete() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancelar") }
            }
        )
    }

    // ── Bottom sheets ─────────────────────────────────────────────────────────
    if (showTriggerSheet) {
        TriggerEditorBottomSheet(
            onDismiss = { showTriggerSheet = false },
            onAdd = { map ->
                viewModel.addTrigger(map)
                showTriggerSheet = false
            }
        )
    }
    if (showConditionSheet) {
        ConditionEditorBottomSheet(
            onDismiss = { showConditionSheet = false },
            onAdd = { map ->
                viewModel.addCondition(map)
                showConditionSheet = false
            }
        )
    }
    if (showActionSheet) {
        ActionEditorBottomSheet(
            onDismiss = { showActionSheet = false },
            onAdd = { map ->
                viewModel.addAction(map)
                showActionSheet = false
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (automationId == -1L) "Nueva automatización" else "Editar automatización",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Volver",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SurfaceContainerLowest,
                    scrolledContainerColor = SurfaceContainerLowest
                )
            )
        },
        bottomBar = {
            Surface(
                color = SurfaceContainerLow,
                tonalElevation = 0.dp
            ) {
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
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Text(
                            "Guardar automatización",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Name field ────────────────────────────────────────────────────
            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre de la automatización") },
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
                        unfocusedContainerColor = SurfaceContainer,
                        focusedContainerColor = SurfaceContainer
                    )
                )
            }

            // ── IF section (Triggers) ─────────────────────────────────────────
            item {
                AutomationSectionCard {
                    SectionHeader(
                        icon = Icons.Default.Bolt,
                        label = "SI — DISPARADORES",
                        count = uiState.triggers.size,
                        accentColor = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(12.dp))

                    uiState.triggers.forEachIndexed { index, map ->
                        TriggerPill(
                            trigger = map.toAutoTrigger(),
                            onDelete = { viewModel.removeTrigger(index) }
                        )
                        Spacer(Modifier.height(8.dp))
                    }

                    AddItemButton(
                        label = "Añadir disparador",
                        onClick = { showTriggerSheet = true }
                    )
                }
            }

            // ── IF (conditions) section ────────────────────────────────────────
            item {
                AutomationSectionCard {
                    SectionHeader(
                        icon = Icons.Default.CheckCircle,
                        label = "Y SI — CONDICIONES",
                        count = uiState.conditions.size,
                        accentColor = MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(Modifier.height(8.dp))

                    // Trigger mode selector
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Modo:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        listOf("any" to "Cualquiera", "all" to "Todas").forEach { (value, label) ->
                            FilterChip(
                                selected = triggerMode == value,
                                onClick = { triggerMode = value },
                                label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f),
                                    selectedLabelColor = MaterialTheme.colorScheme.tertiary,
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = triggerMode == value,
                                    selectedBorderColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f),
                                    borderColor = OutlineVariant
                                )
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))

                    uiState.conditions.forEachIndexed { index, map ->
                        ConditionPill(
                            condition = map.toAutoCondition(),
                            onDelete = { viewModel.removeCondition(index) }
                        )
                        Spacer(Modifier.height(8.dp))
                    }

                    AddItemButton(
                        label = "Añadir condición",
                        onClick = { showConditionSheet = true }
                    )
                }
            }

            // ── THEN section (Actions) ─────────────────────────────────────────
            item {
                AutomationSectionCard {
                    SectionHeader(
                        icon = Icons.Default.PlayArrow,
                        label = "ENTONCES — ACCIONES",
                        count = uiState.actions.size,
                        accentColor = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(Modifier.height(12.dp))

                    uiState.actions.forEachIndexed { index, map ->
                        ActionPill(
                            action = map.toAutoAction(),
                            onDelete = { viewModel.removeAction(index) }
                        )
                        Spacer(Modifier.height(8.dp))
                    }

                    AddItemButton(
                        label = "Añadir acción",
                        onClick = { showActionSheet = true }
                    )
                }
            }

            // ── Enabled switch ────────────────────────────────────────────────
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow),
                    border = BorderStroke(1.dp, OutlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Automatización activa",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                if (enabled) "Se ejecutará cuando se cumplan las condiciones"
                                else "Desactivada — no se ejecutará",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(
                            checked = enabled,
                            onCheckedChange = { enabled = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                uncheckedTrackColor = SurfaceContainerHigh
                            )
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(4.dp)) }
        }
    }
}

// ── Reusable layout ───────────────────────────────────────────────────────────

@Composable
private fun AutomationSectionCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
        border = BorderStroke(1.dp, OutlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}

@Composable
private fun SectionHeader(
    icon: ImageVector,
    label: String,
    count: Int,
    accentColor: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = accentColor,
            letterSpacing = 0.5.sp
        )
        Spacer(Modifier.weight(1f))
        if (count > 0) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.2f))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = accentColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun AddItemButton(label: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
    ) {
        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

// ── Pill composables ──────────────────────────────────────────────────────────

@Composable
private fun TriggerPill(trigger: AutomationTrigger, onDelete: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    ItemPill(
        icon = triggerIcon(trigger.type),
        label = triggerSummary(trigger),
        iconTint = primary,
        bgColor = primary.copy(alpha = 0.12f),
        borderColor = primary.copy(alpha = 0.35f),
        textColor = primary,
        onDelete = onDelete
    )
}

@Composable
private fun ConditionPill(condition: AutomationCondition, onDelete: () -> Unit) {
    val tertiary = MaterialTheme.colorScheme.tertiary
    ItemPill(
        icon = conditionIcon(condition.type),
        label = conditionSummary(condition),
        iconTint = tertiary,
        bgColor = tertiary.copy(alpha = 0.12f),
        borderColor = tertiary.copy(alpha = 0.35f),
        textColor = tertiary,
        onDelete = onDelete
    )
}

@Composable
private fun ActionPill(action: AutomationAction, onDelete: () -> Unit) {
    val secondary = MaterialTheme.colorScheme.secondary
    val secondaryContainer = MaterialTheme.colorScheme.secondaryContainer
    ItemPill(
        icon = actionIcon(action.type),
        label = actionSummary(action),
        iconTint = secondary,
        bgColor = secondaryContainer.copy(alpha = 0.18f),
        borderColor = secondary.copy(alpha = 0.35f),
        textColor = secondary,
        onDelete = onDelete
    )
}

@Composable
private fun ItemPill(
    icon: ImageVector,
    label: String,
    iconTint: Color,
    bgColor: Color,
    borderColor: Color,
    textColor: Color,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.width(4.dp))
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Eliminar",
                    tint = textColor.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// ── Summary helpers ───────────────────────────────────────────────────────────

private fun triggerSummary(trigger: AutomationTrigger): String = when (trigger.type) {
    "time" -> {
        val time = trigger.let {
            val t = (it as? AutomationTrigger)
            // try to use raw time string stored via the new sheet
            "%02d:%02d".format(it.hour, it.minute)
        }
        val days = trigger.days.joinToString(", ") { dayName(it) }
        if (days.isBlank()) "Hora: $time" else "Hora: $time · $days"
    }
    "numeric_state" -> {
        val entity = if (trigger.entity == "outdoor_temp") "Temp. exterior" else "Temp. interior"
        val dir = if (trigger.below) "por debajo de" else "por encima de"
        "$entity $dir ${trigger.value.toInt()}°C"
    }
    "sun" -> {
        val event = if (trigger.event == "sunset") "Puesta de sol" else "Salida del sol"
        if (trigger.offset != 0) "$event ± ${trigger.offset} min" else event
    }
    "geofence" -> if (trigger.action == "enter") "Al llegar a casa" else "Al salir de casa"
    else -> trigger.type
}

private fun conditionSummary(condition: AutomationCondition): String = when (condition.type) {
    "time_range" -> "Entre ${condition.from} y ${condition.to}"
    "numeric_state" -> {
        val entity = if (condition.entity == "outdoor_temp") "Temp. exterior" else "Temp. interior"
        val dir = if (condition.below) "por debajo de" else "por encima de"
        "$entity $dir ${condition.value.toInt()}°C"
    }
    "presence" -> "Alguien en casa"
    else -> condition.type
}

private fun actionSummary(action: AutomationAction): String = when (action.type) {
    "set_temperature" -> "Fijar temperatura a ${"%.1f".format(action.temperature)}°C"
    "set_mode" -> "Cambiar modo a ${modeName(action.mode)}"
    "notify" -> "Notificación: ${action.title.ifBlank { "(sin título)" }}"
    "delay" -> "Esperar ${action.minutes} min"
    "apply_scenario" -> "Escenario: ${action.scenarioId}"
    else -> action.type
}

private fun dayName(day: Int): String = when (day) {
    1 -> "L"; 2 -> "M"; 3 -> "X"; 4 -> "J"; 5 -> "V"; 6 -> "S"; 7 -> "D"; else -> "$day"
}

private fun modeName(mode: String): String = when (mode) {
    "schedule" -> "Programado"; "manual" -> "Manual"; "away" -> "Ausente"
    "hg" -> "Anticongelación"; "off" -> "Apagado"
    "heating" -> "Calefacción"; "cooling" -> "Frío"
    else -> mode
}

// ── Icon helpers ──────────────────────────────────────────────────────────────

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
