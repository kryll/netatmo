package com.arsys.netatmo.ui.screens.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import java.util.Calendar

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
private val DAY_NAMES = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")

private fun formatDuration(minutes: Int): String {
    val h = minutes / 60; val m = minutes % 60
    return when {
        h == 0 -> "${m}min"
        m == 0 -> "${h}h"
        else -> "${h}h ${m}min"
    }
}

private fun zoneDescription(name: String): String = when {
    name.contains("noche", ignoreCase = true) -> "Ahorro energético nocturno"
    name.contains("mañana", ignoreCase = true) -> "Despertar confortable"
    name.contains("eco", ignoreCase = true) -> "Modo económico inteligente"
    name.contains("confort", ignoreCase = true) -> "Temperatura de confort plena"
    else -> "Zona de temperatura"
}

private fun dayGroup(index: Int): String = when (index) {
    in 0..4 -> "L – V"
    else -> "S – D"
}

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
    var autoAdaptEnabled by remember { mutableStateOf(true) }
    var openWindowEnabled by remember { mutableStateOf(true) }

    LaunchedEffect(scheduleId) { viewModel.loadSchedule(scheduleId) }
    LaunchedEffect(uiState.saved) { if (uiState.saved) navController.popBackStack() }

    val daySlots = remember(uiState.slots, uiState.selectedDay) {
        uiState.slots.filter { it.dayOfWeek == uiState.selectedDay }.sortedBy { it.minuteOfDay }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            BottomActionBar(
                onSave = { viewModel.save() },
                onReset = { viewModel.loadSchedule(scheduleId) },
                isSaving = uiState.isSaving
            )
        }
    ) { scaffoldPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding)
        ) {
            TopBar(
                title = uiState.name,
                onBack = { navController.popBackStack() },
                onInfo = { showZoneManager = true },
                onSave = { viewModel.save() },
                isSaving = uiState.isSaving
            )

            uiState.error?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                item {
                    ActivePresetCard(
                        name = uiState.name,
                        slotCount = daySlots.size,
                        autoAdaptEnabled = autoAdaptEnabled,
                        onClick = { showNameDialog = true }
                    )
                }

                item {
                    DaySelectorRow(
                        selectedDay = uiState.selectedDay,
                        slots = uiState.slots,
                        onSelect = viewModel::selectDay
                    )
                }

                item {
                    DayGroupLabel(selectedDay = uiState.selectedDay)
                }

                item {
                    TimelineCard(
                        slots = daySlots,
                        zones = uiState.zones,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Franjas de temperatura",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(
                            onClick = { showZoneManager = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Tune,
                                contentDescription = "Gestionar zonas",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                itemsIndexed(
                    daySlots,
                    key = { _, slot -> "${slot.dayOfWeek}-${slot.minuteOfDay}" }
                ) { index, slot ->
                    val endMinute = daySlots.getOrNull(index + 1)?.minuteOfDay ?: 1440
                    val zone = uiState.zones.find { it.id == slot.zoneId }
                    SlotExpandedCard(
                        slot = slot,
                        zone = zone,
                        endMinute = endMinute,
                        onTempChange = { newTemp ->
                            zone?.let { viewModel.updateZone(it.copy(temperature = newTemp)) }
                        },
                        onEdit = { showSlotDetail = slot },
                        onDelete = { viewModel.removeSlot(slot.dayOfWeek, slot.minuteOfDay) },
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
                            .padding(horizontal = 24.dp, vertical = 16.dp),
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

                item {
                    SmartTogglesCard(
                        autoAdaptEnabled = autoAdaptEnabled,
                        openWindowEnabled = openWindowEnabled,
                        onAutoAdaptChange = { autoAdaptEnabled = it },
                        onOpenWindowChange = { openWindowEnabled = it }
                    )
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

// ─── TopBar ──────────────────────────────────────────────────────────────────

@Composable
private fun TopBar(
    title: String,
    onBack: () -> Unit,
    onInfo: () -> Unit,
    onSave: () -> Unit,
    isSaving: Boolean
) {
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
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "Volver",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = "Programación Semanal",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
                IconButton(onClick = onInfo) {
                    Icon(
                        Icons.Default.Tune,
                        contentDescription = "Gestionar zonas",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isSaving) {
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
                    IconButton(onClick = onSave) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Guardar",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            HorizontalDivider(color = OutlineVariant, thickness = 1.dp)
        }
    }
}

// ─── ActivePresetCard ─────────────────────────────────────────────────────────

@Composable
private fun ActivePresetCard(
    name: String,
    slotCount: Int,
    autoAdaptEnabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Name pill
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(SurfaceContainerHigh)
                .border(1.dp, OutlineVariant, RoundedCornerShape(20.dp))
                .clickable { onClick() }
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Default.ExpandMore,
                contentDescription = "Editar nombre",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }

        // Slot count chip
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(SurfaceContainerHigh)
                .border(1.dp, OutlineVariant, RoundedCornerShape(20.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Schedule,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "$slotCount franjas",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Auto-Adapt chip
        if (autoAdaptEnabled) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Bolt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "Auto",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

// ─── DaySelectorRow ──────────────────────────────────────────────────────────

@Composable
private fun DaySelectorRow(
    selectedDay: Int,
    slots: List<SlotUi>,
    onSelect: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        DAY_LABELS.forEachIndexed { index, label ->
            val selected = selectedDay == index
            val hasSlots = slots.any { it.dayOfWeek == index }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary
                            else SurfaceContainerHigh
                        )
                        .clickable { onSelect(index) }
                ) {
                    Text(
                        text = label,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 13.sp
                    )
                }
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(
                            if (hasSlots) MaterialTheme.colorScheme.primary
                            else Color.Transparent
                        )
                )
            }
        }
    }
}

// ─── DayGroupLabel ───────────────────────────────────────────────────────────

@Composable
private fun DayGroupLabel(selectedDay: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.DateRange,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "${DAY_NAMES[selectedDay]} · ${dayGroup(selectedDay)}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ─── Timeline card ────────────────────────────────────────────────────────────

@Composable
private fun TimelineCard(
    slots: List<SlotUi>,
    zones: List<ZoneUi>,
    modifier: Modifier = Modifier
) {
    val now = remember {
        val cal = Calendar.getInstance()
        cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    }

    // Build segments as list of (color, weight)
    val segments: List<Pair<Color, Float>> = remember(slots, zones) {
        if (slots.isEmpty()) return@remember listOf(Pair(Color(0xFF1C2025), 1440f))
        val result = mutableListOf<Pair<Color, Float>>()
        if (slots.first().minuteOfDay > 0) {
            result += Pair(zoneColor(slots.last().zoneId).copy(alpha = 0.5f), slots.first().minuteOfDay.toFloat())
        }
        slots.forEachIndexed { i, slot ->
            val end = if (i < slots.size - 1) slots[i + 1].minuteOfDay else 1440
            result += Pair(zoneColor(slot.zoneId), (end - slot.minuteOfDay).toFloat())
        }
        result
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceContainerLow)
            .border(1.dp, OutlineVariant, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Column {
            // Colored timeline bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(SurfaceContainerHigh)
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    segments.forEach { (color, weight) ->
                        Box(
                            Modifier
                                .weight(weight)
                                .fillMaxHeight()
                                .background(color.copy(alpha = 0.8f))
                        )
                    }
                }
                // Current time needle using weighted spacers
                Row(modifier = Modifier.fillMaxSize()) {
                    if (now > 0) Spacer(Modifier.weight(now.toFloat()))
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(2.dp)
                            .background(Color.White.copy(alpha = 0.9f))
                    )
                    if (now < 1440) Spacer(Modifier.weight((1440 - now).toFloat()))
                }
            }

            Spacer(Modifier.height(6.dp))

            // Time axis labels
            Row(modifier = Modifier.fillMaxWidth()) {
                val axisLabels = listOf("00:00", "06:00", "12:00", "18:00", "24:00")
                axisLabels.forEachIndexed { i, label ->
                    Text(
                        text = label,
                        modifier = if (i == axisLabels.lastIndex) Modifier else Modifier.weight(1f),
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = if (i == 0) TextAlign.Start
                        else if (i == axisLabels.lastIndex) TextAlign.End
                        else TextAlign.Center
                    )
                }
            }
        }
    }
}

// ─── SlotExpandedCard ─────────────────────────────────────────────────────────

@Composable
private fun SlotExpandedCard(
    slot: SlotUi,
    zone: ZoneUi?,
    endMinute: Int,
    onTempChange: (Double) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val color = zoneColor(slot.zoneId)
    val icon = zoneIcon(slot.zoneId)
    val duration = endMinute - slot.minuteOfDay
    val temp = zone?.temperature ?: 20.0

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceContainerLow)
            .border(1.dp, OutlineVariant, RoundedCornerShape(16.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            // Left colored border
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
                    .background(color)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 12.dp)
            ) {
                // Header row: icon + name/time + MoreVert
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Zone icon box
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(color.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = color,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = zone?.name ?: "Zona ${slot.zoneId}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.width(6.dp))
                            // Duration badge
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(SurfaceContainerHigh)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = formatDuration(duration),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Text(
                            text = "${formatTime(slot.minuteOfDay)} → ${formatTime(endMinute)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "Opciones",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Description
                if (zone != null) {
                    Text(
                        text = zoneDescription(zone.name),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Spacer(Modifier.height(10.dp))
                }

                // Temperature control row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Minus button
                    IconButton(
                        onClick = { onTempChange((temp - 0.5).coerceIn(7.0, 30.0)) },
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceContainerHigh)
                    ) {
                        Icon(
                            Icons.Default.Remove,
                            contentDescription = "Bajar temperatura",
                            tint = color,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Temperature display
                    Text(
                        text = "%.1f°C".format(temp),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = color
                    )

                    // Plus button
                    IconButton(
                        onClick = { onTempChange((temp + 0.5).coerceIn(7.0, 30.0)) },
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceContainerHigh)
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Subir temperatura",
                            tint = color,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

// ─── SmartTogglesCard ─────────────────────────────────────────────────────────

@Composable
private fun SmartTogglesCard(
    autoAdaptEnabled: Boolean,
    openWindowEnabled: Boolean,
    onAutoAdaptChange: (Boolean) -> Unit,
    onOpenWindowChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceContainerLow)
            .border(1.dp, OutlineVariant, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = "Funciones inteligentes",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(12.dp))

        // Auto-Adapt toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (autoAdaptEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        else SurfaceContainerHigh
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Bolt,
                    contentDescription = null,
                    tint = if (autoAdaptEnabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Auto-Adapt",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Ajuste predictivo de temperatura",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = autoAdaptEnabled,
                onCheckedChange = onAutoAdaptChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    uncheckedTrackColor = SurfaceContainerHigh
                )
            )
        }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = OutlineVariant, thickness = 1.dp)
        Spacer(Modifier.height(8.dp))

        // Open Window toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (openWindowEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        else SurfaceContainerHigh
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Thermostat,
                    contentDescription = null,
                    tint = if (openWindowEnabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Detección ventana abierta",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Pausa automática al abrir ventana",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = openWindowEnabled,
                onCheckedChange = onOpenWindowChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    uncheckedTrackColor = SurfaceContainerHigh
                )
            )
        }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = OutlineVariant, thickness = 1.dp)
        Spacer(Modifier.height(8.dp))

        // Weather integration row (informational)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFF97316).copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.WbSunny,
                    contentDescription = null,
                    tint = Color(0xFFF97316),
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = "Integración meteorológica",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Activa · Conectada con estación exterior",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFF97316)
                )
            }
        }
    }
}

// ─── BottomActionBar ─────────────────────────────────────────────────────────

@Composable
private fun BottomActionBar(
    onSave: () -> Unit,
    onReset: () -> Unit,
    isSaving: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceContainerLowest)
    ) {
        HorizontalDivider(color = OutlineVariant, thickness = 1.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onReset,
                enabled = !isSaving,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Text("Restablecer")
            }
            Button(
                onClick = onSave,
                enabled = !isSaving,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Guardar programación")
                }
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
                Text(
                    text = "%02d:%02d".format(hour, minute),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

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
