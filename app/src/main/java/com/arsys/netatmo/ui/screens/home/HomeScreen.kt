package com.arsys.netatmo.ui.screens.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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

// ─── Color constants ──────────────────────────────────────────────────────────
private val ScreenBackground = Color(0xFF101419)
private val SecondaryContainerOrange = Color(0xFFF66018)
private val AtmosphericGlowColor = SecondaryContainerOrange.copy(alpha = 0.10f)

// ─── Screen ───────────────────────────────────────────────────────────────────

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
    var showBoostSheet by remember { mutableStateOf(false) }
    var showNotification by remember { mutableStateOf(true) }

    val avgTemp = uiState.thermostatState?.rooms
        ?.mapNotNull { it.currentTemp }
        ?.takeIf { it.isNotEmpty() }
        ?.average()

    val isBoilerOn = uiState.thermostatState?.let { state ->
        state.modules.any { it.boilerStatus == true } ||
        state.rooms.any { it.heatingActive }
    } ?: false

    val activeRoomCount = uiState.thermostatState?.rooms
        ?.count { it.heatingActive } ?: 0

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBackground)
    ) {
        // Atmospheric glow — always visible (secondary-container/10)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.radialGradient(
                        colors = listOf(AtmosphericGlowColor, Color.Transparent)
                    )
                )
        )

        Column(modifier = Modifier.fillMaxSize()) {
            StitchHeader(
                homeName = uiState.thermostatState?.homeName,
                onRefresh = { viewModel.loadThermostatData() }
            )

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
                            // ── Dashboard section ──────────────────────────
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

                                    // Notification card
                                    AnimatedVisibility(
                                        visible = showNotification,
                                        exit = fadeOut() + shrinkVertically()
                                    ) {
                                        NotificationCard(
                                            onDismiss = { showNotification = false }
                                        )
                                    }

                                    // Home title row
                                    HomeTitleRow(
                                        homeName = uiState.thermostatState?.homeName ?: "Mi Casa",
                                        activeZones = activeRoomCount,
                                        onRefresh = { viewModel.loadThermostatData() }
                                    )

                                    // Temp tiles 2-column row
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

                            // ── Rooms section ──────────────────────────────
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
                                                text = "Gestionar todas",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.clickable { }
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
                        }
                    }
                }

                // Turbo FAB — pill shape, orange, fire icon + label
                if (uiState.thermostatState != null && boostState == null) {
                    ExtendedFloatingActionButton(
                        onClick = { showBoostSheet = true },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 16.dp, bottom = 24.dp),
                        containerColor = SecondaryContainerOrange,
                        contentColor = Color.White,
                        shape = RoundedCornerShape(50),
                        icon = {
                            Icon(
                                Icons.Default.LocalFireDepartment,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        text = {
                            Text(
                                "Modo Turbo",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    )
                }

                // Success snackbar
                if (uiState.successMessage != null) {
                    Card(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 16.dp, vertical = 88.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceContainerHigh),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.tertiary)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                uiState.successMessage ?: "",
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

    // Boost bottom sheet
    if (showBoostSheet) {
        TurboBottomSheet(
            onDismiss = { showBoostSheet = false },
            onConfirm = { delta, duration ->
                viewModel.startBoost(delta, duration)
                showBoostSheet = false
            }
        )
    }
}

// ─── Header ───────────────────────────────────────────────────────────────────

@Composable
private fun StitchHeader(
    homeName: String?,
    onRefresh: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(ScreenBackground.copy(alpha = 0.85f))
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
                // Logo + labels
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    // Branded logo circle: "N" on primary-tinted circle
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "N",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column {
                        Text(
                            text = "NETATMO SMART",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.2.sp
                        )
                        Text(
                            text = homeName ?: "Inicio",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Actions: Tune + Refresh + Avatar
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Tune/filter icon
                    Surface(
                        onClick = { },
                        shape = CircleShape,
                        color = SurfaceContainer,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                Icons.Default.Tune,
                                contentDescription = "Filtros",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    // Refresh
                    Surface(
                        onClick = onRefresh,
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

            HorizontalDivider(color = OutlineVariant, thickness = 1.dp)
        }
    }
}

// ─── Home Title Row ───────────────────────────────────────────────────────────

@Composable
private fun HomeTitleRow(
    homeName: String,
    activeZones: Int,
    onRefresh: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = homeName,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                // Green dot
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiary)
                )
            }
            Text(
                text = "$activeZones zonas reguladas activas",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Surface(
            onClick = onRefresh,
            shape = CircleShape,
            color = SurfaceContainerHigh,
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "Actualizar",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// ─── Notification Card ────────────────────────────────────────────────────────

@Composable
private fun NotificationCard(onDismiss: () -> Unit) {
    val tertiaryContainer = MaterialTheme.colorScheme.tertiaryContainer
    val tertiary = MaterialTheme.colorScheme.tertiary

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, tertiary.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = tertiaryContainer.copy(alpha = 0.20f)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.NotificationImportant,
                    contentDescription = null,
                    tint = tertiary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Acuse de recibo completado",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
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
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Su revisión anual ha sido registrada y el certificado está disponible para descarga.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Ref: #ND-84920-OK",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.clickable { }
                ) {
                    Icon(
                        Icons.Default.PictureAsPdf,
                        contentDescription = null,
                        tint = tertiary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Ver PDF",
                        style = MaterialTheme.typography.labelSmall,
                        color = tertiary
                    )
                }
            }
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
        Spacer(Modifier.height(20.dp))
        Text(
            "Sin hogar configurado",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Ve a Ajustes para seleccionar tu hogar",
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
            Spacer(Modifier.width(8.dp))
            Text(
                message,
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
private fun InteriorTempTile(temp: Double, humidity: Int?, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.border(1.dp, OutlineVariant, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = SurfaceContainer
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Home, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                ) {
                    Text(
                        "INTERIOR",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "%.1f".format(temp),
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "°C",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Default.WaterDrop, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(13.dp))
                Text(
                    if (humidity != null) "$humidity% Humedad" else "-- Humedad",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ExteriorTempTile(temp: Double, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.border(1.dp, OutlineVariant, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = SurfaceContainer
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.WbSunny, null, tint = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(20.dp))
                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.15f)
                ) {
                    Text(
                        "EXTERIOR",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "%.1f".format(temp),
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "°C",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Default.Air, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(13.dp))
                Text("Exterior", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ─── Boiler Status Card ───────────────────────────────────────────────────────

@Composable
private fun BoilerStatusCard(isActive: Boolean) {
    val pulseAnim = rememberInfiniteTransition(label = "boiler_pulse")
    val pulseScale by pulseAnim.animateFloat(
        initialValue = 0.85f, targetValue = 1.15f,
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
            // Header row: pulsing icon + title/subtitle + pressure chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .scale(pulseScale)
                                .clip(CircleShape)
                                .background(BoilerActiveColor.copy(alpha = 0.2f))
                        )
                        Box(
                            modifier = Modifier
                                .size(26.dp)
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
                                "Caldera Modulante",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .scale(pulseScale)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.tertiary)
                            )
                        }
                        Text(
                            "Modulación activa",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Speed, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(13.dp))
                        Text(
                            "1.35 bar · Óptima",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = OutlineVariant.copy(alpha = 0.3f))
            Spacer(Modifier.height(10.dp))

            // 3-column stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BoilerStatCell(
                    label = "Tª Impulsión",
                    value = "48.5°C",
                    icon = Icons.Default.DeviceThermostat,
                    modifier = Modifier.weight(1f)
                )
                BoilerStatCell(
                    label = "Bomba",
                    value = "Activa",
                    icon = Icons.Default.Sync,
                    isPositive = true,
                    modifier = Modifier.weight(1f)
                )
                BoilerStatCell(
                    label = "Auditoría",
                    value = "A+ 100%",
                    icon = Icons.Default.Stars,
                    isPositive = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = OutlineVariant.copy(alpha = 0.3f))
            Spacer(Modifier.height(10.dp))

            // Certificate row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Default.WorkspacePremium,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(16.dp)
                    )
                    Column {
                        Text(
                            "Certificado #ND-84920-OK registrado",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "Garantía extendida hasta 2029",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Surface(
                    onClick = { },
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f)
                ) {
                    Text(
                        "Expediente SAT OK",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun BoilerStatCell(
    label: String,
    value: String,
    icon: ImageVector? = null,
    isPositive: Boolean = false,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier, shape = RoundedCornerShape(10.dp), color = SurfaceContainerLow) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                icon?.let {
                    Icon(
                        it, null,
                        tint = if (isPositive) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(13.dp)
                    )
                }
                Text(
                    value,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isPositive) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

// ─── Boost Active Banner ──────────────────────────────────────────────────────

@Composable
private fun BoostActiveBanner(remainingMinutes: Int, onDeactivate: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
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
                Icon(Icons.Default.Bolt, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                Column {
                    Text(
                        "TURBO ACTIVO: ${remainingMinutes}min",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Potencia suplementaria máxima",
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
                    "Desactivar",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                )
            }
        }
    }
}

// ─── Room Card ────────────────────────────────────────────────────────────────

private fun roomIcon(roomName: String): ImageVector {
    val lower = roomName.lowercase()
    return when {
        lower.contains("sal") || lower.contains("estar") || lower.contains("living") -> Icons.Default.Weekend
        lower.contains("dorm") || lower.contains("habit") || lower.contains("cuarto") -> Icons.Default.Bed
        lower.contains("despa") || lower.contains("ofici") || lower.contains("estudi") -> Icons.Default.Computer
        lower.contains("cocin") -> Icons.Default.Kitchen
        lower.contains("bano") || lower.contains("baño") -> Icons.Default.Bathtub
        else -> Icons.Default.MeetingRoom
    }
}

private fun roomModeIcon(mode: ThermostatMode): ImageVector = when (mode) {
    ThermostatMode.SCHEDULE -> Icons.Default.Schedule
    ThermostatMode.MANUAL -> Icons.Default.TouchApp
    ThermostatMode.AWAY -> Icons.Default.DirectionsWalk
    ThermostatMode.FROST_GUARD -> Icons.Default.AcUnit
    ThermostatMode.OFF -> Icons.Default.PowerSettingsNew
}

private fun roomModeShortLabel(mode: ThermostatMode): String = when (mode) {
    ThermostatMode.SCHEDULE -> "Auto"
    ThermostatMode.MANUAL -> "Manual"
    ThermostatMode.AWAY -> "Ausente"
    ThermostatMode.FROST_GUARD -> "Antihielo"
    ThermostatMode.OFF -> "Apagar"
}

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
    val durationOptions = listOf(60 to "1h", 120 to "2h", 180 to "3h", 0 to "Sin límite")
    var duration by remember { mutableStateOf(60) }

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
    val currentTempColor = if (isSelected)
        MaterialTheme.colorScheme.secondary
    else if (room.heatingActive)
        MaterialTheme.colorScheme.tertiary
    else
        MaterialTheme.colorScheme.primary

    Surface(
        onClick = onSelect,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, cardBorderColor, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = cardBgColor
    ) {
        Box {
            // Ambient glow overlay for expanded hero card
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.10f),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }

            Column(modifier = Modifier.padding(16.dp)) {
                // Header row: icon + name/badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
                                roomIcon(room.name),
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
                                    room.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (room.heatingActive) {
                                    Icon(Icons.Default.Whatshot, "Calentando", tint = WarmColor, modifier = Modifier.size(16.dp))
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(100.dp),
                                color = modeBadgeColor(room.mode).copy(alpha = 0.15f)
                            ) {
                                Text(
                                    room.mode.displayName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = modeBadgeColor(room.mode),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    // Collapsed: large temp on right; expanded: nothing (temp shown in body)
                    if (!isSelected) {
                        Column(horizontalAlignment = Alignment.End) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    room.currentTemp?.let { "%.1f".format(it) } ?: "--",
                                    style = MaterialTheme.typography.headlineLarge,
                                    color = currentTempColor
                                )
                                Text(
                                    "°",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = currentTempColor,
                                    modifier = Modifier.padding(bottom = 3.dp)
                                )
                            }
                        }
                    }
                }

                // Collapsed inline stepper (target temp +/-)
                if (!isSelected) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                Icons.Default.ThermostatAuto, null,
                                modifier = Modifier.size(13.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "Consigna:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Surface(
                                onClick = {
                                    tempValue = (tempValue - 0.5).coerceAtLeast(7.0)
                                    onTemperatureChange(tempValue, duration)
                                },
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                modifier = Modifier.size(28.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    Icon(Icons.Default.Remove, "-", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                }
                            }
                            Text(
                                "%.1f°C".format(tempValue),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Surface(
                                onClick = {
                                    tempValue = (tempValue + 0.5).coerceAtMost(30.0)
                                    onTemperatureChange(tempValue, duration)
                                },
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                modifier = Modifier.size(28.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    Icon(Icons.Default.Add, "+", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }

                    // Unreachable indicator for collapsed
                    if (!room.reachable) {
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.SignalWifiOff, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(4.dp))
                            Text("No accesible", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                // Expanded controls
                AnimatedVisibility(visible = isSelected) {
                    Column(modifier = Modifier.padding(top = 16.dp)) {
                        HorizontalDivider(color = OutlineVariant.copy(alpha = 0.5f))
                        Spacer(Modifier.height(16.dp))

                        // Big current temp display in card body
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    room.currentTemp?.let { "%.1f".format(it) } ?: "--",
                                    style = MaterialTheme.typography.displayLarge,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Text(
                                    "°C",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    "CONSIGNA DESEADA",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Default.ArrowUpward, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                    Text(
                                        "%.1f°C".format(tempValue),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        // Gradient temperature slider (14°–26°)
                        GradientTemperatureSlider(
                            value = tempValue,
                            onValueChange = { tempValue = it },
                            minTemp = 14f,
                            maxTemp = 26f
                        )

                        Spacer(Modifier.height(4.dp))
                        // Return time estimate
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Icon(Icons.Default.AccessTime, null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "Estimado 20 min",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(Modifier.height(12.dp))

                        // Apply button
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
                                Text(
                                    "Aplicar %.1f°C".format(tempValue),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }

                        Spacer(Modifier.height(14.dp))
                        Text(
                            "Modo operativo",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(8.dp))

                        // 5-button mode grid
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ThermostatMode.entries.forEach { mode ->
                                val isSelected2 = room.mode == mode
                                Surface(
                                    onClick = { onModeChange(mode) },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected2) MaterialTheme.colorScheme.primary
                                            else SurfaceContainerHigh,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            roomModeIcon(mode),
                                            contentDescription = null,
                                            tint = if (isSelected2) MaterialTheme.colorScheme.onPrimary
                                                   else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            roomModeShortLabel(mode),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isSelected2) MaterialTheme.colorScheme.onPrimary
                                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }

                        // Unreachable indicator for expanded
                        if (!room.reachable) {
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.SignalWifiOff, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                                Spacer(Modifier.width(4.dp))
                                Text("No accesible", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Gradient Temperature Slider ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GradientTemperatureSlider(
    value: Double,
    onValueChange: (Double) -> Unit,
    minTemp: Float = 14f,
    maxTemp: Float = 26f
) {
    val primary = MaterialTheme.colorScheme.primary
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val secondaryContainer = MaterialTheme.colorScheme.secondaryContainer
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val thumbColor = Color(0xFFF1F5F9)

    Column {
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(Math.round(it * 2).toDouble() / 2) },
            valueRange = minTemp..maxTemp,
            steps = ((maxTemp - minTemp) * 2).toInt() - 1,
            modifier = Modifier.fillMaxWidth(),
            thumb = { _ ->
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(thumbColor)
                        .border(2.dp, primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(secondaryContainer)
                    )
                }
            },
            track = { sliderState ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(primary, primaryContainer, secondaryContainer)
                            )
                        )
                )
            }
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("${minTemp.toInt()}°", style = MaterialTheme.typography.bodySmall, color = onSurfaceVariant)
            Text("${maxTemp.toInt()}°", style = MaterialTheme.typography.bodySmall, color = onSurfaceVariant)
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
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
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
                    tint = if (module.reachable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    when (module.type) {
                        "NATherm1", "NTH01" -> "Termostato"
                        "NRV" -> "Válvula de radiador"
                        "OTM" -> "Módulo OpenTherm"
                        "NAPlug" -> "Relé / Enchufe"
                        "NAMain" -> "Estación principal"
                        else -> module.type
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    if (module.reachable) "Conectado" else "Sin conexión",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (module.reachable) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
                )
            }
            if (isPlug) {
                Icon(Icons.Default.ElectricalServices, "Enchufado", tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
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
                        Text("$pct%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.RadioButtonChecked, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    activeSchedule?.name ?: "Sin programación activa",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)) {
                    Text("Activa", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                }
                if (activeSchedule != null) {
                    IconButton(onClick = { onEditSchedule(activeSchedule.id) }, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.Edit, "Editar", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(34.dp)) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        if (expanded) "Contraer" else "Expandir",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    if (otherSchedules.isNotEmpty()) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = OutlineVariant.copy(alpha = 0.5f))
                        otherSchedules.forEach { schedule ->
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.RadioButtonUnchecked, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(schedule.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                                TextButton(onClick = { onSwitchSchedule(schedule.id) }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)) {
                                    Text("Activar", style = MaterialTheme.typography.labelMedium)
                                }
                                IconButton(onClick = { onEditSchedule(schedule.id) }, modifier = Modifier.size(34.dp)) {
                                    Icon(Icons.Default.Edit, "Editar", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = OutlineVariant.copy(alpha = 0.5f))
                    TextButton(
                        onClick = onNewSchedule,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Nueva programación")
                    }
                }
            }
        }
    }
}

// ─── Turbo Bottom Sheet ───────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TurboBottomSheet(
    onDismiss: () -> Unit,
    onConfirm: (deltaTemp: Double, durationMinutes: Int) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedDuration by remember { mutableStateOf(30) }
    val durationOptions = listOf(15 to "15 min", 30 to "30 min", 45 to "45 min")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceContainerHigh,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Handle
            Box(modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(OutlineVariant)
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Bolt, null, tint = SecondaryContainerOrange, modifier = Modifier.size(24.dp))
                Text(
                    "Modo Turbo",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                "Activa potencia máxima durante un tiempo determinado",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                "Duración",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            // 3-column duration grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                durationOptions.forEach { (mins, label) ->
                    val isSelected = selectedDuration == mins
                    Surface(
                        onClick = { selectedDuration = mins },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) SecondaryContainerOrange else SurfaceContainer,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) SecondaryContainerOrange else OutlineVariant
                        ),
                        modifier = Modifier.weight(1f).height(52.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Button(
                onClick = { onConfirm(2.0, selectedDuration) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SecondaryContainerOrange,
                    contentColor = Color.White
                )
            ) {
                Icon(Icons.Default.LocalFireDepartment, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Activar Turbo General",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
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
