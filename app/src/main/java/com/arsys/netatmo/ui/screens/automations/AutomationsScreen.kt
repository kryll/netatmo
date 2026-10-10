package com.arsys.netatmo.ui.screens.automations

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.arsys.netatmo.data.local.entities.AutomationEntity
import com.arsys.netatmo.ui.navigation.Screen
import com.arsys.netatmo.ui.theme.*

// ── Semantic colors for automation types ──────────────────────────────────────
private fun automationTypeAccent(type: String): Color = when (type) {
    "GEOFENCE" -> CoolColor          // #0284C7
    "CALENDAR" -> Color(0xFF7C3AED)  // violet
    "SCHEDULE" -> ComfortColor       // #16A34A
    "ADVANCED" -> Color(0xFF7C3AED)  // violet
    else       -> AwayColor
}

private fun automationTypeContainerColor(type: String): Color = when (type) {
    "GEOFENCE" -> Color(0xFF3198DC)  // primaryContainer
    "CALENDAR" -> Color(0xFF5B21B6)  // violet container
    "SCHEDULE" -> Color(0xFF1CA64D)  // tertiaryContainer
    "ADVANCED" -> Color(0xFF5B21B6)
    else       -> AwayColor
}

private fun automationTypeIcon(type: String): ImageVector = when (type) {
    "GEOFENCE" -> Icons.Default.LocationOn
    "CALENDAR" -> Icons.Default.CalendarToday
    "SCHEDULE" -> Icons.Default.Schedule
    "ADVANCED" -> Icons.Default.AutoAwesome
    else       -> Icons.Default.AutoMode
}

private fun automationTypeLabel(type: String): String = when (type) {
    "GEOFENCE" -> "Geovalla"
    "CALENDAR" -> "Calendario"
    "SCHEDULE" -> "Horario"
    "ADVANCED" -> "Avanzada"
    else       -> type
}

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationsScreen(
    navController: NavController,
    viewModel: AutomationsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var fabExpanded by remember { mutableStateOf(false) }
    var showInactive by remember { mutableStateOf(false) }

    val activeAutomations = uiState.automations.filter { it.enabled }
    val inactiveAutomations = uiState.automations.filter { !it.enabled }

    val bgColor = Color(0xFF101419)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Header ────────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bgColor)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 20.dp)
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .background(MaterialTheme.colorScheme.tertiary, CircleShape)
                        )
                        Text(
                            text = "Motor Inteligente Activo",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = androidx.compose.ui.unit.TextUnit(
                                0.06f, androidx.compose.ui.unit.TextUnitType.Em
                            )
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Automatizaciones",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                IconButton(
                    onClick = { navController.navigate("automation_log") },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(40.dp)
                        .background(SurfaceContainer, CircleShape)
                ) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = "Historial",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            HorizontalDivider(
                color = OutlineVariant,
                thickness = 1.dp
            )

            // ── Content ───────────────────────────────────────────────────────
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 16.dp,
                    bottom = 88.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (uiState.automations.isEmpty() && !uiState.isLoading) {
                    item { EmptyAutomationsView() }
                } else {
                    // Active section header
                    item {
                        SectionHeader(
                            label = "Activas",
                            count = activeAutomations.size,
                            trailing = if (activeAutomations.isNotEmpty())
                                "${activeAutomations.size} en vigilancia" else null
                        )
                    }

                    if (activeAutomations.isEmpty()) {
                        item {
                            Text(
                                text = "No hay automatizaciones activas",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    } else {
                        items(activeAutomations, key = { it.id }) { automation ->
                            AutomationCard(
                                automation = automation,
                                onToggle = { viewModel.toggleAutomation(automation.id, it) },
                                onEdit = {
                                    when (automation.type) {
                                        "GEOFENCE" -> navController.navigate("geofence/${automation.id}")
                                        "CALENDAR" -> navController.navigate("calendar_automation/${automation.id}")
                                        "SCHEDULE" -> navController.navigate("schedule_automation/${automation.id}")
                                        "ADVANCED" -> navController.navigate("advanced_automation/${automation.id}")
                                    }
                                },
                                onDelete = { viewModel.deleteAutomation(automation) }
                            )
                        }
                    }

                    // Inactive collapsible section
                    if (inactiveAutomations.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(4.dp))
                            CollapsibleSectionHeader(
                                label = "Inactivas",
                                count = inactiveAutomations.size,
                                expanded = showInactive,
                                onToggle = { showInactive = !showInactive }
                            )
                        }

                        item {
                            AnimatedVisibility(
                                visible = showInactive,
                                enter = expandVertically(),
                                exit = shrinkVertically()
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    inactiveAutomations.forEach { automation ->
                                        AutomationCard(
                                            automation = automation,
                                            onToggle = { viewModel.toggleAutomation(automation.id, it) },
                                            onEdit = {
                                                when (automation.type) {
                                                    "GEOFENCE" -> navController.navigate("geofence/${automation.id}")
                                                    "CALENDAR" -> navController.navigate("calendar_automation/${automation.id}")
                                                    "SCHEDULE" -> navController.navigate("schedule_automation/${automation.id}")
                                                    "ADVANCED" -> navController.navigate("advanced_automation/${automation.id}")
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
        }

        // ── FAB expandible ────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 24.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            // Backdrop dismiss layer
            if (fabExpanded) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { fabExpanded = false }
                )
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AnimatedVisibility(
                    visible = fabExpanded,
                    enter = expandVertically(expandFrom = Alignment.Bottom),
                    exit = shrinkVertically(shrinkTowards = Alignment.Bottom)
                ) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FabMenuItem(
                            label = "Geovalla",
                            icon = Icons.Default.LocationOn,
                            containerColor = automationTypeContainerColor("GEOFENCE"),
                            contentColor = Color.White,
                            onClick = {
                                fabExpanded = false
                                navController.navigate("geofence/-1")
                            }
                        )
                        FabMenuItem(
                            label = "Calendario",
                            icon = Icons.Default.CalendarToday,
                            containerColor = Color(0xFF5B21B6),
                            contentColor = Color.White,
                            onClick = {
                                fabExpanded = false
                                navController.navigate("calendar_automation/-1")
                            }
                        )
                        FabMenuItem(
                            label = "Horario",
                            icon = Icons.Default.Schedule,
                            containerColor = automationTypeContainerColor("SCHEDULE"),
                            contentColor = Color.White,
                            onClick = {
                                fabExpanded = false
                                navController.navigate("schedule_automation/-1")
                            }
                        )
                        FabMenuItem(
                            label = "Avanzada",
                            icon = Icons.Default.AutoAwesome,
                            containerColor = Color(0xFF5B21B6),
                            contentColor = Color.White,
                            onClick = {
                                fabExpanded = false
                                navController.navigate("advanced_automation/-1")
                            }
                        )
                        FabMenuItem(
                            label = "Vacaciones",
                            icon = Icons.Default.BeachAccess,
                            containerColor = Color(0xFF0369A1),
                            contentColor = Color.White,
                            onClick = {
                                fabExpanded = false
                                navController.navigate("vacation_mode")
                            }
                        )
                    }
                }

                val rotation by animateFloatAsState(
                    targetValue = if (fabExpanded) 45f else 0f,
                    label = "fab_rotation"
                )

                FloatingActionButton(
                    onClick = { fabExpanded = !fabExpanded },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = if (fabExpanded) "Cerrar" else "Agregar automatización",
                        modifier = Modifier.rotate(rotation)
                    )
                }
            }
        }
    }
}

// ── Sub-components ─────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(
    label: String,
    count: Int,
    trailing: String? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold
        )
        if (count > 0) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = "($count)",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (trailing != null) {
            Spacer(Modifier.weight(1f))
            Text(
                text = trailing,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun CollapsibleSectionHeader(
    label: String,
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        label = "chevron_rotation"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onToggle)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            modifier = Modifier
                .size(20.dp)
                .rotate(rotation),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "$label ($count)",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun FabMenuItem(
    label: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = SurfaceContainerHigh,
            modifier = Modifier.padding(end = 8.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
        SmallFloatingActionButton(
            onClick = onClick,
            containerColor = containerColor,
            contentColor = contentColor,
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp))
        }
    }
}

// ── AutomationCard ─────────────────────────────────────────────────────────────

@Composable
fun AutomationCard(
    automation: AutomationEntity,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    val typeAccent = automationTypeAccent(automation.type)
    val typeContainer = automationTypeContainerColor(automation.type)
    val alpha = if (automation.enabled) 1f else 0.45f

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = SurfaceContainerHigh,
            title = {
                Text(
                    "Eliminar automatización",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    "¿Eliminar \"${automation.name}\"? Esta acción no se puede deshacer.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    onDelete()
                }) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancelar", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceContainer)
            .border(
                width = 1.dp,
                color = OutlineVariant,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onEdit)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Left accent strip
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(
                        typeAccent.copy(alpha = alpha),
                        RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
                    )
                    .align(Alignment.CenterVertically)
            ) {
                // spacer to give height
                Spacer(modifier = Modifier.height(64.dp))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 4.dp, top = 14.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon in circular container
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(typeContainer.copy(alpha = alpha * 0.25f), CircleShape)
                        .border(
                            width = 1.dp,
                            color = typeAccent.copy(alpha = alpha * 0.4f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = automationTypeIcon(automation.type),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = typeAccent.copy(alpha = alpha)
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = automation.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                        maxLines = 1
                    )
                    Spacer(Modifier.height(2.dp))
                    val subtitle = buildSubtitle(automation)
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                        maxLines = 1
                    )
                    automation.lastTriggeredAt?.let {
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.History,
                                contentDescription = null,
                                modifier = Modifier.size(11.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(
                                text = java.text.SimpleDateFormat(
                                    "dd/MM · HH:mm",
                                    java.util.Locale.getDefault()
                                ).format(java.util.Date(it)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                            )
                        }
                    }
                }

                Switch(
                    checked = automation.enabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = typeAccent,
                        uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        uncheckedTrackColor = OutlineVariant
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
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                    )
                }
            }
        }
    }
}

private fun buildSubtitle(automation: AutomationEntity): String = when (automation.type) {
    "GEOFENCE" -> "Geovalla · ${automation.mode}"
    "CALENDAR" -> "Calendario · %.1f°C".format(automation.targetTemperature)
    "ADVANCED" -> try {
        val obj = org.json.JSONObject(automation.triggerData)
        val nt = obj.optJSONArray("triggers")?.length() ?: 0
        val na = obj.optJSONArray("actions")?.length() ?: 0
        "$nt disparador(es), $na acción(es)"
    } catch (e: Exception) { "Automatización avanzada" }
    else -> "%.1f°C · ${automation.mode}".format(automation.targetTemperature)
}

// ── Empty state ────────────────────────────────────────────────────────────────

@Composable
fun EmptyAutomationsView() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 64.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        RoundedCornerShape(24.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(24.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.AutoMode,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = "Sin automatizaciones",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Pulsa + para crear una automatización\npor ubicación, calendario u horario",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
