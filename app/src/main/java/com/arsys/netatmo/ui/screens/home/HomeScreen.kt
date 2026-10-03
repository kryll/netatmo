package com.arsys.netatmo.ui.screens.home

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import com.arsys.netatmo.domain.model.ModuleState
import com.arsys.netatmo.domain.model.RoomState
import com.arsys.netatmo.domain.model.ThermostatMode
import com.arsys.netatmo.ui.components.TemperatureSlider
import com.arsys.netatmo.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedRoom by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = uiState.thermostatState?.homeName ?: "Netatmo Smart",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        uiState.thermostatState?.let {
                            Text(
                                text = "${it.rooms.size} habitación(es)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadThermostatData() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Actualizar")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                uiState.thermostatState == null && uiState.error == null -> {
                    // Estado inicial - no hay home seleccionado
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Home,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Sin hogar configurado",
                            style = MaterialTheme.typography.titleMedium
                        )
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
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Error, contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error)
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
                                        modifier = Modifier.padding(top = 8.dp)
                                    )
                                }
                                items(modules, key = { it.id }) { module ->
                                    ModuleCard(module = module)
                                }
                            }
                        }
                    }
                }
            }

            // Success snackbar
            AnimatedVisibility(
                visible = uiState.successMessage != null,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary)
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

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onSelect,
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
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
                                color = WarmColor.copy(alpha = 0.2f)
                            ) {
                                Icon(
                                    Icons.Default.Whatshot,
                                    contentDescription = "Calentando",
                                    modifier = Modifier.padding(4.dp).size(16.dp),
                                    tint = WarmColor
                                )
                            }
                        }
                    }
                    Text(
                        text = room.mode.displayName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = room.targetTemp?.let { "%.1f°".format(it) } ?: "--",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Expanded controls
            AnimatedVisibility(visible = isSelected) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(16.dp))

                    Text("Temperatura objetivo", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    // +/- quick buttons + current value
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalIconButton(
                            onClick = { tempValue = (tempValue - 0.5).coerceAtLeast(7.0) },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "-0.5°C")
                        }
                        Text(
                            text = "%.1f°C".format(tempValue),
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                        FilledTonalIconButton(
                            onClick = { tempValue = (tempValue + 0.5).coerceAtMost(30.0) },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "+0.5°C")
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
                        listOf(30, 60, 120, 180).forEach { mins ->
                            FilterChip(
                                selected = duration == mins,
                                onClick = { duration = mins },
                                label = { Text("${mins}min") }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { onTemperatureChange(tempValue, duration) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSetting
                    ) {
                        if (isSetting) CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        else Text("Aplicar %.1f°C durante ${duration}min".format(tempValue))
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Modo", style = MaterialTheme.typography.labelLarge)
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
                                modifier = Modifier.weight(1f)
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
    // Netatmo returns battery_level in mV (3500–6000) for most modules; normalize to 0–100
    val batteryPct = module.batteryLevel?.let { raw ->
        if (raw <= 100) raw else ((raw - 3500).coerceIn(0, 2500) * 100 / 2500)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
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
                modifier = Modifier.size(32.dp),
                tint = if (module.reachable) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when (module.type) {
                        "NATherm1", "NTH01" -> "Termostato"
                        "NRV" -> "Válvula de radiador"
                        "OTM" -> "Módulo OpenTherm"
                        "NAPlug" -> "Relé / Enchufe"
                        "NAMain" -> "Estación principal"
                        else -> module.type
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = if (module.reachable) "Conectado" else "Sin conexión",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (module.reachable) MaterialTheme.colorScheme.tertiary
                            else MaterialTheme.colorScheme.error
                )
            }
            if (isPlug) {
                // NAPlug is mains-powered — show plug icon instead of battery
                Icon(
                    Icons.Default.ElectricalServices,
                    contentDescription = "Enchufado",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
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
