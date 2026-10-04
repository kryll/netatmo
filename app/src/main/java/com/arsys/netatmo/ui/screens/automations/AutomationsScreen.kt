package com.arsys.netatmo.ui.screens.automations

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.arsys.netatmo.data.local.entities.AutomationEntity
import com.arsys.netatmo.ui.navigation.Screen

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
                    val byType = uiState.automations.groupBy { it.type }

                    byType.forEach { (type, automations) ->
                        item {
                            Text(
                                text = when (type) {
                                    "GEOFENCE" -> "Geovalla"
                                    "CALENDAR" -> "Calendario"
                                    "SCHEDULE" -> "Horario"
                                    else -> type
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                        items(automations, key = { it.id }) { automation ->
                            AutomationCard(
                                automation = automation,
                                onToggle = { viewModel.toggleAutomation(automation.id, it) },
                                onEdit = {
                                    when (automation.type) {
                                        "GEOFENCE" -> navController.navigate("geofence/${automation.id}")
                                        "CALENDAR" -> navController.navigate("calendar_automation/${automation.id}")
                                    }
                                },
                                onDelete = { viewModel.deleteAutomation(automation) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AutomationCard(
    automation: AutomationEntity,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

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
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        onClick = onEdit
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when (automation.type) {
                    "GEOFENCE" -> Icons.Default.LocationOn
                    "CALENDAR" -> Icons.Default.CalendarToday
                    "SCHEDULE" -> Icons.Default.Schedule
                    else -> Icons.Default.AutoMode
                },
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = if (automation.enabled) Accent else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = automation.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Obj: %.1f°C · ${automation.mode}".format(automation.targetTemperature),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                automation.lastTriggeredAt?.let {
                    Text(
                        text = "Último: ${java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.getDefault()).format(java.util.Date(it))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Switch(
                checked = automation.enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Accent
                )
            )
            IconButton(onClick = { showDeleteDialog = true }) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Eliminar",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun EmptyAutomationsCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .padding(32.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.AutoMode,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = Accent
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Sin automatizaciones",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Pulsa + para crear una automatización por ubicación o calendario",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
