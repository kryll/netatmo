package com.arsys.netatmo.ui.screens.automations

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.arsys.netatmo.data.local.entities.AutomationEntity
import com.arsys.netatmo.ui.navigation.Screen
import org.json.JSONObject

private val Accent = Color(0xFF0284C7)
private val TextPrimary = Color(0xFF1E293B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationsScreen(
    navController: NavController,
    viewModel: AutomationsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddMenu by remember { mutableStateOf(false) }
    var showInactive by remember { mutableStateOf(false) }

    val activeAutomations = uiState.automations.filter { it.enabled }
    val inactiveAutomations = uiState.automations.filter { !it.enabled }
    val hasBoth = activeAutomations.isNotEmpty() && inactiveAutomations.isNotEmpty()

    Scaffold(
        topBar = {},
        floatingActionButton = {
            Box {
                FloatingActionButton(
                    onClick = { showAddMenu = true },
                    containerColor = Accent,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Agregar")
                }
                DropdownMenu(
                    expanded = showAddMenu,
                    onDismissRequest = { showAddMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Geovalla (ubicación)") },
                        leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                        onClick = {
                            showAddMenu = false
                            navController.navigate("geofence/-1")
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Evento de calendario") },
                        leadingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null) },
                        onClick = {
                            showAddMenu = false
                            navController.navigate("calendar_automation/-1")
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Automatizacion de horario") },
                        leadingIcon = { Icon(Icons.Default.Schedule, contentDescription = null) },
                        onClick = {
                            showAddMenu = false
                            navController.navigate("schedule_automation/-1")
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Temperatura exterior") },
                        leadingIcon = { Icon(Icons.Default.WbSunny, contentDescription = null) },
                        onClick = {
                            showAddMenu = false
                            navController.navigate("outdoor_temp_automation/-1")
                        }
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 20.dp)
            ) {
                Text(
                    text = "Automatizaciones",
                    color = TextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(
                    onClick = { navController.navigate("automation_log") },
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    Icon(Icons.Default.History, contentDescription = "Registro", tint = Accent)
                }
            }
            HorizontalDivider(color = Color(0xFFE2E8F0))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (uiState.automations.isEmpty() && !uiState.isLoading) {
                    item {
                        EmptyAutomationsCard()
                    }
                } else {
                    // "Activas" section label — only when both sections have items
                    if (hasBoth) {
                        item {
                            Text(
                                text = "Activas",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                            )
                        }
                    }

                    // Active automations grouped by type
                    if (activeAutomations.isEmpty()) {
                        item {
                            EmptyAutomationsCard()
                        }
                    } else {
                        val byType = activeAutomations.groupBy { it.type }
                        byType.forEach { (type, automations) ->
                            item {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(automationTypeColor(type), RoundedCornerShape(3.dp))
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = when (type) {
                                            "GEOFENCE" -> "Geovalla"
                                            "CALENDAR" -> "Calendario"
                                            "SCHEDULE" -> "Horario"
                                            "OUTDOOR_TEMP" -> "Temp. Exterior"
                                            else -> type
                                        },
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${automations.size}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }
                            }
                            items(automations, key = { it.id }) { automation ->
                                AutomationCard(
                                    automation = automation,
                                    onToggle = { viewModel.toggleAutomation(automation.id, it) },
                                    onEdit = {
                                        when (automation.type) {
                                            "GEOFENCE" -> navController.navigate("geofence/${automation.id}")
                                            "CALENDAR" -> navController.navigate("calendar_automation/${automation.id}")
                                            "SCHEDULE" -> navController.navigate("schedule_automation/${automation.id}")
                                            "OUTDOOR_TEMP" -> navController.navigate("outdoor_temp_automation/${automation.id}")
                                        }
                                    },
                                    onDelete = { viewModel.deleteAutomation(automation) }
                                )
                            }
                        }
                    }

                    // Inactive/collapsed section
                    if (inactiveAutomations.isNotEmpty()) {
                        item {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showInactive = !showInactive }
                                    .padding(top = 8.dp, bottom = 4.dp)
                            ) {
                                Icon(
                                    imageVector = if (showInactive) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Inactivas (${inactiveAutomations.size})",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        item {
                            AnimatedVisibility(visible = showInactive) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        inactiveAutomations.forEachIndexed { index, automation ->
                                            var showDeleteDialog by remember { mutableStateOf(false) }
                                            val typeColor = automationTypeColor(automation.type)

                                            if (showDeleteDialog) {
                                                AlertDialog(
                                                    onDismissRequest = { showDeleteDialog = false },
                                                    title = { Text("Eliminar automatización") },
                                                    text = { Text("¿Eliminar \"${automation.name}\"?") },
                                                    confirmButton = {
                                                        TextButton(onClick = {
                                                            showDeleteDialog = false
                                                            viewModel.deleteAutomation(automation)
                                                        }) {
                                                            Text("Eliminar", color = MaterialTheme.colorScheme.error)
                                                        }
                                                    },
                                                    dismissButton = {
                                                        TextButton(onClick = { showDeleteDialog = false }) {
                                                            Text("Cancelar")
                                                        }
                                                    }
                                                )
                                            }

                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(
                                                        start = 12.dp,
                                                        end = 4.dp,
                                                        top = if (index == 0) 10.dp else 6.dp,
                                                        bottom = if (index == inactiveAutomations.lastIndex) 10.dp else 6.dp
                                                    ),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = automationTypeIcon(automation.type),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(24.dp),
                                                    tint = typeColor.copy(alpha = 0.5f)
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Text(
                                                    text = automation.name,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = TextPrimary.copy(alpha = 0.5f),
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Switch(
                                                    checked = false,
                                                    onCheckedChange = {
                                                        viewModel.toggleAutomation(automation.id, true)
                                                    },
                                                    colors = SwitchDefaults.colors(
                                                        checkedThumbColor = Color.White,
                                                        checkedTrackColor = typeColor
                                                    ),
                                                    modifier = Modifier.padding(horizontal = 2.dp)
                                                )
                                                IconButton(
                                                    onClick = { showDeleteDialog = true },
                                                    modifier = Modifier.size(36.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.Delete,
                                                        contentDescription = "Eliminar",
                                                        modifier = Modifier.size(18.dp),
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                                    )
                                                }
                                            }

                                            if (index < inactiveAutomations.lastIndex) {
                                                HorizontalDivider(
                                                    modifier = Modifier.padding(horizontal = 12.dp),
                                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun automationTypeColor(type: String): Color = when (type) {
    "GEOFENCE" -> Color(0xFF0284C7)
    "CALENDAR" -> Color(0xFF7C3AED)
    "SCHEDULE" -> Color(0xFF059669)
    "OUTDOOR_TEMP" -> Color(0xFFEA580C)
    else -> Color(0xFF64748B)
}

private fun automationTypeIcon(type: String): ImageVector = when (type) {
    "GEOFENCE" -> Icons.Default.LocationOn
    "CALENDAR" -> Icons.Default.CalendarToday
    "SCHEDULE" -> Icons.Default.Schedule
    "OUTDOOR_TEMP" -> Icons.Default.WbSunny
    else -> Icons.Default.AutoMode
}

@Composable
fun AutomationCard(
    automation: AutomationEntity,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    val typeColor = automationTypeColor(automation.type)
    val alpha = if (automation.enabled) 1f else 0.5f

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Eliminar automatización") },
            text = { Text("¿Eliminar \"${automation.name}\"?") },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; onDelete() }) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancelar") }
            }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        onClick = onEdit
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = automationTypeIcon(automation.type),
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = typeColor.copy(alpha = alpha)
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = automation.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary.copy(alpha = alpha)
                )
                Spacer(modifier = Modifier.height(2.dp))
                val subtitle = when (automation.type) {
                    "GEOFENCE" -> "Geovalla · ${automation.mode}"
                    "CALENDAR" -> "Calendario · %.1f°C".format(automation.targetTemperature)
                    "OUTDOOR_TEMP" -> run {
                        try {
                            val json = JSONObject(automation.triggerData ?: "")
                            val condition = json.getString("condition")
                            val thresholdTemp = json.getDouble("thresholdTemp")
                            val cond = if (condition == "below") "bajo" else "sobre"
                            "Ext. $cond %.1f°C → %.1f°C".format(thresholdTemp, automation.targetTemperature)
                        } catch (e: Exception) {
                            "Temperatura exterior"
                        }
                    }
                    else -> "%.1f°C · ${automation.mode}".format(automation.targetTemperature)
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
                )
                automation.lastTriggeredAt?.let {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = null,
                            modifier = Modifier.size(11.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = java.text.SimpleDateFormat("dd/MM · HH:mm", java.util.Locale.getDefault())
                                .format(java.util.Date(it)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            Switch(
                checked = automation.enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = typeColor
                ),
                modifier = Modifier.padding(horizontal = 2.dp)
            )
            IconButton(onClick = { showDeleteDialog = true }, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Eliminar",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
fun EmptyAutomationsCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF0284C7).copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.AutoMode,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = Accent
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Sin automatizaciones",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Pulsa + para crear una automatización\npor ubicación o calendario",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
