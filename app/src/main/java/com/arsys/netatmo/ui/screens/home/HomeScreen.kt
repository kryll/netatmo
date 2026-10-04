package com.arsys.netatmo.ui.screens.home

import androidx.compose.animation.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.arsys.netatmo.domain.model.ModuleState
import com.arsys.netatmo.domain.model.RoomState
import com.arsys.netatmo.domain.model.ScheduleInfo
import com.arsys.netatmo.domain.model.ThermostatMode
import com.arsys.netatmo.ui.components.TemperatureSlider
import com.arsys.netatmo.ui.theme.*

private val NetatmoPrimary = Color(0xFF0EA5E9)
private val NetatmoPrimaryDark = Color(0xFF0369A1)
private val NetatmoGradient = listOf(Color(0xFF0EA5E9), Color(0xFF0369A1))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedRoom by remember { mutableStateOf<String?>(null) }

    // Compute a representative current temperature for the header
    val avgTemp = uiState.thermostatState?.rooms
        ?.mapNotNull { it.currentTemp }
        ?.takeIf { it.isNotEmpty() }
        ?.average()

    Column(modifier = Modifier.fillMaxSize()) {
        // Gradient header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(NetatmoGradient))
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 20.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = uiState.thermostatState?.homeName ?: "Netatmo Smart",
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                        uiState.thermostatState?.let {
                            Text(
                                text = "${it.rooms.size} habitación(es)",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 13.sp
                            )
                        }
                    }
                    // Refresh button
                    IconButton(onClick = { viewModel.loadThermostatData() }) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Actualizar",
                            tint = Color.White
                        )
                    }
                }

                // Big temperature display
                avgTemp?.let { temp ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "%.1f".format(temp),
                            color = Color.White,
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 52.sp
                        )
                        Text(
                            text = "°C",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = 6.dp, start = 4.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Icon(
                            Icons.Default.Home,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier
                                .size(28.dp)
                                .padding(bottom = 4.dp)
                        )
                    }
                }
            }
        }

        // Content area
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = NetatmoPrimary
                    )
                }
                uiState.thermostatState == null && uiState.error == null -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .background(
                                    Brush.verticalGradient(NetatmoGradient),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Home,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = "Sin hogar configurado",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Ve a Ajustes para seleccionar tu hogar",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Error banner
                        uiState.error?.let { error ->
                            item {
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    elevation = CardDefaults.cardElevation(2.dp),
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
                                        IconButton(onClick = { viewModel.dismissError() }) {
                                            Icon(Icons.Default.Close, contentDescription = "Cerrar")
                                        }
                                    }
                                }
                            }
                        }

                        // Rooms
                        uiState.thermostatState?.rooms?.let { rooms ->
                            items(rooms, key = { it.id }) { room ->
                                RoomCard(
                                    room = room,
                                    isSelected = selectedRoom == room.id,
                                    onSelect = {
                                        selectedRoom = if (selectedRoom == room.id) null else room.id
                                    },
                                    onTemperatureChange = { temp, minutes ->
                                        viewModel.setRoomTemperature(room.id, temp, minutes)
                                    },
                                    onModeChange = { mode ->
                                        viewModel.setRoomMode(room.id, mode)
                                    },
                                    isSetting = uiState.isSettingTemp
                                )
                            }
                        }

                        // Modules
                        uiState.thermostatState?.modules?.let { modules ->
                            if (modules.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "Dispositivos",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(top = 8.dp, start = 4.dp)
                                    )
                                }
                                items(modules, key = { it.id }) { module ->
                                    ModuleCard(module = module)
                                }
                            }
                        }

                        // Schedules section
                        uiState.thermostatState?.schedules?.let { schedules ->
                            if (schedules.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "Programaciones",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(top = 8.dp, start = 4.dp)
                                    )
                                }
                                item {
                                    ScheduleCard(
                                        schedules = schedules,
                                        onSwitchSchedule = { scheduleId ->
                                            viewModel.switchSchedule(scheduleId)
                                        },
                                        onEditSchedule = { scheduleId ->
                                            navController.navigate("schedule/$scheduleId")
                                        },
                                        onNewSchedule = {
                                            navController.navigate("schedule/new")
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Success snackbar
            if (uiState.successMessage != null) {
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(uiState.successMessage ?: "")
                    }
                }
            }

            LaunchedEffect(uiState.successMessage) {
                if (uiState.successMessage != null) {
                    kotlinx.coroutines.delay(3000)
                    viewModel.dismissSuccess()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomCard(
    room: RoomState,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onTemperatureChange: (Double, Int) -> Unit,
    onModeChange: (ThermostatMode) -> Unit,
    isSetting: Boolean
) {
    var tempValue by remember(room.targetTemp) { mutableStateOf(room.targetTemp ?: 19.0) }
    var duration by remember { mutableStateOf(60) }
    val durationOptions = listOf(60 to "1h", 120 to "2h", 180 to "3h", 0 to "Sin limite")

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onSelect,
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                NetatmoPrimary.copy(alpha = 0.08f)
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = room.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        if (room.heatingActive) {
                            Surface(
                                shape = CircleShape,
                                color = WarmColor.copy(alpha = 0.15f)
                            ) {
                                Icon(
                                    Icons.Default.Whatshot,
                                    contentDescription = "Calentando",
                                    modifier = Modifier
                                        .padding(4.dp)
                                        .size(16.dp),
                                    tint = WarmColor
                                )
                            }
                        }
                    }
                    Text(
                        text = room.mode.displayName,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isSelected) NetatmoPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Temperature display
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = room.currentTemp?.let { "%.1f°".format(it) } ?: "--",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = temperatureColor(room.currentTemp)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.ThermostatAuto,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = NetatmoPrimary
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = room.targetTemp?.let { "%.1f°".format(it) } ?: "--",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = NetatmoPrimary
                        )
                    }
                }
            }

            // Expanded controls
            AnimatedVisibility(visible = isSelected) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    HorizontalDivider(color = NetatmoPrimary.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        "Temperatura objetivo",
                        style = MaterialTheme.typography.labelLarge,
                        color = NetatmoPrimaryDark
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // +/- quick buttons + current value
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalIconButton(
                            onClick = { tempValue = (tempValue - 0.5).coerceAtLeast(7.0) },
                            modifier = Modifier.size(48.dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = NetatmoPrimary.copy(alpha = 0.12f),
                                contentColor = NetatmoPrimary
                            )
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "-0.5C")
                        }
                        Text(
                            text = "%.1f°C".format(tempValue),
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = NetatmoPrimary,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                        FilledTonalIconButton(
                            onClick = { tempValue = (tempValue + 0.5).coerceAtMost(30.0) },
                            modifier = Modifier.size(48.dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = NetatmoPrimary.copy(alpha = 0.12f),
                                contentColor = NetatmoPrimary
                            )
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "+0.5C")
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    TemperatureSlider(
                        value = tempValue,
                        onValueChange = { tempValue = it }
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        durationOptions.forEach { (mins, label) ->
                            FilterChip(
                                selected = duration == mins,
                                onClick = { duration = mins },
                                label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NetatmoPrimary,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { onTemperatureChange(tempValue, duration) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSetting,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NetatmoPrimary
                        )
                    ) {
                        if (isSetting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            val label = if (duration == 0)
                                "Aplicar %.1f°C sin limite de tiempo".format(tempValue)
                            else
                                "Aplicar %.1f°C durante ${durationOptions.first { it.first == duration }.second}".format(tempValue)
                            Text(label, color = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Modo",
                        style = MaterialTheme.typography.labelLarge,
                        color = NetatmoPrimaryDark
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ThermostatMode.entries.take(4).forEach { mode ->
                            FilterChip(
                                selected = room.mode == mode,
                                onClick = { onModeChange(mode) },
                                label = { Text(mode.displayName, fontSize = 11.sp) },
                                modifier = Modifier.weight(1f),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NetatmoPrimary,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }
            }

            if (!room.reachable) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.SignalWifiOff,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "No accesible",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
fun ModuleCard(module: ModuleState) {
    val isPlug = module.type == "NAPlug"
    // Netatmo returns battery_level in mV (3500-6000) for most modules; normalize to 0-100
    val batteryPct = module.batteryLevel?.let { raw ->
        if (raw <= 100) raw else ((raw - 3500).coerceIn(0, 2500) * 100 / 2500)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        if (module.reachable) NetatmoPrimary.copy(alpha = 0.12f)
                        else MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (module.type) {
                        "NATherm1", "NTH01" -> Icons.Default.DeviceThermostat
                        "NRV" -> Icons.Default.Thermostat
                        "OTM" -> Icons.Default.Settings
                        "NAPlug" -> Icons.Default.Power
                        "NAMain" -> Icons.Default.Hub
                        else -> Icons.Default.DeviceHub
                    },
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = if (module.reachable) NetatmoPrimary
                           else MaterialTheme.colorScheme.error
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when (module.type) {
                        "NATherm1", "NTH01" -> "Termostato"
                        "NRV" -> "Valvula de radiador"
                        "OTM" -> "Modulo OpenTherm"
                        "NAPlug" -> "Rele / Enchufe"
                        "NAMain" -> "Estacion principal"
                        else -> module.type
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = if (module.reachable) "Conectado" else "Sin conexion",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (module.reachable) NetatmoPrimary
                            else MaterialTheme.colorScheme.error
                )
            }
            if (isPlug) {
                Icon(
                    Icons.Default.ElectricalServices,
                    contentDescription = "Enchufado",
                    tint = NetatmoPrimary.copy(alpha = 0.7f)
                )
            } else {
                batteryPct?.let { pct ->
                    Column(horizontalAlignment = Alignment.End) {
                        Icon(
                            imageVector = when {
                                pct > 75 -> Icons.Default.BatteryFull
                                pct > 50 -> Icons.Default.Battery5Bar
                                pct > 25 -> Icons.Default.Battery3Bar
                                else -> Icons.Default.BatteryAlert
                            },
                            contentDescription = null,
                            tint = when {
                                pct > 50 -> ComfortColor
                                pct > 25 -> Color(0xFFFF9800)
                                else -> MaterialTheme.colorScheme.error
                            }
                        )
                        Text("$pct%", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
fun ScheduleCard(
    schedules: List<ScheduleInfo>,
    onSwitchSchedule: (String) -> Unit,
    onEditSchedule: (String) -> Unit = {},
    onNewSchedule: () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            schedules.forEach { schedule ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (schedule.isActive) Icons.Default.RadioButtonChecked
                                      else Icons.Default.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (schedule.isActive) NetatmoPrimary
                               else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = schedule.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (schedule.isActive) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.weight(1f)
                    )
                    if (!schedule.isActive) {
                        TextButton(
                            onClick = { onSwitchSchedule(schedule.id) },
                            colors = ButtonDefaults.textButtonColors(contentColor = NetatmoPrimary)
                        ) {
                            Text("Activar")
                        }
                    } else {
                        Text(
                            "Activa",
                            style = MaterialTheme.typography.labelSmall,
                            color = NetatmoPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    IconButton(
                        onClick = { onEditSchedule(schedule.id) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Editar programacion",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = NetatmoPrimary.copy(alpha = 0.15f)
            )
            TextButton(
                onClick = onNewSchedule,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColors(contentColor = NetatmoPrimary)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Nueva programacion")
            }
        }
    }
}

private fun temperatureColor(temp: Double?): Color {
    if (temp == null) return Color.Gray
    return when {
        temp < 16 -> FrostColor
        temp < 19 -> CoolColor
        temp < 22 -> ComfortColor
        temp < 25 -> WarmColor
        else -> Color(0xFFD32F2F)
    }
}
