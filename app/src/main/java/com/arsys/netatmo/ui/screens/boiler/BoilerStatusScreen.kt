package com.arsys.netatmo.ui.screens.boiler

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arsys.netatmo.domain.model.ModuleState
import com.arsys.netatmo.ui.theme.BoilerActiveColor
import com.arsys.netatmo.ui.theme.ComfortColor
import com.arsys.netatmo.ui.theme.OutlineVariant
import com.arsys.netatmo.ui.theme.SurfaceContainer
import com.arsys.netatmo.ui.theme.SurfaceContainerHigh
import com.arsys.netatmo.ui.theme.SurfaceContainerLowest
import com.arsys.netatmo.ui.theme.WarmColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoilerStatusScreen(
    onBack: () -> Unit,
    onDiagnostic: () -> Unit,
    viewModel: BoilerStatusViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isActive = state.boilerHealth.isModulating

    val infiniteTransition = rememberInfiniteTransition(label = "boilerGlow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.10f,
        targetValue = 0.30f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Estado Caldera",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Volver",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Actualizar",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SurfaceContainerLowest
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->

        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ── Error banner ───────────────────────────────────────────────────
            state.error?.let { err ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            err,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { viewModel.dismissError() },
                            modifier = Modifier.size(32.dp)
                        ) {
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

            // ── Central boiler state card ──────────────────────────────────────
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isActive)
                        WarmColor.copy(alpha = 0.08f)
                    else
                        SurfaceContainer
                ),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(
                    1.dp,
                    if (isActive) WarmColor.copy(alpha = 0.35f) else OutlineVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Flame icon with animated glow ring when active
                    Box(contentAlignment = Alignment.Center) {
                        if (isActive) {
                            Surface(
                                modifier = Modifier.size(110.dp),
                                shape = CircleShape,
                                color = WarmColor.copy(alpha = glowAlpha)
                            ) {}
                        }
                        Surface(
                            modifier = Modifier.size(80.dp),
                            shape = CircleShape,
                            color = if (isActive)
                                WarmColor.copy(alpha = 0.18f)
                            else
                                SurfaceContainerHigh,
                            border = BorderStroke(
                                2.dp,
                                if (isActive) WarmColor else OutlineVariant
                            )
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.LocalFireDepartment,
                                    contentDescription = null,
                                    tint = if (isActive) WarmColor
                                           else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(42.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = if (isActive) "Caldera activa" else "En reposo",
                        color = if (isActive) WarmColor
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )

                    // Health score pill
                    val health = state.boilerHealth
                    val pillColor = when {
                        health.healthScore >= 75 -> ComfortColor
                        health.healthScore >= 50 -> WarmColor
                        else -> MaterialTheme.colorScheme.error
                    }
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = pillColor.copy(alpha = 0.14f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(pillColor)
                            )
                            Text(
                                text = "${health.healthLabel} · ${health.healthScore}%",
                                color = pillColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // ── Metrics row ───────────────────────────────────────────────────
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard(
                    modifier = Modifier.weight(1f),
                    label = "Modulación",
                    value = state.boilerHealth.modulationPct?.let { "$it%" } ?: "--",
                    icon = Icons.Default.Tune,
                    tint = if (isActive) BoilerActiveColor else MaterialTheme.colorScheme.primary
                )
                MetricCard(
                    modifier = Modifier.weight(1f),
                    label = "Temp. impulsión",
                    value = state.boilerHealth.impulsionTempC
                        ?.let { "%.1f°C".format(it) }
                        ?: "--",
                    icon = Icons.Default.Thermostat,
                    tint = if (isActive) WarmColor else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ── Modulation progress bar ───────────────────────────────────────
            state.boilerHealth.modulationPct?.let { pct ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, OutlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Nivel de modulación",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp
                            )
                            Text(
                                "$pct%",
                                color = if (isActive) WarmColor
                                        else MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                        LinearProgressIndicator(
                            progress = { pct / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = if (isActive) WarmColor
                                    else MaterialTheme.colorScheme.primary,
                            trackColor = OutlineVariant
                        )
                    }
                }
            }

            // ── Module grid ───────────────────────────────────────────────────
            if (state.modules.isNotEmpty()) {
                Text(
                    "Módulos del sistema",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 4.dp)
                )
                state.modules.forEach { module ->
                    ModuleCard(module = module)
                }
            }

            // ── Diagnostic button ─────────────────────────────────────────────
            FilledTonalButton(
                onClick = {
                    viewModel.launchDiagnostic()
                    onDiagnostic()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = SurfaceContainer,
                    contentColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(
                    Icons.Default.Build,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Ejecutar Diagnóstico Hidráulico", fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

// ── Reusable composables ──────────────────────────────────────────────────────

@Composable
private fun MetricCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    icon: ImageVector,
    tint: Color
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, OutlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
            Text(
                value,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ModuleCard(module: ModuleState) {
    val typeName = when (module.type) {
        "NAPlug"   -> "Relay / Caldera"
        "NATherm1" -> "Termostato"
        "NRV"      -> "Válvula de zona"
        "NAMain"   -> "Estación interior"
        else       -> module.type
    }
    val reachableColor = if (module.reachable) ComfortColor
                         else MaterialTheme.colorScheme.error
    val reachableText = if (module.reachable) "Conectado" else "Sin señal"

    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, OutlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    typeName,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(reachableColor)
                    )
                    Text(reachableText, color = reachableColor, fontSize = 12.sp)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                module.batteryLevel?.let { bat ->
                    SignalBadge(
                        value = "$bat%",
                        label = "Bat",
                        color = when {
                            bat >= 70 -> ComfortColor
                            bat >= 30 -> WarmColor
                            else -> MaterialTheme.colorScheme.error
                        }
                    )
                }
                module.rfStrength?.let { rf ->
                    SignalBadge(
                        value = "${rf}dBm",
                        label = "RF",
                        color = when {
                            rf >= 60 -> ComfortColor
                            rf >= 30 -> WarmColor
                            else -> MaterialTheme.colorScheme.error
                        }
                    )
                }
                module.wifiStrength?.let { wifi ->
                    SignalBadge(
                        value = "${wifi}dBm",
                        label = "WiFi",
                        color = when {
                            wifi >= 60 -> ComfortColor
                            wifi >= 30 -> WarmColor
                            else -> MaterialTheme.colorScheme.error
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SignalBadge(value: String, label: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.12f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(value, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(label, color = color.copy(alpha = 0.7f), fontSize = 9.sp, fontWeight = FontWeight.Medium)
        }
    }
}
