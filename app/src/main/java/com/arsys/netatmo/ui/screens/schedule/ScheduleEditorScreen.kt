package com.arsys.netatmo.ui.screens.schedule

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController

private val DAY_NAMES = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")

private fun formatTime(minuteOfDay: Int): String =
    "%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleEditorScreen(
    scheduleId: String?,
    navController: NavController,
    viewModel: ScheduleEditorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddSlotDialog by remember { mutableStateOf<Int?>(null) } // dayOfWeek index
    var editingName by remember { mutableStateOf(false) }
    var nameFieldValue by remember { mutableStateOf("") }

    LaunchedEffect(scheduleId) { viewModel.loadSchedule(scheduleId) }
    LaunchedEffect(uiState.saved) { if (uiState.saved) navController.popBackStack() }

    // Sync name field when state loads
    LaunchedEffect(uiState.name) {
        if (!editingName) nameFieldValue = uiState.name
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                },
                title = {
                    if (editingName) {
                        TextField(
                            value = nameFieldValue,
                            onValueChange = { nameFieldValue = it },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                focusedContainerColor = MaterialTheme.colorScheme.surface
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text(
                            text = uiState.name,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                },
                actions = {
                    if (editingName) {
                        IconButton(onClick = {
                            viewModel.setName(nameFieldValue)
                            editingName = false
                        }) {
                            Icon(Icons.Default.Check, contentDescription = "Confirmar nombre")
                        }
                    } else {
                        IconButton(onClick = {
                            nameFieldValue = uiState.name
                            editingName = true
                        }) {
                            Icon(Icons.Default.Edit, contentDescription = "Editar nombre")
                        }
                    }
                    if (uiState.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(24.dp)
                                .padding(end = 8.dp)
                        )
                    } else {
                        IconButton(onClick = { viewModel.save() }) {
                            Icon(Icons.Default.Save, contentDescription = "Guardar")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Error banner
            uiState.error?.let { error ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Error,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(error, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            // Zones section header
            item {
                Text(
                    text = "Zonas de temperatura",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Zone list
            items(uiState.zones, key = { it.id }) { zone ->
                ZoneEditorRow(
                    zone = zone,
                    canDelete = uiState.zones.size > 1,
                    onUpdate = { viewModel.updateZone(it) },
                    onDelete = { viewModel.removeZone(zone.id) }
                )
            }

            // Add zone button
            item {
                OutlinedButton(
                    onClick = { viewModel.addZone() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Añadir zona")
                }
            }

            // Weekly schedule header
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Horario semanal",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // One row per day
            itemsIndexed(DAY_NAMES) { dayIndex, dayName ->
                val daySlots = uiState.slots
                    .filter { it.dayOfWeek == dayIndex }
                    .sortedBy { it.minuteOfDay }
                DayScheduleRow(
                    dayName = dayName,
                    dayIndex = dayIndex,
                    slots = daySlots,
                    zones = uiState.zones,
                    onAddSlot = { showAddSlotDialog = dayIndex },
                    onRemoveSlot = { slot -> viewModel.removeSlot(slot.dayOfWeek, slot.minuteOfDay) }
                )
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    // Add slot dialog
    showAddSlotDialog?.let { dayIndex ->
        AddSlotDialog(
            dayName = DAY_NAMES[dayIndex],
            zones = uiState.zones,
            onAdd = { minuteOfDay ->
                viewModel.addSlot(dayIndex, minuteOfDay)
                showAddSlotDialog = null
            },
            onDismiss = { showAddSlotDialog = null }
        )
    }
}

@Composable
private fun ZoneEditorRow(
    zone: ZoneUi,
    canDelete: Boolean,
    onUpdate: (ZoneUi) -> Unit,
    onDelete: () -> Unit
) {
    var nameValue by remember(zone.id) { mutableStateOf(zone.name) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Zone name
            OutlinedTextField(
                value = nameValue,
                onValueChange = {
                    nameValue = it
                    onUpdate(zone.copy(name = it))
                },
                label = { Text("Nombre") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )

            // Temperature control
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "%.1f°C".format(zone.temperature),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row {
                    IconButton(
                        onClick = {
                            val newTemp = (zone.temperature - 0.5).coerceIn(7.0, 30.0)
                            onUpdate(zone.copy(temperature = newTemp))
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Bajar")
                    }
                    IconButton(
                        onClick = {
                            val newTemp = (zone.temperature + 0.5).coerceIn(7.0, 30.0)
                            onUpdate(zone.copy(temperature = newTemp))
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Subir")
                    }
                }
            }

            // Delete button
            if (canDelete) {
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Eliminar zona",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayScheduleRow(
    dayName: String,
    dayIndex: Int,
    slots: List<SlotUi>,
    zones: List<ZoneUi>,
    onAddSlot: () -> Unit,
    onRemoveSlot: (SlotUi) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onAddSlot, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Add, contentDescription = "Añadir franja")
                }
            }
            if (slots.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                slots.forEach { slot ->
                    val zoneName = zones.find { it.id == slot.zoneId }?.name ?: "?"
                    SlotChip(
                        label = "${formatTime(slot.minuteOfDay)} → $zoneName",
                        onDelete = { onRemoveSlot(slot) }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
            } else {
                Text(
                    text = "Sin franjas horarias",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SlotChip(label: String, onDelete: () -> Unit) {
    InputChip(
        selected = false,
        onClick = {},
        label = { Text(label, style = MaterialTheme.typography.bodySmall) },
        trailingIcon = {
            IconButton(onClick = onDelete, modifier = Modifier.size(18.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Eliminar franja", modifier = Modifier.size(14.dp))
            }
        }
    )
}

@Composable
private fun AddSlotDialog(
    dayName: String,
    zones: List<ZoneUi>,
    onAdd: (minuteOfDay: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var hourValue by remember { mutableStateOf(7f) }
    var minuteValue by remember { mutableStateOf(0f) }
    var selectedZoneId by remember { mutableStateOf(zones.firstOrNull()?.id ?: 0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Añadir franja – $dayName") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Hour slider
                Text(
                    text = "Hora: %02d:%02d".format(hourValue.toInt(), (minuteValue * 5).toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text("Hora (0–23)", style = MaterialTheme.typography.labelSmall)
                Slider(
                    value = hourValue,
                    onValueChange = { hourValue = it },
                    valueRange = 0f..23f,
                    steps = 22
                )
                Text("Minutos (pasos de 5)", style = MaterialTheme.typography.labelSmall)
                Slider(
                    value = minuteValue,
                    onValueChange = { minuteValue = it },
                    valueRange = 0f..11f,
                    steps = 10
                )

                HorizontalDivider()

                // Zone selector
                Text("Zona:", style = MaterialTheme.typography.labelMedium)
                zones.forEach { zone ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RadioButton(
                            selected = selectedZoneId == zone.id,
                            onClick = { selectedZoneId = zone.id }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("${zone.name} (%.1f°C)".format(zone.temperature))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val totalMinutes = hourValue.toInt() * 60 + (minuteValue.toInt() * 5)
                onAdd(totalMinutes)
            }) {
                Text("Añadir")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
