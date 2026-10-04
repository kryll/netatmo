package com.arsys.netatmo.ui.screens.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.arsys.netatmo.data.api.models.Room

// ─── Design constants ─────────────────────────────────────────────────────────

private val Accent = Color(0xFF0284C7)
private val BgSurface = Color(0xFFF4F6F9)
private val TextPrimary = Color(0xFF1E293B)
private val TextSecondary = Color(0xFF64748B)

private val ZONE_COLORS = listOf(
    Color(0xFF1A6BB5),  // 0 Blue  – Noche
    Color(0xFFD4591E),  // 1 Orange – Mañana
    Color(0xFF5DB9C5),  // 2 Cyan  – Eco
    Color(0xFFF5B800),  // 3 Yellow – Confort
    Color(0xFF8BC34A),  // 4 Green
    Color(0xFF9C27B0),  // 5 Purple
)

private val ZONE_ICONS: List<ImageVector>
    get() = listOf(
        Icons.Default.Bedtime,
        Icons.Default.LocalFireDepartment,
        Icons.Default.Eco,
        Icons.Default.WbSunny,
        Icons.Default.Thermostat,
        Icons.Default.Thermostat,
    )

private fun zoneColor(zoneId: Int) = ZONE_COLORS.getOrElse(zoneId % ZONE_COLORS.size) { Color.Gray }
private fun zoneIcon(zoneId: Int) = ZONE_ICONS.getOrElse(zoneId % ZONE_ICONS.size) { Icons.Default.Thermostat }
private fun formatTime(minuteOfDay: Int) = "%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)

private val DAY_LABELS = listOf("L", "M", "X", "J", "V", "S", "D")

// ─── Screen ──────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleEditorScreen(
    scheduleId: String?,
    navController: NavController,
    viewModel: ScheduleEditorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddSlotDialog by remember { mutableStateOf(false) }
    var showSlotDetail by remember { mutableStateOf<SlotUi?>(null) }
    var showNameDialog by remember { mutableStateOf(false) }
    var showZoneManager by remember { mutableStateOf(false) }

    LaunchedEffect(scheduleId) { viewModel.loadSchedule(scheduleId) }
    LaunchedEffect(uiState.saved) { if (uiState.saved) navController.popBackStack() }

    val daySlots = remember(uiState.slots, uiState.selectedDay) {
        uiState.slots.filter { it.dayOfWeek == uiState.selectedDay }.sortedBy { it.minuteOfDay }
    }

    Scaffold(containerColor = BgSurface) { scaffoldPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding)
        ) {
            // ── Header ───────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
            ) {
                Column {
                    // Title bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Volver",
                                tint = Accent)
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Programación",
                                color = TextPrimary,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = uiState.name,
                                color = TextSecondary,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.clickable { showNameDialog = true }
                            )
                        }
                        // Zones manager
                        IconButton(onClick = { showZoneManager = true }) {
                            Icon(Icons.Default.Tune, contentDescription = "Gestionar zonas",
                                tint = Accent)
                        }
                        // Save
                        if (uiState.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(20.dp)
                                    .padding(end = 4.dp),
                                color = Accent,
                                strokeWidth = 2.dp
                            )
                        } else {
                            IconButton(onClick = { viewModel.save() }) {
                                Icon(Icons.Default.Check, contentDescription = "Guardar",
                                    tint = Accent)
                            }
                        }
                    }

                    // Day chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        DAY_LABELS.forEachIndexed { index, label ->
                            val selected = uiState.selectedDay == index
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (selected) Accent
                                        else Color(0xFFF1F5F9)
                                    )
                                    .clickable { viewModel.selectDay(index) }
                            ) {
                                Text(
                                    text = label,
                                    color = if (selected) Color.White else TextSecondary,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = Color(0xFFE2E8F0))
                }
            }

            // ── Timeline bar ──────────────────────────────────────────────────
            TimelineBar(
                slots = daySlots,
                zones = uiState.zones,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            )

            // ── Error banner ──────────────────────────────────────────────────
            uiState.error?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            // ── Slot list ─────────────────────────────────────────────────────
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(daySlots, key = { "${it.dayOfWeek}-${it.minuteOfDay}" }) { slot ->
                    SlotCard(
                        slot = slot,
                        zones = uiState.zones,
                        rooms = uiState.rooms,
                        onClick = { showSlotDetail = slot },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showAddSlotDialog = true }
                            .padding(horizontal = 24.dp, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Accent,
                            modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Añadir franja horaria",
                            color = Accent,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }

    // ── Dialogs & sheets ──────────────────────────────────────────────────────

    if (showNameDialog) {
        NameEditDialog(
            currentName = uiState.name,
            onConfirm = { viewModel.setName(it); showNameDialog = false },
            onDismiss = { showNameDialog = false }
        )
    }

    if (showAddSlotDialog) {
        AddSlotDialog(
            zones = uiState.zones,
            onAdd = { minuteOfDay, zoneId ->
                viewModel.addSlot(uiState.selectedDay, minuteOfDay, zoneId)
                showAddSlotDialog = false
            },
            onDismiss = { showAddSlotDialog = false }
        )
    }

    showSlotDetail?.let { slot ->
        SlotDetailSheet(
            slot = slot,
            zones = uiState.zones,
            rooms = uiState.rooms,
            onChangeZone = { zoneId -> viewModel.setSlotZone(slot.dayOfWeek, slot.minuteOfDay, zoneId) },
            onUpdateRoomTemp = { zoneId, roomId, temp -> viewModel.updateZoneRoomTemp(zoneId, roomId, temp) },
            onDelete = {
                viewModel.removeSlot(slot.dayOfWeek, slot.minuteOfDay)
                showSlotDetail = null
            },
            onDismiss = { showSlotDetail = null }
        )
    }

    if (showZoneManager) {
        ZoneManagerSheet(
            zones = uiState.zones,
            onUpdateZone = { viewModel.updateZone(it) },
            onAddZone = { viewModel.addZone() },
            onRemoveZone = { viewModel.removeZone(it) },
            onDismiss = { showZoneManager = false }
        )
    }
}

// ─── Timeline bar ─────────────────────────────────────────────────────────────

@Composable
private fun TimelineBar(
    slots: List<SlotUi>,
    zones: List<ZoneUi>,
    modifier: Modifier = Modifier
) {
    if (slots.isEmpty()) {
        Box(
            modifier
                .fillMaxWidth()
                .height(18.dp)
                .clip(CircleShape)
                .background(Color.LightGray)
        )
        return
    }

    data class Segment(val color: Color, val weight: Float, val startMinute: Int)

    val segments = mutableListOf<Segment>()
    if (slots.first().minuteOfDay > 0) {
        segments += Segment(zoneColor(slots.last().zoneId), slots.first().minuteOfDay.toFloat(), 0)
    }
    slots.forEachIndexed { i, slot ->
        val end = if (i < slots.size - 1) slots[i + 1].minuteOfDay else 1440
        segments += Segment(zoneColor(slot.zoneId), (end - slot.minuteOfDay).toFloat(), slot.minuteOfDay)
    }

    Column(modifier = modifier) {
        // Colored bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp)
                .clip(RoundedCornerShape(9.dp))
        ) {
            segments.forEach { seg ->
                Box(
                    Modifier
                        .weight(seg.weight)
                        .fillMaxHeight()
                        .background(seg.color)
                )
            }
        }
        // Time labels aligned to segment starts
        Spacer(Modifier.height(3.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            segments.forEach { seg ->
                val showLabel = seg.weight / 1440f > 0.07f
                Text(
                    text = if (showLabel) formatTime(seg.startMinute) else "",
                    modifier = Modifier.weight(seg.weight),
                    fontSize = 9.sp,
                    color = Color(0xFF888888),
                    textAlign = TextAlign.Start
                )
            }
        }
    }
}

// ─── Slot card ────────────────────────────────────────────────────────────────

@Composable
private fun SlotCard(
    slot: SlotUi,
    zones: List<ZoneUi>,
    rooms: List<Room>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val zone = zones.find { it.id == slot.zoneId }
    val color = zoneColor(slot.zoneId)
    val icon = zoneIcon(slot.zoneId)

    Card(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(vertical = 14.dp)
        ) {
            // Time label
            Text(
                text = formatTime(slot.minuteOfDay),
                modifier = Modifier
                    .width(68.dp)
                    .padding(start = 16.dp)
                    .align(Alignment.Top),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF666666)
            )

            // Left colored bar
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )

            Spacer(Modifier.width(14.dp))

            // Zone icon
            Icon(
                icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(22.dp).align(Alignment.Top)
            )

            Spacer(Modifier.width(12.dp))

            // Room temperatures
            Column(modifier = Modifier.weight(1f)) {
                if (zone != null && rooms.isNotEmpty()) {
                    rooms.forEach { room ->
                        val temp = zone.roomTemps[room.id] ?: zone.temperature
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "%.0f°C".format(temp),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF1C1C1E),
                                modifier = Modifier.width(44.dp)
                            )
                            Text(
                                text = room.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF1C1C1E)
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                    }
                } else {
                    Text(
                        text = zone?.name ?: "Zona ${slot.zoneId}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1C1C1E)
                    )
                    if (zone != null) {
                        Text(
                            text = "%.1f°C".format(zone.temperature),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF888888)
                        )
                    }
                }
            }

            // Chevron
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = "Ver detalles",
                tint = Color(0xFFCCCCCC),
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .padding(end = 12.dp)
            )
        }
    }
}

// ─── Slot detail sheet ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SlotDetailSheet(
    slot: SlotUi,
    zones: List<ZoneUi>,
    rooms: List<Room>,
    onChangeZone: (Int) -> Unit,
    onUpdateRoomTemp: (Int, String, Double) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val zone = zones.find { it.id == slot.zoneId }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(zoneColor(slot.zoneId))
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = formatTime(slot.minuteOfDay),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Eliminar franja",
                        tint = MaterialTheme.colorScheme.error)
                }
            }

            Spacer(Modifier.height(16.dp))

            // Zone picker
            Text("Zona activa", style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF888888))
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                zones.forEach { z ->
                    val selected = z.id == slot.zoneId
                    FilterChip(
                        selected = selected,
                        onClick = { onChangeZone(z.id) },
                        label = { Text(z.name) },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(zoneColor(z.id))
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = zoneColor(z.id).copy(alpha = 0.15f),
                            selectedLabelColor = zoneColor(z.id)
                        )
                    )
                }
            }

            // Room temperatures
            if (zone != null && rooms.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Text("Temperaturas", style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF888888))
                Spacer(Modifier.height(8.dp))
                rooms.forEach { room ->
                    val temp = zone.roomTemps[room.id] ?: zone.temperature
                    RoomTempRow(
                        roomName = room.name,
                        temp = temp,
                        accentColor = zoneColor(slot.zoneId),
                        onTempChange = { onUpdateRoomTemp(zone.id, room.id, it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RoomTempRow(
    roomName: String,
    temp: Double,
    accentColor: Color,
    onTempChange: (Double) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = roomName,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium
        )
        IconButton(
            onClick = { onTempChange((temp - 0.5).coerceIn(7.0, 30.0)) },
            modifier = Modifier.size(36.dp)
        ) {
            Icon(Icons.Default.Remove, contentDescription = "Bajar", tint = accentColor)
        }
        Text(
            text = "%.1f°C".format(temp),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = accentColor,
            modifier = Modifier.width(60.dp),
            textAlign = TextAlign.Center
        )
        IconButton(
            onClick = { onTempChange((temp + 0.5).coerceIn(7.0, 30.0)) },
            modifier = Modifier.size(36.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Subir", tint = accentColor)
        }
    }
}

// ─── Add slot dialog ──────────────────────────────────────────────────────────

@Composable
private fun AddSlotDialog(
    zones: List<ZoneUi>,
    onAdd: (minuteOfDay: Int, zoneId: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var hour by remember { mutableStateOf(7) }
    var minute by remember { mutableStateOf(0) }
    var selectedZoneId by remember { mutableStateOf(zones.firstOrNull()?.id ?: 0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nueva franja horaria") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Big time display
                Text(
                    text = "%02d:%02d".format(hour, minute),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = Accent,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                // Hour slider
                Column {
                    Text("Hora: $hour", style = MaterialTheme.typography.labelSmall)
                    Slider(
                        value = hour.toFloat(),
                        onValueChange = { hour = it.toInt() },
                        valueRange = 0f..23f,
                        steps = 22,
                        colors = SliderDefaults.colors(
                            activeTrackColor = Accent,
                            thumbColor = Accent
                        )
                    )
                }

                // Minute slider (5-min steps)
                Column {
                    Text("Minutos: %02d".format(minute), style = MaterialTheme.typography.labelSmall)
                    Slider(
                        value = (minute / 5).toFloat(),
                        onValueChange = { minute = (it.toInt() * 5).coerceIn(0, 55) },
                        valueRange = 0f..11f,
                        steps = 10,
                        colors = SliderDefaults.colors(
                            activeTrackColor = Accent,
                            thumbColor = Accent
                        )
                    )
                }

                HorizontalDivider()

                // Zone selector
                Text("Zona:", style = MaterialTheme.typography.labelMedium)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    zones.forEach { zone ->
                        val selected = selectedZoneId == zone.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (selected) zoneColor(zone.id).copy(alpha = 0.12f)
                                    else Color.Transparent
                                )
                                .clickable { selectedZoneId = zone.id }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(zoneColor(zone.id))
                            )
                            Spacer(Modifier.width(10.dp))
                            Icon(zoneIcon(zone.id), contentDescription = null,
                                tint = zoneColor(zone.id), modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = zone.name,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                                color = if (selected) zoneColor(zone.id) else Color.Unspecified
                            )
                            Text(
                                text = "%.1f°C".format(zone.temperature),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF888888)
                            )
                            if (selected) {
                                Spacer(Modifier.width(4.dp))
                                Icon(Icons.Default.Check, contentDescription = null,
                                    tint = zoneColor(zone.id), modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(hour * 60 + minute, selectedZoneId) },
                colors = ButtonDefaults.buttonColors(containerColor = Accent)
            ) { Text("Añadir") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

// ─── Name edit dialog ─────────────────────────────────────────────────────────

@Composable
private fun NameEditDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var nameValue by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nombre de la programación") },
        text = {
            OutlinedTextField(
                value = nameValue,
                onValueChange = { nameValue = it },
                label = { Text("Nombre") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(nameValue) }) { Text("Aceptar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

// ─── Zone manager sheet ───────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ZoneManagerSheet(
    zones: List<ZoneUi>,
    onUpdateZone: (ZoneUi) -> Unit,
    onAddZone: () -> Unit,
    onRemoveZone: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Zonas de temperatura",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(16.dp))

            zones.forEach { zone ->
                ZoneManagerRow(
                    zone = zone,
                    canDelete = zones.size > 1,
                    onNameChange = { onUpdateZone(zone.copy(name = it)) },
                    onTempChange = { onUpdateZone(zone.copy(temperature = it)) },
                    onDelete = { onRemoveZone(zone.id) }
                )
                Spacer(Modifier.height(8.dp))
            }

            OutlinedButton(
                onClick = onAddZone,
                modifier = Modifier.fillMaxWidth(),
                border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Añadir zona")
            }
        }
    }
}

@Composable
private fun ZoneManagerRow(
    zone: ZoneUi,
    canDelete: Boolean,
    onNameChange: (String) -> Unit,
    onTempChange: (Double) -> Unit,
    onDelete: () -> Unit
) {
    val color = zoneColor(zone.id)
    var nameValue by remember(zone.id) { mutableStateOf(zone.name) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.08f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(color)
        )
        OutlinedTextField(
            value = nameValue,
            onValueChange = { nameValue = it; onNameChange(it) },
            singleLine = true,
            modifier = Modifier.weight(1f),
            textStyle = MaterialTheme.typography.bodyMedium,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = color,
                unfocusedBorderColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedContainerColor = Color.Transparent
            )
        )

        // Temp controls
        IconButton(
            onClick = { onTempChange((zone.temperature - 0.5).coerceIn(7.0, 30.0)) },
            modifier = Modifier.size(32.dp)
        ) {
            Icon(Icons.Default.Remove, contentDescription = "Bajar", tint = color,
                modifier = Modifier.size(18.dp))
        }
        Text(
            text = "%.1f°".format(zone.temperature),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = color,
            modifier = Modifier.width(44.dp),
            textAlign = TextAlign.Center
        )
        IconButton(
            onClick = { onTempChange((zone.temperature + 0.5).coerceIn(7.0, 30.0)) },
            modifier = Modifier.size(32.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Subir", tint = color,
                modifier = Modifier.size(18.dp))
        }

        if (canDelete) {
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Eliminar",
                    tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
            }
        }
    }
}
