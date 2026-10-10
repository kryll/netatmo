package com.arsys.netatmo.ui.screens.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.scale
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

// Background base
private val ScreenBackground = Color(0xFF101419)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val boostState by viewModel.boostState.collectAsState()
    val remainingMinutes by viewModel.boostRemainingMinutes.collectAsState()
    var selectedRoom by remember { mutableStateOf<String?>(null) }
    var showBoostDialog by remember { mutableStateOf(false) }

    val avgTemp = uiState.thermostatState?.rooms
        ?.mapNotNull { it.currentTemp }
        ?.takeIf { it.isNotEmpty() }
        ?.average()

    val isBoilerOn = uiState.thermostatState?.let { state ->
        state.modules.any { it.boilerStatus == true } ||
        state.rooms.any { it.heatingActive }
    } ?: false

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBackground)
    ) {
        // Warm glow backdrop when boiler is active
        if (isBoilerOn) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                WarmColor.copy(alpha = 0.08f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // Fixed header
            StitchHeader(
                homeName = uiState.thermostatState?.homeName,
                roomCount = uiState.thermostatState?.rooms?.size,
                onRefresh = { viewModel.loadThermostatData() }
            )

            // Content
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    uiState.isLoading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    uiState.thermostatState == null && uiState.error == null -> {
                        EmptyHomeState()
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 96.dp),
                            verticalArrangement = Arrangement.spacedBy(0.dp)
                        ) {
                            // Temperature tiles
                            item {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                        .padding(top = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // Error banner
                                    uiState.error?.let { error ->
                                        ErrorBanner(
                                            message = error,
                                            onDismiss = { viewModel.dismissError() }
                                        )
                                    }

                                    // Temp tiles row
                                    if (avgTemp != null || uiState.outdoorTemperature != null) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            avgTemp?.let { temp ->
                                                InteriorTempTile(
                                                    temp = temp,
                                                    humidity = null,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                            uiState.outdoorTemperature?.let { outTemp ->
                                                ExteriorTempTile(
                                                    temp = outTemp,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }
                                    }

                                    // Boiler card
                                    AnimatedVisibility(
                                        visible = isBoilerOn,
                                        enter = fadeIn() + expandVertically(),
                                        exit = fadeOut() + shrinkVertically()
                                    ) {
                                        BoilerStatusCard(isActive = isBoilerOn)
                                    }

                                    // Boost active banner
                                    AnimatedVisibility(
                                        visible = boostState != null,
                                        enter = fadeIn() + expandVertically(),
                                        exit = fadeOut() + shrinkVertically()
                                    ) {
                                        BoostActiveBanner(
                                            remainingMinutes = remainingMinutes,
                                            onDeactivate = { viewModel.stopBoost() }
                                        )
                                    }
                                }
                            }

                            // Rooms section header
                            uiState.thermostatState?.rooms?.let { rooms ->
                                if (rooms.isNotEmpty()) {
                                    item {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                                .padding(top = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Zonas Climatizadas",
                                                style = MaterialTheme.typography.titleLarge,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "${rooms.size} zona(s)",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    items(rooms, key = { it.id }) { room ->
                                        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp)) {
                                            DarkRoomCard(
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
                                }
                            }

                            // Modules section
                            uiState.thermostatState?.modules?.let { modules ->
                                if (modules.isNotEmpty()) {
                                    item {
                                        Text(
                                            text = "Dispositivos",
                                            style = MaterialTheme.typography.titleLarge,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier
                                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                                .padding(top = 8.dp)
                                        )
                                    }
                                    items(modules, key = { it.id }) { module ->
                                        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp)) {
                                            DarkModuleCard(module = module)
                                        }
                                    }
                                }
                            }

                            // Schedules section
                            uiState.thermostatState?.schedules?.let { schedules ->
                                if (schedules.isNotEmpty()) {
                                    item {
                                        Text(
                                            text = "Programaciones",
                                            style = MaterialTheme.typography.titleLarge,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier
                                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                                .padding(top = 8.dp)
                                        )
                                    }
                                    item {
                                        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp)) {
                                            DarkScheduleCard(
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
                }

                // Boost FAB — only when thermostat is loaded and no boost is active
                if (uiState.thermostatState != null && boostState == null) {
                    FloatingActionButton(
                        onClick = { showBoostDialog = true },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp),
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ) {
                        Icon(Icons.Default.Whatshot, contentDescription = "Modo Turbo")
                    }
                }

                // Success snackbar
                if (uiState.successMessage != null) {
                    Card(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 16.dp, vertical = 88.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = SurfaceContainerHigh
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f)
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
                            Text(
                                text = uiState.successMessage ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
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

    if (showBoostDialog) {
        DarkBoostDialog(
            onDismiss = { showBoostDialog = false },
            onConfirm = { delta, duration ->
                viewModel.startBoost(delta, duration)
                showBoostDialog = false
            }
        )
    }
}

// ─── Header ───────────────────────────────────────────────────────────────────

@Composable
private fun StitchHeader(
    homeName: String?,
    roomCount: Int?,
    onRefresh: () -> Unit
) {
    val refreshAnim = rememberInfiniteTransition(label = "refresh")
    var isRefreshing by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Color(0xFF101419).copy(alpha = 0.92f)
            )
            .statusBarsPadding()
    ) {
        Column {
            // App bar row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Logo + label + title
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    // Logo circle
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Home,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Netatmo Smart",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = homeName ?: "Inicio",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Actions
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Refresh button
                    Surface(
                        onClick = { onRefresh() },
                        shape = CircleShape,
                        color = SurfaceContainer,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Actualizar",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    // Avatar
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Bottom border of header
            HorizontalDivider(
                color = OutlineVariant,
                thickness = 1.dp
            )
        }
    }
}

// ─── Empty State ──────────────────────────────────────────────────────────────

@Composable
private fun EmptyHomeState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Home,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "Sin hogar configurado",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Ve a Ajustes para seleccionar tu hogar",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ─── Error Banner ─────────────────────────────────────────────────────────────

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Cerrar",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// ─── Temperature Tiles ────────────────────────────────────────────────────────

@Composable
private fun InteriorTempTile(
    temp: Double,
    humidity: Int?,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .border(1.dp, OutlineVariant, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = SurfaceContainer
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Home,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "INTERIOR",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "%.1f".format(temp),
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 52.sp,
                    letterSpacing = (-1.5).sp
                )
                Text(
                    text = "°C",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(
                    Icons.Default.WaterDrop,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = if (humidity != null) "$humidity% Humedad" else "-- Humedad",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ExteriorTempTile(
    temp: Double,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .border(1.dp, OutlineVariant, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = SurfaceContainer
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.WbSunny,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(20.dp)
                )
                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "EXTERIOR",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "%.1f".format(temp),
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 52.sp,
                    letterSpacing = (-1.5).sp
                )
                Text(
                    text = "°C",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(
                    Icons.Default.Air,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = "Exterior",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ─── Boiler Status Card ───────────────────────────────────────────────────────

@Composable
private fun BoilerStatusCard(isActive: Boolean) {
    val pulseAnim = rememberInfiniteTransition(label = "boiler_pulse")
    val pulseScale by pulseAnim.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot_scale"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, OutlineVariant.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = if (isActive) WarmColor.copy(alpha = 0.07f) else SurfaceContainer
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Animated pulsing dot
                    Box(
                        modifier = Modifier.size(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .scale(pulseScale)
                                .clip(CircleShape)
                                .background(BoilerActiveColor.copy(alpha = 0.2f))
                        )
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(BoilerActiveColor.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.LocalFireDepartment,
                                contentDescription = null,
                                tint = BoilerActiveColor,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Caldera",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            // Animated green dot
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .scale(pulseScale)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.tertiary)
                            )
                        }
                        Text(
                            text = "Modulación activa",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                // Speed/pressure indicator
                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.2f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.Speed,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "Activa",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = OutlineVariant.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(10.dp))

            // Mini stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BoilerStatCell(label = "Tª Impulsión", value = "--", modifier = Modifier.weight(1f))
                BoilerStatCell(label = "Bomba", value = "Activa", isPositive = true, modifier = Modifier.weight(1f))
                BoilerStatCell(label = "Estado", value = "OK", isPositive = true, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun BoilerStatCell(
    label: String,
    value: String,
    isPositive: Boolean = false,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = SurfaceContainerLow
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (isPositive) MaterialTheme.colorScheme.tertiary
                        else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

// ─── Boost Active Banner ──────────────────────────────────────────────────────

@Composable
private fun BoostActiveBanner(
    remainingMinutes: Int,
    onDeactivate: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                MaterialTheme.colorScheme.error.copy(alpha = 0.3f),
                RoundedCornerShape(12.dp)
            ),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    Icons.Default.Bolt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
                Column {
                    Text(
                        text = "TURBO ACTIVO: ${remainingMinutes}min",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Potencia suplementaria máxima",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Surface(
                onClick = onDeactivate,
                shape = RoundedCornerShape(100.dp),
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Text(
                    text = "Desactivar",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                )
            }
        }
    }
}

// ─── Room Card ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DarkRoomCard(
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

    val cardBorderColor = when {
        isSelected && room.heatingActive -> WarmColor.copy(alpha = 0.5f)
        isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        room.heatingActive -> WarmColor.copy(alpha = 0.25f)
        else -> OutlineVariant
    }
    val cardBgColor = when {
        isSelected && room.heatingActive -> WarmColor.copy(alpha = 0.08f)
        isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
        room.heatingActive -> WarmColor.copy(alpha = 0.05f)
        else -> SurfaceContainer
    }

    Surface(
        onClick = onSelect,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, cardBorderColor, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = cardBgColor
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Room icon + name + mode badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceContainerHigh),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.MeetingRoom,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = room.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (room.heatingActive) {
                                Icon(
                                    Icons.Default.Whatshot,
                                    contentDescription = "Calentando",
                                    tint = WarmColor,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        // Mode badge
                        Surface(
                            shape = RoundedCornerShape(100.dp),
                            color = modeBadgeColor(room.mode).copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = room.mode.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                color = modeBadgeColor(room.mode),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // Temperature display
                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = room.currentTemp?.let { "%.1f".format(it) } ?: "--",
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold,
                            color = temperatureColor(room.currentTemp),
                            lineHeight = 40.sp
                        )
                        Text(
                            text = "°",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = temperatureColor(room.currentTemp),
                            modifier = Modifier.padding(bottom = 3.dp)
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            Icons.Default.ThermostatAuto,
                            contentDescription = null,
                            modifier = Modifier.size(11.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = room.targetTemp?.let { "%.1f°".format(it) } ?: "--",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Expanded controls
            AnimatedVisibility(visible = isSelected) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    HorizontalDivider(color = OutlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        "Temperatura objetivo",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // +/- controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalIconButton(
                            onClick = { tempValue = (tempValue - 0.5).coerceAtLeast(7.0) },
                            modifier = Modifier.size(48.dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "-0.5C")
                        }
                        Text(
                            text = "%.1f°C".format(tempValue),
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                        FilledTonalIconButton(
                            onClick = { tempValue = (tempValue + 0.5).coerceAtMost(30.0) },
                            modifier = Modifier.size(48.dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                contentColor = MaterialTheme.colorScheme.primary
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

                    Spacer(modifier = Modifier.height(10.dp))
                    // Duration chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        durationOptions.forEach { (mins, label) ->
                            FilterChip(
                                selected = duration == mins,
                                onClick = { duration = mins },
                                label = { Text(label, fontSize = 11.sp) },
                                modifier = Modifier.weight(1f),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
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
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        if (isSetting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            val label = if (duration == 0)
                                "Aplicar %.1f°C sin limite".format(tempValue)
                            else
                                "Aplicar %.1f°C · ${durationOptions.first { it.first == duration }.second}".format(tempValue)
                            Text(label, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Modo operativo",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
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
                                label = { Text(mode.displayName, fontSize = 10.sp) },
                                modifier = Modifier.weight(1f),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                )
                            )
                        }
                    }
                }
            }

            // Unreachable indicator
            if (!room.reachable) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.SignalWifiOff,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
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

// ─── Module Card ──────────────────────────────────────────────────────────────

@Composable
fun DarkModuleCard(module: ModuleState) {
    val isPlug = module.type == "NAPlug"
    val batteryPct = module.batteryLevel?.let { raw ->
        if (raw <= 100) raw else ((raw - 3500).coerceIn(0, 2500) * 100 / 2500)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, OutlineVariant, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = SurfaceContainer
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (module.reachable) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
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
                    modifier = Modifier.size(22.dp),
                    tint = if (module.reachable) MaterialTheme.colorScheme.primary
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
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (module.reachable) "Conectado" else "Sin conexion",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (module.reachable) MaterialTheme.colorScheme.tertiary
                            else MaterialTheme.colorScheme.error
                )
            }
            if (isPlug) {
                Icon(
                    Icons.Default.ElectricalServices,
                    contentDescription = "Enchufado",
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
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
                                pct > 25 -> WarmColor
                                else -> MaterialTheme.colorScheme.error
                            }
                        )
                        Text(
                            "$pct%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// ─── Schedule Card ────────────────────────────────────────────────────────────

@Composable
fun DarkScheduleCard(
    schedules: List<ScheduleInfo>,
    onSwitchSchedule: (String) -> Unit,
    onEditSchedule: (String) -> Unit,
    onNewSchedule: () -> Unit
) {
    val activeSchedule = schedules.firstOrNull { it.isActive }
    val otherSchedules = schedules.filter { !it.isActive }
    var expanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, OutlineVariant, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = SurfaceContainer
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.RadioButtonChecked,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = activeSchedule?.name ?: "Sin programacion activa",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = "Activa",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                if (activeSchedule != null) {
                    IconButton(
                        onClick = { onEditSchedule(activeSchedule.id) },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Editar",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Contraer" else "Expandir",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    if (otherSchedules.isNotEmpty()) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = OutlineVariant.copy(alpha = 0.5f)
                        )
                        otherSchedules.forEach { schedule ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.RadioButtonUnchecked,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = schedule.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    onClick = { onSwitchSchedule(schedule.id) },
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Text("Activar", style = MaterialTheme.typography.labelMedium)
                                }
                                IconButton(
                                    onClick = { onEditSchedule(schedule.id) },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "Editar",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = OutlineVariant.copy(alpha = 0.5f)
                    )
                    TextButton(
                        onClick = onNewSchedule,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Nueva programacion")
                    }
                }
            }
        }
    }
}

// ─── Boost Dialog ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DarkBoostDialog(
    onDismiss: () -> Unit,
    onConfirm: (deltaTemp: Double, durationMinutes: Int) -> Unit
) {
    val deltaOptions = listOf(1.0 to "+1°C", 2.0 to "+2°C", 3.0 to "+3°C", 5.0 to "+5°C")
    val durationOptions = listOf(30 to "30 min", 60 to "60 min", 90 to "90 min", 120 to "120 min")
    var selectedDelta by remember { mutableStateOf(2.0) }
    var selectedDuration by remember { mutableStateOf(60) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceContainerHigh,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Whatshot,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Modo Turbo",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        text = {
            Column {
                Text(
                    "Aumento temporal de temperatura",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Incremento:",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    deltaOptions.forEach { (delta, label) ->
                        FilterChip(
                            selected = selectedDelta == delta,
                            onClick = { selectedDelta = delta },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.error,
                                selectedLabelColor = MaterialTheme.colorScheme.onError
                            )
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Duracion:",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    durationOptions.forEach { (minutes, label) ->
                        FilterChip(
                            selected = selectedDuration == minutes,
                            onClick = { selectedDuration = minutes },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.error,
                                selectedLabelColor = MaterialTheme.colorScheme.onError
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedDelta, selectedDuration) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Text("Activar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

private fun temperatureColor(temp: Double?): Color {
    if (temp == null) return Color(0xFF89929B)
    return when {
        temp < 16 -> FrostColor
        temp < 19 -> CoolColor
        temp < 22 -> ComfortColor
        temp < 25 -> WarmColor
        else -> Color(0xFFFF6B6B)
    }
}

@Composable
private fun modeBadgeColor(mode: ThermostatMode): Color = when (mode) {
    ThermostatMode.SCHEDULE -> MaterialTheme.colorScheme.primary
    ThermostatMode.MANUAL -> WarmColor
    ThermostatMode.AWAY -> AwayColor
    ThermostatMode.FROST_GUARD -> FrostColor
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
