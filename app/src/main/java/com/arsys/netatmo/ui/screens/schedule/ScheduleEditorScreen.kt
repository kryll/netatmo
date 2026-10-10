package com.arsys.netatmo.ui.screens.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.arsys.netatmo.ui.theme.OutlineVariant
import com.arsys.netatmo.ui.theme.SurfaceContainer
import com.arsys.netatmo.ui.theme.SurfaceContainerHigh
import com.arsys.netatmo.ui.theme.SurfaceContainerHighest
import com.arsys.netatmo.ui.theme.SurfaceContainerLow
import com.arsys.netatmo.ui.theme.SurfaceContainerLowest

// ─── Design constants ─────────────────────────────────────────────────────────

private val ZONE_COLORS = listOf(
    Color(0xFF3B82F6),  // 0 Blue   – Noche
    Color(0xFFF97316),  // 1 Orange – Mañana
    Color(0xFF06B6D4),  // 2 Cyan   – Eco
    Color(0xFFEAB308),  // 3 Yellow – Confort
    Color(0xFF22C55E),  // 4 Green
    Color(0xFFA855F7),  // 5 Purple
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

private fun zoneColor(zoneId: Int) = ZONE_COLORS.getOrElse(zoneId % ZONE_COLORS.size) { Color(0xFF89929B) }
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { scaffoldPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding)
        ) {
            // ── TopAppBar dark ────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceContainerLowest)
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                Icons.Default.ArrowBack,
                                contentDescription = "Volver",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { showNameDialog = true },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = uiState.name,
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                            Text(
                                text = "Toca para editar",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }

                        IconButton(onClick = { showZoneManager = true }) {
                            Icon(
                                Icons.Default.Tune,
                                contentDescription = "Gestionar zonas",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        if (uiState.isSaving) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .padding(12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    strokeWidth = 2.dp
                                )
                            }
                        } else {
                            IconButton(onClick = { viewModel.save() }) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Guardar",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    // ── Day selector chips ────────────────────────────────────
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
                                        if (selected) MaterialTheme.colorScheme.primaryContainer
                                        else SurfaceContainerHigh
                                    )
                                    .clickable { viewModel.selectDay(index) }
                            ) {
                                Text(
                                    text = label,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = OutlineVariant, thickness = 1.dp)
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
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
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
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Añadir franja horaria",
                            color = MaterialTheme.colorScheme.primary,
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
                .clip(RoundedCornerShape(9.dp))
                .background(SurfaceContainerLow)
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
        // Colored bar over surfaceContainerLow base
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(SurfaceContainerLow)
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                segments.forEach { seg ->
                    Box(
                        Modifier
                            .weight(seg.weight)
                            .fillMaxHeight()
                            .background(seg.color.copy(alpha = 0.85f))
                    )
                }
            }
        }
        // Time labels aligned to segment starts
        Spacer(Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            segments.forEach { seg ->
                val showLabel = seg.weight / 1440f > 0.07f
                Text(
                    text = if (showLabel) formatTime(seg.startMinute) else "",
                    modifier = Modifier.weight(seg.weight),
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceContainerLow)
            .border(1.dp, OutlineVariant, RoundedCornerShape(16.dp))
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            // Left colored border 4dp
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
                    .background(color)
            )

            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 14.dp, horizontal = 12.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Time column – 68dp, labelLarge
                Text(
                    text = formatTime(slot.minuteOfDay),
                    modifier = Modifier
                        .width(68.dp)
                        .align(Alignment.Top),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Zone icon
                Icon(
                    icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier
                        .size(22.dp)
                        .align(Alignment.Top)
                )

                Spacer(Modifier.width(12.dp))

                // Room temperatures – bodyMedium
                Column(modifier = Modifier.weight(1f)) {
                    if (zone != null && rooms.isNotEmpty()) {
                        rooms.forEach { room ->
                            val temp = zone.roomTemps[room.id] ?: zone.temperature
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "%.0f°C".format(temp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.width(44.dp)
                                )
                                Text(
                                    text = room.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.height(2.dp))
                        }
                    } else {
                        Text(
                            text = zone?.name ?: "Zona ${slot.zoneId}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (zone != null) {
                            Text(
                                text = "%.1f°C".format(zone.temperature),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // ChevronRight
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = "Ver detalles",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
            }
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 4.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(OutlineVariant)
            )
        }
    ) {
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
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Eliminar franja",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Zone picker chips with dot
            Text(
                "Zona activa",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                zones.forEach { z ->
                    val selected = z.id == slot.zoneId
                    val zColor = zoneColor(z.id)
                    FilterChip(
                        selected = selected,
                        onClick = { onChangeZone(z.id) },
                        label = {
                            Text(
                                z.name,
                                color = if (selected) zColor else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(zColor)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = zColor.copy(alpha = 0.18f),
                            selectedLabelColor = zColor,
                            containerColor = SurfaceContainerHigh,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selected,
                            selectedBorderColor = zColor.copy(alpha = 0.4f),
                            borderColor = OutlineVariant
                        )
                    )
                }
            }

            // Room temperatures
            if (zone != null && rooms.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Text(
                    "Temperaturas",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        // Minus button on surfaceContainerHigh
        IconButton(
            onClick = { onTempChange((temp - 0.5).coerceIn(7.0, 30.0)) },
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceContainerHigh)
        ) {
            Icon(Icons.Default.Remove, contentDescription = "Bajar", tint = accentColor,
                modifier = Modifier.size(18.dp))
        }
        Text(
            text = "%.1f°C".format(temp),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = accentColor,
            modifier = Modifier.width(60.dp),
            textAlign = TextAlign.Center
        )
        // Plus button on surfaceContainerHigh
        IconButton(
            onClick = { onTempChange((temp + 0.5).coerceIn(7.0, 30.0)) },
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceContainerHigh)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Subir", tint = accentColor,
                modifier = Modifier.size(18.dp))
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
        containerColor = SurfaceContainer,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = {
            Text(
                "Nueva franja horaria",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Big time display – displaySmall
                Text(
                    text = "%02d:%02d".format(hour, minute),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                // Hour slider
                Column {
                    Text(
                        "Hora: $hour",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = hour.toFloat(),
                        onValueChange = { hour = it.toInt() },
                        valueRange = 0f..23f,
                        steps = 22,
                        colors = SliderDefaults.colors(
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = SurfaceContainerHighest,
                            thumbColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }

                // Minute slider (5-min steps)
                Column {
                    Text(
                        "Minutos: %02d".format(minute),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = (minute / 5).toFloat(),
                        onValueChange = { minute = (it.toInt() * 5).coerceIn(0, 55) },
                        valueRange = 0f..11f,
                        steps = 10,
                        colors = SliderDefaults.colors(
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = SurfaceContainerHighest,
                            thumbColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }

                HorizontalDivider(color = OutlineVariant, thickness = 1.dp)

                // Zone selector – pills with color
                Text(
                    "Zona:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    zones.forEach { zone ->
                        val selected = selectedZoneId == zone.id
                        val zColor = zoneColor(zone.id)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (selected) zColor.copy(alpha = 0.15f)
                                    else SurfaceContainerHigh
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (selected) zColor.copy(alpha = 0.4f) else Color.Transparent,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable { selectedZoneId = zone.id }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(zColor)
                            )
                            Spacer(Modifier.width(10.dp))
                            Icon(
                                zoneIcon(zone.id),
                                contentDescription = null,
                                tint = zColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = zone.name,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                                color = if (selected) zColor else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "%.1f°C".format(zone.temperature),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (selected) {
                                Spacer(Modifier.width(4.dp))
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = zColor,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(hour * 60 + minute, selectedZoneId) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) { Text("Añadir") }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) { Text("Cancelar") }
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
        containerColor = SurfaceContainer,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        title = {
            Text(
                "Nombre de la programación",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            OutlinedTextField(
                value = nameValue,
                onValueChange = { nameValue = it },
                label = { Text("Nombre") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = OutlineVariant,
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    cursorColor = MaterialTheme.colorScheme.primary
                )
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(nameValue) },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) { Text("Aceptar") }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) { Text("Cancelar") }
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 4.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(OutlineVariant)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Zonas de temperatura",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
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
                border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                )
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
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceContainerLow)
            .border(1.dp, OutlineVariant, RoundedCornerShape(12.dp))
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
                focusedContainerColor = Color.Transparent,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                cursorColor = color
            )
        )

        // Temp controls – IconButtons on surfaceContainerHigh
        IconButton(
            onClick = { onTempChange((zone.temperature - 0.5).coerceIn(7.0, 30.0)) },
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceContainerHigh)
        ) {
            Icon(Icons.Default.Remove, contentDescription = "Bajar", tint = color,
                modifier = Modifier.size(16.dp))
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
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceContainerHigh)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Subir", tint = color,
                modifier = Modifier.size(16.dp))
        }

        if (canDelete) {
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f))
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Eliminar",
                    tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
            }
        }
    }
}
